package dev.minecraftagenttools.forge.schematica;

import static org.junit.Assert.*;

import cpw.mods.fml.common.Mod;
import java.util.Arrays;
import org.junit.Test;

public class ModDependencyTest {
    @Test
    public void loaderDependencyUsesTheExactUpstreamModId() {
        // Schematica 1.12.6-GTNH declares @Mod(modid = "Schematica"). Forge's lookup
        // is case-sensitive: a lowercase dependency produces the missing-mod startup screen.
        String dependencies = SchematicaMod.class.getAnnotation(Mod.class).dependencies();
        assertTrue(Arrays.asList(dependencies.split(";")).contains("required-after:Schematica"));
    }
}
