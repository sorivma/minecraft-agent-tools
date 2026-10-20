package dev.minecraftagenttools.forge.nei;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import java.util.UUID;
import net.minecraftforge.common.config.Configuration;

/** Install on the Minecraft client alongside NEI, independently of the server observation mod. */
@Mod(
        modid = "minecraftagenttoolsnei",
        name = "Minecraft Agent Tools NEI",
        version = "0.1.0-SNAPSHOT",
        acceptableRemoteVersions = "*",
        dependencies = "required-after:NotEnoughItems@[2.6.44,)")
public final class NeiClientMod {
    private int port;
    private String token;
    private NeiBridge bridge;
    private NeiReader reader;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        if (!event.getSide().isClient())
            throw new IllegalStateException("Install the NEI adapter on the Minecraft client only");
        Configuration config = new Configuration(event.getSuggestedConfigurationFile());
        config.load();
        port =
                config.getInt(
                        "port", "bridge", 25577, 1024, 65535, "Loopback-only client NEI bridge");
        token =
                config.getString(
                        "token",
                        "bridge",
                        UUID.randomUUID().toString() + UUID.randomUUID().toString(),
                        "Private credential; copy to MCP MAT_NEI_TOKEN and set MAT_NEI_PORT");
        config.save();
        if (token.length() < 32)
            throw new IllegalArgumentException("NEI bridge token must have at least 32 characters");
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) throws java.io.IOException {
        reader = new NeiReader();
        bridge = new NeiBridge(port, token, reader::call);
        FMLCommonHandler.instance().bus().register(this);
        Runtime.getRuntime().addShutdownHook(new Thread(bridge::close, "mat-nei-shutdown"));
    }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END && bridge != null) {
            reader.tick();
            bridge.tick();
        }
    }
}
