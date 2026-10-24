package dev.minecraftagenttools.forge.schematica;

import static org.junit.Assert.*;

import org.junit.Test;

public class SchematicMetadataTest {
    public interface Storage {
        boolean setBlock(int x, int y, int z, Object block, int meta);

        boolean setBlock(int x, int y, int z, Object block);

        boolean setBlockMetadata(int x, int y, int z, int meta);

        int getBlockMetadata(int x, int y, int z);
    }

    public static class NibbleStorage implements Storage {
        final int[] cells = new int[4];

        public boolean setBlock(int x, int y, int z, Object block, int meta) {
            return setBlockMetadata(x, y, z, meta);
        }

        public boolean setBlock(int x, int y, int z, Object block) {
            return setBlock(x, y, z, block, 0);
        }

        public boolean setBlockMetadata(int x, int y, int z, int meta) {
            if (x < 0 || x >= 2 || y != 0 || z < 0 || z >= 2) return false;
            cells[x + z * 2] = meta & 15;
            return true;
        }

        public int getBlockMetadata(int x, int y, int z) {
            if (x < 0 || x >= 2 || y != 0 || z < 0 || z >= 2) return 0;
            return cells[x + z * 2];
        }
    }

    @Test
    public void frame300SurvivesSchematicNibbleStorageAndMetadataUpdates() {
        NibbleStorage delegate = new NibbleStorage();
        Storage world = (Storage) SchematicMetadata.wrap(Storage.class, delegate, 2, 1, 2);
        assertTrue(world.setBlock(1, 0, 1, "frame", 300));
        assertEquals(12, delegate.getBlockMetadata(1, 0, 1));
        assertEquals(300, world.getBlockMetadata(1, 0, 1));
        assertEquals(0, world.getBlockMetadata(0, 0, 0));
        assertTrue(world.setBlockMetadata(1, 0, 1, 65535));
        assertEquals(65535, world.getBlockMetadata(1, 0, 1));
        assertTrue(world.setBlock(1, 0, 1, "air"));
        assertEquals(0, world.getBlockMetadata(1, 0, 1));
        assertFalse(world.setBlock(2, 0, 0, "frame", 300));
        assertEquals(0, world.getBlockMetadata(2, 0, 0));
    }
}
