package dev.minecraftagenttools.harness.schematica;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import dev.minecraftharness.forge.HarnessWidgets;

@Mod(
        modid = "minecraftagenttoolsschematicaharness",
        name = "Minecraft Agent Tools Schematica Widgets",
        version = "0.1.0-SNAPSHOT",
        acceptableRemoteVersions = "*",
        dependencies =
                "required-after:minecraftharness;required-after:minecraftagenttoolsschematica")
public final class SchematicaHarnessMod {
    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        if (event.getSide().isClient())
            HarnessWidgets.register(
                    "minecraftagenttools:schematics-v1",
                    SchematicsWidget.instructions(),
                    SchematicsWidget::parse);
    }
}
