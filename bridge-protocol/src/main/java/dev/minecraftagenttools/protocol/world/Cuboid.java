package dev.minecraftagenttools.protocol.world;

/** Inclusive scan bounds; bounded before multiplication to prevent integer overflow. */
public final class Cuboid {
    private final int x, y, z, width, height;
    public final int volume;

    public Cuboid(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        if (minY < 0 || maxY > 255 || maxX < minX || maxY < minY || maxZ < minZ)
            throw new IllegalArgumentException("Invalid scan bounds");
        long w = (long) maxX - minX + 1, h = (long) maxY - minY + 1, d = (long) maxZ - minZ + 1;
        if (w > 32768 || d > 32768 || w * h * d > 32768)
            throw new IllegalArgumentException("Volume limit: 32768");
        x = minX;
        y = minY;
        z = minZ;
        width = (int) w;
        height = (int) h;
        volume = (int) (w * h * d);
    }

    public int[] at(int offset) {
        if (offset < 0 || offset >= volume)
            throw new IllegalArgumentException("Offset outside scan");
        return new int[] {
            x + offset % width, y + (offset / width) % height, z + offset / (width * height)
        };
    }
}
