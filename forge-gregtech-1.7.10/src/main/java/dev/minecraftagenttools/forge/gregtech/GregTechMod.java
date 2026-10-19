package dev.minecraftagenttools.forge.gregtech;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.*;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import dev.minecraftagenttools.gamebridge.GameBridge;
import dev.minecraftagenttools.gregtech.contract.GtTools;
import java.util.UUID;
import net.minecraftforge.common.config.Configuration;

/** Server-side adapter; works on a dedicated server or the integrated single-player server. */
@Mod(
        modid = "minecraftagenttoolsgregtech",
        name = "Minecraft Agent Tools GregTech",
        version = "0.1.0-SNAPSHOT",
        acceptableRemoteVersions = "*",
        dependencies = "required-after:gregtech")
public final class GregTechMod {
    private int port;
    private String token;
    private GameBridge bridge;
    private GtReader reader;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        Configuration config = new Configuration(event.getSuggestedConfigurationFile());
        config.load();
        port =
                config.getInt(
                        "port", "bridge", 25578, 1024, 65535, "Loopback-only GregTech read bridge");
        token =
                config.getString(
                        "token",
                        "bridge",
                        UUID.randomUUID().toString() + UUID.randomUUID().toString(),
                        "Private credential; copy to MAT_GREGTECH_TOKEN and set MAT_GREGTECH_PORT");
        config.save();
        if (token.length() < 32)
            throw new IllegalArgumentException("GregTech token must have at least 32 characters");
        FMLCommonHandler.instance().bus().register(this);
    }

    @Mod.EventHandler
    public void start(FMLServerStartedEvent event) throws java.io.IOException {
        reader = new GtReader();
        bridge = new GameBridge(port, token, GtTools::validate, reader::call);
    }

    @Mod.EventHandler
    public void stop(FMLServerStoppingEvent event) {
        if (bridge != null) {
            bridge.close();
            bridge = null;
            reader = null;
        }
    }

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && bridge != null) {
            reader.tick();
            bridge.tick();
        }
    }
}
