package dev.minecraftagenttools.forge.schematica;

import static org.junit.Assert.*;

import java.lang.reflect.Field;
import org.junit.Test;

public class ChunkRenderPassTest {
    public static class Hooks {
        static int worldRenderPass = -1, renderPass = -1;
    }

    public static class Chunk {
        int worldPass, tilePass;
        boolean fail;

        public Chunk(Object world, int x, int y, int z) {}

        public void renderBlocks(int pass, int x, int y, int z, int xx, int yy, int zz) {
            worldPass = Hooks.worldRenderPass;
            if (fail) throw new IllegalStateException("renderer failed");
        }

        public void renderTileEntities(int pass) {
            tilePass = Hooks.renderPass;
        }
    }

    @Test
    public void modBlocksAndTileRenderersReceiveTheSchematicPassAndRestoreForgeState()
            throws Exception {
        Field field = Hooks.class.getDeclaredField("worldRenderPass");
        field.setAccessible(true);
        int original = field.getInt(null);
        int tileOriginal = Hooks.renderPass;
        try {
            field.setInt(null, -1);
            Chunk chunk =
                    (Chunk)
                            ChunkRenderPass.constructor(Chunk.class, Object.class, Hooks.class)
                                    .newInstance(new Object(), 0, 0, 0);
            chunk.renderBlocks(0, 0, 0, 0, 1, 1, 1);
            assertEquals("GT casing textures reject a world pass of -1", 0, chunk.worldPass);
            chunk.renderBlocks(1, 0, 0, 0, 1, 1, 1);
            assertEquals(1, chunk.worldPass);
            assertEquals(-1, Hooks.worldRenderPass);
            chunk.renderTileEntities(1);
            assertEquals(1, chunk.tilePass);
            assertEquals(tileOriginal, Hooks.renderPass);
            chunk.fail = true;
            try {
                chunk.renderBlocks(0, 0, 0, 0, 1, 1, 1);
                fail();
            } catch (IllegalStateException expected) {
                assertEquals(-1, Hooks.worldRenderPass);
            }
        } finally {
            field.setInt(null, original);
        }
    }
}
