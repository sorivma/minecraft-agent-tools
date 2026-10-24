package dev.minecraftagenttools.forge.betterquesting;

import betterquesting.api.events.DatabaseEvent;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import dev.minecraftagenttools.betterquesting.contract.BqTools;
import dev.minecraftagenttools.gamebridge.GameBridge;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;

@Mod(
        modid = "minecraftagenttoolsbetterquesting",
        name = "Minecraft Agent Tools BetterQuesting",
        version = "0.1.0-SNAPSHOT",
        acceptableRemoteVersions = "*",
        dependencies = "required-after:betterquesting@[3.8.89,)")
public final class BetterQuestingMod {
    private int port;
    private String token;
    private BqReader reader;
    private GameBridge bridge;
    private final AtomicInteger revision = new AtomicInteger();

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        if (!event.getSide().isClient())
            throw new IllegalStateException(
                    "Install the BetterQuesting adapter on the client only");
        Configuration config = new Configuration(event.getSuggestedConfigurationFile());
        config.load();
        port =
                config.getInt(
                        "port",
                        "bridge",
                        25580,
                        1024,
                        65535,
                        "Loopback-only BetterQuesting bridge");
        token =
                config.getString(
                        "token",
                        "bridge",
                        UUID.randomUUID().toString() + UUID.randomUUID().toString(),
                        "Private credential; copy to MCP MAT_BETTERQUESTING_TOKEN and set MAT_BETTERQUESTING_PORT");
        config.save();
        if (token.length() < 32)
            throw new IllegalArgumentException(
                    "BetterQuesting bridge token must have at least 32 characters");
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) throws java.io.IOException {
        reader = new BqReader(revision::get);
        bridge = new GameBridge(port, token, BqTools::validate, reader::call);
        FMLCommonHandler.instance().bus().register(this);
        MinecraftForge.EVENT_BUS.register(this);
        Runtime.getRuntime().addShutdownHook(new Thread(bridge::close, "mat-bq-shutdown"));
    }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END && bridge != null) {
            reader.tick();
            bridge.tick();
        }
    }

    @SubscribeEvent
    public void changed(DatabaseEvent event) {
        // Events can originate on BQ's IO/server threads. Never inspect game objects here.
        if (event instanceof DatabaseEvent.Update || event instanceof DatabaseEvent.Load)
            revision.incrementAndGet();
    }
}
