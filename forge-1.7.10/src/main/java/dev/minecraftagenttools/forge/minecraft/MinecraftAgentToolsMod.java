package dev.minecraftagenttools.forge.minecraft;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.*;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import java.util.UUID;
import net.minecraftforge.common.config.Configuration;

@Mod(
        modid = "minecraftagenttools",
        name = "Minecraft Agent Tools",
        version = "0.1.0-SNAPSHOT",
        acceptableRemoteVersions = "*")
public final class MinecraftAgentToolsMod {
    private int port;
    private String token;
    private LocalBridge bridge;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        Configuration config = new Configuration(event.getSuggestedConfigurationFile());
        config.load();
        port =
                config.getInt(
                        "port", "bridge", 25576, 1024, 65535, "Loopback-only observation bridge");
        token =
                config.getString(
                        "token",
                        "bridge",
                        UUID.randomUUID().toString() + UUID.randomUUID().toString(),
                        "Private administrator credential; copy to MCP MAT_TOKEN");
        config.save();
        if (token.length() < 32)
            throw new IllegalArgumentException("Bridge token must have at least 32 characters");
        FMLCommonHandler.instance().bus().register(this);
    }

    @Mod.EventHandler
    public void start(FMLServerStartedEvent event) throws java.io.IOException {
        bridge = new LocalBridge(port, token, new Observations());
    }

    @Mod.EventHandler
    public void stop(FMLServerStoppingEvent event) {
        if (bridge != null) {
            bridge.close();
            bridge = null;
        }
    }

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && bridge != null) bridge.tick();
    }
}
