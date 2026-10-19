package dev.minecraftagenttools.protocol.world;

import static dev.minecraftagenttools.protocol.data.Data.map;
import static org.junit.Assert.*;

import dev.minecraftagenttools.minecraft.contract.Tools;
import java.util.*;
import org.junit.Test;

public class TerrainPageTest {
    @Test
    public void baseNearNegativeChunkCornerFitsInOneDefaultPage() {
        // Player at (-1,64,-1): a 10x10 base centered here crosses four chunks.
        TerrainPage page = new TerrainPage(-1, -1, 1, 56, 72, 0);
        assertEquals(-32, page.minX);
        assertEquals(15, page.maxX);
        assertEquals(-32, page.minZ);
        assertEquals(15, page.maxZ);
        assertEquals(56, page.startY);
        assertEquals(72, page.endY);
        assertEquals(-1, page.nextOffset);
        for (int x = -6; x <= 3; x++)
            for (int z = -6; z <= 3; z++)
                assertTrue(x >= page.minX && x <= page.maxX && z >= page.minZ && z <= page.maxZ);
    }

    @Test
    public void fullHeightPagesHaveNoGapsAndBoundGameThreadWork() {
        for (int radius = 0; radius <= 2; radius++) {
            int offset = 0, expectedY = 0, cells = 0;
            do {
                TerrainPage page = new TerrainPage(-1, 2, radius, 0, 255, offset);
                assertEquals(expectedY, page.startY);
                int count = (page.endY - page.startY + 1) * page.width * page.depth;
                assertTrue(count > 0 && count <= TerrainPage.MAX_CELLS);
                cells += count;
                expectedY = page.endY + 1;
                offset = page.nextOffset;
            } while (offset >= 0);
            assertEquals(256, expectedY);
            assertEquals(256 * 16 * 16 * (2 * radius + 1) * (2 * radius + 1), cells);
        }
    }

    @Test
    public void runsRoundTripFloorWallsRoofAndMetadataWithUnknownGaps() {
        List<Object> runs = new ArrayList<Object>();
        // Different palette IDs can represent the same block with different metadata.
        int[][] rows = {{0, 0, 0, 0, 0, 0}, {1, -1, -1, 2, -1, 1}, {3, 3, 3, 3, 3, 3}};
        for (int y = 0; y < rows.length; y++) TerrainPage.appendRow(runs, -3, y + 63, -1, rows[y]);
        int[][] decoded = new int[3][6];
        for (int[] row : decoded) Arrays.fill(row, -1);
        for (Object value : runs) {
            List<?> run = (List<?>) value;
            assertEquals(-1, run.get(2));
            int x = (Integer) run.get(0),
                    y = (Integer) run.get(1),
                    length = (Integer) run.get(3),
                    id = (Integer) run.get(4);
            for (int i = 0; i < length; i++) decoded[y - 63][x + 3 + i] = id;
        }
        assertEquals(5, runs.size());
        for (int y = 0; y < rows.length; y++) assertArrayEquals(rows[y], decoded[y]);
    }

    @Test
    public void invalidBoundsAndOverflowCannotCreateHugeScans() {
        for (int[] a :
                new int[][] {
                    {Integer.MAX_VALUE, 0, 1, 0, 255, 0},
                    {0, Integer.MIN_VALUE, 1, 0, 255, 0},
                    {0, 0, 3, 0, 255, 0},
                    {TerrainPage.MIN_CHUNK, 0, 1, 0, 255, 0},
                    {TerrainPage.MAX_CHUNK, 0, 1, 0, 255, 0},
                    {0, 0, 0, -1, 255, 0},
                    {0, 0, 0, 0, 256, 0},
                    {0, 0, 0, 80, 79, 0},
                    {0, 0, 0, 0, 255, 256},
                    {0, 0, 0, 0, 255, -1}
                }) {
            try {
                new TerrainPage(a[0], a[1], a[2], a[3], a[4], a[5]);
                fail(Arrays.toString(a));
            } catch (IllegalArgumentException expected) {
            }
        }
        assertEquals(-29999984, new TerrainPage(TerrainPage.MIN_CHUNK, 0, 0, 0, 0, 0).minX);
        assertEquals(29999983, new TerrainPage(TerrainPage.MAX_CHUNK, 0, 0, 0, 0, 0).maxX);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void agentNeedsOnlyScopeAndCanDiscoverOptionalBounds() {
        Tools.validate("read_terrain", map("session", "s", "player", "p", "dimension", 0));
        Map<String, Object> schema = null;
        for (Map<String, Object> tool : Tools.list())
            if (tool.get("name").equals("read_terrain"))
                schema = (Map<String, Object>) tool.get("inputSchema");
        assertNotNull(schema);
        assertEquals(Arrays.asList("session", "player", "dimension"), schema.get("required"));
        for (Map<String, Object> invalid :
                Arrays.asList(
                        map("chunkRadius", 3),
                        map("minY", -1),
                        map("maxY", 256),
                        map("offset", 256),
                        map("entityOffset", -1),
                        map("tileOffset", -1),
                        map("chunkX", Integer.MAX_VALUE))) {
            Map<String, Object> args = map("session", "s", "player", "p", "dimension", 0);
            args.putAll(invalid);
            try {
                Tools.validate("read_terrain", args);
                fail(invalid.toString());
            } catch (IllegalArgumentException expected) {
            }
        }
    }
}
