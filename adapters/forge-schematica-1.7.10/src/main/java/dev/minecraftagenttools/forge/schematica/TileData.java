package dev.minecraftagenttools.forge.schematica;

import java.util.function.IntPredicate;
import net.minecraft.nbt.*;

/** Detached render-only tile data; the schematic world never ticks these tiles. */
final class TileData {
    static NBTTagCompound parse(String value) {
        if (value.length() > 8192)
            throw new IllegalArgumentException("tileNbt exceeds 8192 characters");
        // Bound nesting before calling the recursive vanilla SNBT parser.
        int depth = 0;
        boolean quoted = false, escaped = false;
        for (char c : value.toCharArray()) {
            if (escaped) {
                escaped = false;
                continue;
            }
            if (c == '\\') {
                escaped = true;
                continue;
            }
            if (c == '"') quoted = !quoted;
            if (!quoted) {
                if (c == '{' || c == '[') {
                    if (++depth > 32)
                        throw new IllegalArgumentException("tileNbt nesting exceeds 32");
                } else if (c == '}' || c == ']') depth--;
            }
        }
        try {
            NBTBase nbt = JsonToNBT.func_150315_a(value);
            if (!(nbt instanceof NBTTagCompound))
                throw new IllegalArgumentException("tileNbt must be an SNBT compound");
            return (NBTTagCompound) nbt;
        } catch (NBTException e) {
            throw new IllegalArgumentException("Invalid tileNbt: " + e.getMessage(), e);
        }
    }

    static NBTTagCompound at(NBTTagCompound template, int x, int y, int z) {
        NBTTagCompound result =
                template == null ? new NBTTagCompound() : (NBTTagCompound) template.copy();
        result.setInteger("x", x);
        result.setInteger("y", y);
        result.setInteger("z", z);
        return result;
    }

    static int neighbors(int width, int height, int length, int index, IntPredicate connects) {
        int x = index % width, y = index / (width * length), z = (index / width) % length;
        int[][] offsets = {{0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}};
        int mask = 0;
        for (int side = 0; side < 6; side++) {
            int nx = x + offsets[side][0], ny = y + offsets[side][1], nz = z + offsets[side][2];
            if (nx >= 0
                    && nx < width
                    && ny >= 0
                    && ny < height
                    && nz >= 0
                    && nz < length
                    && connects.test(nx + (ny * length + nz) * width)) mask |= 1 << side;
        }
        return mask;
    }
}
