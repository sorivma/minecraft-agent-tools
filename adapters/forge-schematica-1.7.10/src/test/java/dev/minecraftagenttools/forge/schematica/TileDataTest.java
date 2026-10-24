package dev.minecraftagenttools.forge.schematica;

import static org.junit.Assert.*;

import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;

public class TileDataTest {
    @Test
    public void unpaintedHatchUsesRawNbtColorZeroAndValidTextureIndex() {
        NBTTagCompound nbt = TileData.at(null, 0, 0, 0);
        TileData.machine(nbt, 30, 2);
        // GT getColorization() subtracts one from the stored raw color; MTEHatch
        // indexes its 17 casing textures with getColorization() + 1.
        assertEquals(0, nbt.getByte("mColor"));
        assertEquals(30, nbt.getInteger("mID"));
        assertEquals(2, nbt.getShort("mFacing"));
        NBTTagCompound painted = TileData.parse("{mColor:16b}");
        TileData.machine(painted, 31, 5);
        assertEquals(16, painted.getByte("mColor"));
    }

    @Test
    public void modPipeDataIsCopiedWithLocalCoordinatesAndNestedDataIntact() {
        NBTTagCompound template =
                TileData.parse("{x:900,y:80,z:900,pipe:{type:42},connections:48b}");
        NBTTagCompound first = TileData.at(template, 1, 2, 3);
        NBTTagCompound second = TileData.at(template, 4, 5, 6);
        assertEquals(1, first.getInteger("x"));
        assertEquals(4, second.getInteger("x"));
        assertEquals(900, template.getInteger("x"));
        assertEquals(42, first.getCompoundTag("pipe").getInteger("type"));
        first.getCompoundTag("pipe").setInteger("type", 1);
        assertEquals(42, second.getCompoundTag("pipe").getInteger("type"));
        assertEquals(48, first.getByte("connections"));
    }

    @Test
    public void gtPipeConnectionsUseForgeDirectionsAndDoNotWrapRows() {
        assertEquals(63, TileData.neighbors(3, 3, 3, 13, i -> true));
        assertEquals(2 | 8 | 32, TileData.neighbors(3, 3, 3, 0, i -> true));
        assertEquals(32, TileData.neighbors(3, 1, 1, 0, i -> true));
        assertEquals(16 | 32, TileData.neighbors(3, 1, 1, 1, i -> true));
        assertEquals(16, TileData.neighbors(3, 1, 1, 2, i -> true));
        assertEquals(0, TileData.neighbors(3, 1, 1, 1, i -> false));
    }

    @Test(expected = IllegalArgumentException.class)
    public void excessiveNestingIsRejectedBeforeVanillaRecursion() {
        String value = "{}";
        for (int i = 0; i < 33; i++) value = "{nested:" + value + "}";
        TileData.parse(value);
    }

    @Test(expected = IllegalArgumentException.class)
    public void malformedNbtIsRejected() {
        TileData.parse("{broken");
    }
}
