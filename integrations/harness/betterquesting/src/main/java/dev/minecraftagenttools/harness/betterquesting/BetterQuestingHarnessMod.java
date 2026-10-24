package dev.minecraftagenttools.harness.betterquesting;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import dev.minecraftharness.forge.HarnessWidgets;
import net.minecraftforge.common.MinecraftForge;

@Mod(
        modid = "minecraftagenttoolsbetterquestingharness",
        name = "Minecraft Agent Tools Quest Widgets",
        version = "0.1.0-SNAPSHOT",
        acceptableRemoteVersions = "*",
        dependencies =
                "required-after:minecraftharness;required-after:minecraftagenttoolsbetterquesting")
public final class BetterQuestingHarnessMod {
    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        if (event.getSide().isClient()) {
            HarnessWidgets.register(
                    "minecraftagenttools:quest-v1", QuestWidget.instructions(), QuestWidget::parse);
            MinecraftForge.EVENT_BUS.register(new QuestTooltip());
        }
    }
}
