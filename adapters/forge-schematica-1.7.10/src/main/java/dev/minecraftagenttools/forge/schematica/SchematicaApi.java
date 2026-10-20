package dev.minecraftagenttools.forge.schematica;

import static dev.minecraftagenttools.protocol.data.Data.*;

import dev.minecraftagenttools.schematica.contract.*;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/** Fixed public Schematica GTNH 1.12.6 profile; Java 8 adapter, no bundled mod implementation. */
final class SchematicaApi {
    private static final String ROOT = "com.github.lunatrius.schematica.";
    final Class<?> schematicType, worldType, chunkType, rendererType;
    final Object renderer;
    final Constructor<?> chunkConstructor;

    SchematicaApi() throws ReflectiveOperationException {
        schematicType = Class.forName(ROOT + "api.ISchematic");
        worldType = Class.forName(ROOT + "client.world.SchematicWorld");
        chunkType = Class.forName(ROOT + "client.renderer.RendererSchematicChunk");
        rendererType = Class.forName(ROOT + "client.renderer.RendererSchematicGlobal");
        renderer = rendererType.getField("INSTANCE").get(null);
        try {
            chunkConstructor = ChunkRenderPass.constructor(chunkType, worldType);
        } catch (Exception e) {
            throw new ReflectiveOperationException("Cannot initialize Schematica render passes", e);
        }
    }

    static Object call(Object target, String method, Class<?>[] types, Object... args)
            throws ReflectiveOperationException {
        try {
            return target.getClass().getMethod(method, types).invoke(target, args);
        } catch (InvocationTargetException e) {
            throw new IllegalArgumentException("Schematica API: " + e.getCause(), e.getCause());
        }
    }

    static final class Resolved {
        final Block block;
        final int meta, machineId, facing;
        final NBTTagCompound tileNbt;
        final int connections;

        Resolved(
                Block block,
                int meta,
                int machineId,
                int facing,
                NBTTagCompound tileNbt,
                int connections) {
            this.block = block;
            this.meta = meta;
            this.machineId = machineId;
            this.facing = facing;
            this.tileNbt = tileNbt;
            this.connections = connections;
        }
    }

    Resolved resolve(Map<String, Object> p) throws ReflectiveOperationException {
        String name = string(p, "block");
        if (!Block.blockRegistry.containsKey(name))
            throw new IllegalArgumentException("Unknown block: " + name);
        Block block = (Block) Block.blockRegistry.getObject(name);
        int meta = integer(p, "meta", 0, 0, 15), id = integer(p, "machineId", 0, 0, 32767);
        int facing = integer(p, "facing", 2, 0, 5);
        if (id > 0) {
            Object[] machines =
                    (Object[])
                            Class.forName("gregtech.api.GregTechAPI")
                                    .getField("METATILEENTITIES")
                                    .get(null);
            if (id >= machines.length || machines[id] == null)
                throw new IllegalArgumentException("Unknown GregTech machineId: " + id);
            meta =
                    ((Number) call(machines[id], "getTileEntityBaseType", new Class<?>[0]))
                            .intValue();
            if (meta < 0 || meta > 15)
                throw new IllegalArgumentException("Unsupported GregTech tile base type: " + meta);
            if (p.containsKey("connections") && meta < 4)
                throw new IllegalArgumentException(
                        "connections is only valid for GregTech pipes/cables");
        } else if (block.hasTileEntity(meta) && !p.containsKey("tileNbt"))
            throw new IllegalArgumentException(
                    "This block requires tileNbt (SNBT render data) or a GregTech machineId: "
                            + name);
        if (p.containsKey("tileNbt") && !block.hasTileEntity(meta))
            throw new IllegalArgumentException("tileNbt requires a tile entity block: " + name);
        return new Resolved(
                block,
                meta,
                id,
                facing,
                p.containsKey("tileNbt") ? TileData.parse(string(p, "tileNbt")) : null,
                integer(p, "connections", -1, -1, 63));
    }

    Ghost build(Layout layout, int x, int y, int z) throws ReflectiveOperationException {
        List<Resolved> palette = new ArrayList<Resolved>();
        for (Map<String, Object> p : layout.palette) palette.add(resolve(p));
        int[] cells = layout.cells();
        int tiles = 0;
        for (int p : cells)
            if (p >= 0 && palette.get(p).block.hasTileEntity(palette.get(p).meta) && ++tiles > 512)
                throw new IllegalArgumentException("At most 512 machine tiles per plan");
        Object schematic =
                Class.forName(ROOT + "world.storage.Schematic")
                        .getConstructor(ItemStack.class, int.class, int.class, int.class)
                        .newInstance(
                                new ItemStack(Blocks.grass),
                                layout.width,
                                layout.height,
                                layout.length);
        Method setBlock =
                schematicType.getMethod(
                        "setBlock", int.class, int.class, int.class, Block.class, int.class);
        for (int i = 0; i < cells.length; i++)
            if (cells[i] >= 0) {
                Resolved p = palette.get(cells[i]);
                if (!Boolean.TRUE.equals(
                        setBlock.invoke(
                                schematic,
                                i % layout.width,
                                i / (layout.width * layout.length),
                                (i / layout.width) % layout.length,
                                p.block,
                                p.meta)))
                    throw new IllegalArgumentException("Cannot set schematic block");
            }
        World world = (World) worldType.getConstructor(schematicType).newInstance(schematic);
        for (int i = 0; i < cells.length; i++)
            if (cells[i] >= 0) {
                Resolved p = palette.get(cells[i]);
                if (!p.block.hasTileEntity(p.meta)) continue;
                int bx = i % layout.width,
                        by = i / (layout.width * layout.length),
                        bz = (i / layout.width) % layout.length;
                TileEntity tile = p.block.createTileEntity(world, p.meta);
                if (tile == null)
                    throw new IllegalArgumentException("Block did not create a tile entity");
                NBTTagCompound nbt = TileData.at(p.tileNbt, bx, by, bz);
                if (p.machineId > 0) {
                    nbt.setInteger("mID", p.machineId);
                    nbt.setShort("mFacing", (short) p.facing);
                    if (!nbt.hasKey("mColor")) nbt.setByte("mColor", (byte) -1);
                    if (p.meta >= 4) {
                        int mask = p.connections;
                        if (mask < 0)
                            mask =
                                    TileData.neighbors(
                                            layout.width,
                                            layout.height,
                                            layout.length,
                                            i,
                                            cell ->
                                                    cells[cell] >= 0
                                                            && palette.get(cells[cell]).machineId
                                                                    > 0);
                        nbt.setByte("mConnections", (byte) mask);
                    }
                }
                tile.setWorldObj(world);
                tile.readFromNBT(nbt);
                world.setTileEntity(bx, by, bz, tile);
                if (p.machineId > 0) GregTechTiles.bind(tile);
            }
        Ghost ghost = new Ghost(world, layout, palette, cells);
        ghost.display(x, y, z, true, -1);
        return ghost;
    }

    final class Ghost implements SchematicClient.Ghost {
        final World world;
        final Layout layout;
        final List<Resolved> palette;
        final int[] cells;
        final List<Object> chunks = new ArrayList<Object>();
        final RenderBlocks blocks;
        private int refreshCursor;

        Ghost(World world, Layout layout, List<Resolved> palette, int[] cells) {
            this.world = world;
            this.layout = layout;
            this.palette = palette;
            this.cells = cells;
            blocks = new RenderBlocks((IBlockAccess) world);
        }

        public void display(int x, int y, int z, boolean visible, int layer)
                throws ReflectiveOperationException {
            Object position = worldType.getField("position").get(world);
            position.getClass()
                    .getMethod("set", int.class, int.class, int.class)
                    .invoke(position, x, y, z);
            worldType.getField("isRendering").setBoolean(world, visible);
            worldType.getField("isRenderingLayer").setBoolean(world, layer >= 0);
            worldType.getField("renderingLayer").setInt(world, Math.max(0, layer));
            for (Object chunk : chunks) chunkType.getMethod("setDirty").invoke(chunk);
        }

        public void dirty() throws ReflectiveOperationException {
            // Do not restart a full rebuild each second: large plans could otherwise starve
            // their last chunks. Refresh one cached chunk at a time after initial compilation.
            for (Object chunk : chunks)
                if (Boolean.TRUE.equals(chunkType.getMethod("getDirty").invoke(chunk))) return;
            if (!chunks.isEmpty())
                chunkType.getMethod("setDirty").invoke(chunks.get(refreshCursor++ % chunks.size()));
        }

        @SuppressWarnings("unchecked")
        public void render() throws ReflectiveOperationException {
            if (chunks.isEmpty()) {
                // Schematica chunks are 16x16x16. Allocate GL resources only in a render callback.
                for (int y = 0; y < (layout.height + 15) / 16; y++)
                    for (int z = 0; z < (layout.length + 15) / 16; z++)
                        for (int x = 0; x < (layout.width + 15) / 16; x++)
                            chunks.add(chunkConstructor.newInstance(world, x, y, z));
            }
            List<Object> active =
                    (List<Object>)
                            rendererType.getField("sortedRendererSchematicChunk").get(renderer);
            List<Object> saved = new ArrayList<Object>(active);
            Field field = rendererType.getField("renderBlocks");
            Object savedBlocks = field.get(renderer);
            boolean savedCanUpdate = (Boolean) chunkType.getMethod("getCanUpdate").invoke(null);
            try {
                active.clear();
                active.addAll(chunks);
                field.set(renderer, blocks);
                chunkType.getMethod("setCanUpdate", boolean.class).invoke(null, true);
                rendererType.getMethod("render", worldType).invoke(renderer, world);
            } finally {
                active.clear();
                active.addAll(saved);
                field.set(renderer, savedBlocks);
                chunkType.getMethod("setCanUpdate", boolean.class).invoke(null, savedCanUpdate);
            }
        }

        public void close() throws ReflectiveOperationException {
            for (Object chunk : chunks) chunkType.getMethod("delete").invoke(chunk);
            chunks.clear();
        }

        public byte[] export(Plans.Plan plan) throws java.io.IOException {
            return SchematicExport.bytes(plan, this);
        }
    }
}
