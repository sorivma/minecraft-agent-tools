package dev.minecraftagenttools.protocol.world;

import java.util.*;

/** Chunk-aligned terrain bounds, paged by complete horizontal layers. No game dependencies. */
public final class TerrainPage {
    public static final int MAX_CELLS = 65536;
    public static final int MIN_CHUNK = -1874999, MAX_CHUNK = 1874998;
    public final int minX, maxX, minZ, maxZ, minY, maxY, startY, endY, width, depth;
    public final int offset, nextOffset;

    public TerrainPage(int chunkX, int chunkZ, int chunkRadius, int minY, int maxY, int offset) {
        if (chunkRadius < 0
                || chunkRadius > 2
                || chunkX < MIN_CHUNK + chunkRadius
                || chunkX > MAX_CHUNK - chunkRadius
                || chunkZ < MIN_CHUNK + chunkRadius
                || chunkZ > MAX_CHUNK - chunkRadius)
            throw new IllegalArgumentException(
                    "Chunk bounds outside supported world coordinates; chunkRadius must be 0-2");
        if (minY < 0 || maxY > 255 || minY > maxY || offset < 0 || offset > maxY - minY)
            throw new IllegalArgumentException("Invalid terrain height range or layer offset");
        this.minX = (chunkX - chunkRadius) * 16;
        this.maxX = (chunkX + chunkRadius) * 16 + 15;
        this.minZ = (chunkZ - chunkRadius) * 16;
        this.maxZ = (chunkZ + chunkRadius) * 16 + 15;
        this.minY = minY;
        this.maxY = maxY;
        this.offset = offset;
        width = maxX - minX + 1;
        depth = maxZ - minZ + 1;
        startY = minY + offset;
        endY = Math.min(maxY, startY + MAX_CELLS / (width * depth) - 1);
        nextOffset = endY < maxY ? endY - minY + 1 : -1;
    }

    /** Append x-axis runs [x, y, z, length, paletteIndex]; negative IDs are omitted cells. */
    public static void appendRow(List<Object> runs, int minX, int y, int z, int[] paletteIds) {
        for (int i = 0; i < paletteIds.length; ) {
            int end = i + 1;
            while (end < paletteIds.length && paletteIds[end] == paletteIds[i]) end++;
            if (paletteIds[i] >= 0) runs.add(Arrays.asList(minX + i, y, z, end - i, paletteIds[i]));
            i = end;
        }
    }
}
