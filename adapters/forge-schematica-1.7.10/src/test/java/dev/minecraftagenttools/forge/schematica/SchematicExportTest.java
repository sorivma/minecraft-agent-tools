package dev.minecraftagenttools.forge.schematica;

import static org.junit.Assert.*;

import org.junit.Test;

public class SchematicExportTest {
    @Test
    public void alphaExportNeverSilentlyTruncatesFrame300() {
        assertEquals(15, SchematicExport.legacyMetadata(15));
        try {
            SchematicExport.legacyMetadata(300);
            fail("Alpha export must not turn frame metadata 300 into 44 or 12");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("schematic_get"));
        }
    }
}
