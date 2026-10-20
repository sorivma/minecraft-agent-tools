package dev.minecraftagenttools.forge.schematica;

import dev.minecraftagenttools.schematica.contract.Plans;
import java.io.*;
import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.nbt.*;
import net.minecraft.tileentity.TileEntity;

/** Serializes only the generated schematic world, never live-world tile entities. */
final class SchematicExport {
    static byte[] bytes(Plans.Plan plan, SchematicaApi.Ghost ghost) throws IOException {
        NBTTagCompound root = new NBTTagCompound();
        root.setString("Materials", "Alpha");
        root.setShort("Width", (short) plan.layout.width);
        root.setShort("Height", (short) plan.layout.height);
        root.setShort("Length", (short) plan.layout.length);
        Map<String, Integer> ids = new LinkedHashMap<String, Integer>();
        ids.put("minecraft:air", 0);
        for (SchematicaApi.Resolved p : ghost.palette) {
            String name = Block.blockRegistry.getNameForObject(p.block);
            if (!ids.containsKey(name)) ids.put(name, ids.size());
        }
        NBTTagCompound mappings = new NBTTagCompound();
        for (Map.Entry<String, Integer> e : ids.entrySet())
            mappings.setShort(e.getKey(), e.getValue().shortValue());
        byte[] blocks = new byte[ghost.cells.length], meta = new byte[ghost.cells.length];
        NBTTagList tiles = new NBTTagList();
        for (int i = 0; i < blocks.length; i++)
            if (ghost.cells[i] >= 0) {
                SchematicaApi.Resolved p = ghost.palette.get(ghost.cells[i]);
                blocks[i] = ids.get(Block.blockRegistry.getNameForObject(p.block)).byteValue();
                meta[i] = (byte) p.meta;
                if (p.block.hasTileEntity(p.meta)) {
                    int x = i % plan.layout.width,
                            y = i / (plan.layout.width * plan.layout.length),
                            z = (i / plan.layout.width) % plan.layout.length;
                    TileEntity tile = ghost.world.getTileEntity(x, y, z);
                    NBTTagCompound nbt = new NBTTagCompound();
                    tile.writeToNBT(nbt);
                    tiles.appendTag(nbt);
                }
            }
        root.setTag("SchematicaMapping", mappings);
        root.setByteArray("Blocks", blocks);
        root.setByteArray("Data", meta);
        // Local registry mapping IDs fit a byte; zero high bits work in both GTNH formats.
        root.setByteArray("AddBlocks", new byte[blocks.length]);
        root.setTag("TileEntities", tiles);
        root.setTag("Entities", new NBTTagList());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CompressedStreamTools.writeCompressed(root, out);
        return out.toByteArray();
    }
}
