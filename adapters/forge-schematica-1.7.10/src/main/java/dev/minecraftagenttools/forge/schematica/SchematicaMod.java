package dev.minecraftagenttools.forge.schematica;

import cpw.mods.fml.common.*;
import cpw.mods.fml.common.event.*;
import cpw.mods.fml.common.eventhandler.*;
import cpw.mods.fml.common.gameevent.TickEvent;
import dev.minecraftagenttools.gamebridge.GameBridge;
import dev.minecraftagenttools.schematica.contract.SchematicaTools;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;

@Mod(
        modid = "minecraftagenttoolsschematica",
        name = "Minecraft Agent Tools Schematica",
        version = "0.1.0-SNAPSHOT",
        acceptableRemoteVersions = "*",
        dependencies = "required-after:Schematica")
public final class SchematicaMod {
    private int port;
    private String token;
    private GameBridge bridge;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        if (!event.getSide().isClient())
            throw new IllegalStateException("Install the Schematica adapter on the client only");
        Configuration config = new Configuration(event.getSuggestedConfigurationFile());
        config.load();
        port =
                config.getInt(
                        "port",
                        "bridge",
                        25579,
                        1024,
                        65535,
                        "Loopback-only Schematica authoring bridge");
        token =
                config.getString(
                        "token",
                        "bridge",
                        UUID.randomUUID().toString() + UUID.randomUUID().toString(),
                        "Private credential; set MAT_SCHEMATICA_TOKEN and MAT_SCHEMATICA_PORT in MCP");
        config.save();
        if (token.length() < 32)
            throw new IllegalArgumentException("Schematica token must have at least 32 characters");
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) throws java.io.IOException {
        SchematicaApi api = null;
        String error = null;
        try {
            api = new SchematicaApi();
        } catch (ReflectiveOperationException | LinkageError e) {
            error = e.toString();
        }
        SchematicClient.active =
                new SchematicClient(
                        () -> {
                            Minecraft mc = Minecraft.getMinecraft();
                            return new SchematicClient.State(
                                    mc.thePlayer == null ? null : mc.theWorld,
                                    mc.theWorld == null ? 0 : mc.theWorld.provider.dimensionId);
                        },
                        api == null ? null : api::build,
                        error);
        bridge = new GameBridge(port, token, SchematicaTools::validate, SchematicaMod::call);
        FMLCommonHandler.instance().bus().register(this);
        MinecraftForge.EVENT_BUS.register(this);
        Runtime.getRuntime().addShutdownHook(new Thread(bridge::close, "mat-schematica-shutdown"));
    }

    private static java.util.Map<String, Object> call(
            String tool, java.util.Map<String, Object> args) {
        try {
            return SchematicClient.active.call(tool, args);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException(e.toString(), e);
        }
    }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END && bridge != null) {
            SchematicClient.active.tick();
            bridge.tick();
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void render(RenderWorldLastEvent event) {
        if (SchematicClient.active != null && Minecraft.getMinecraft().thePlayer != null)
            SchematicClient.active.render();
    }
}
