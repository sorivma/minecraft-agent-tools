package dev.minecraftagenttools.harness.schematica;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import dev.minecraftharness.forge.HarnessWidgets;
import net.minecraftforge.common.MinecraftForge;

@Mod(
        modid = "minecraftagenttoolsschematicaharness",
        name = "Minecraft Agent Tools Schematica Widgets",
        version = dev.minecraftagenttools.protocol.BuildVersion.VERSION,
        acceptableRemoteVersions = "*",
        dependencies =
                "required-after:minecraftharness;required-after:minecraftagenttoolsschematica")
public final class SchematicaHarnessMod {
    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        if (event.getSide().isClient()) {
            MinecraftForge.EVENT_BUS.register(new MaterialTooltip());
            HarnessWidgets.register(
                    "minecraftagenttools:schematic-v1",
                    SchematicWidget.instructions(),
                    SchematicWidget::parse);
            HarnessWidgets.register(
                    "minecraftagenttools:schematics-v1",
                    "Legacy saved-plan recovery only. Emit one minecraftagenttools:schematic-v1 card per plan instead.",
                    SchematicWidget::parseLegacy);
        }
    }
}
