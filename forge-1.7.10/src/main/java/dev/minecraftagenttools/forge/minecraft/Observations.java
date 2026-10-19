package dev.minecraftagenttools.forge.minecraft;

import static dev.minecraftagenttools.protocol.data.Data.*;

import dev.minecraftagenttools.minecraft.contract.Tools;
import dev.minecraftagenttools.protocol.world.Cuboid;
import dev.minecraftagenttools.protocol.world.Snapshots;
import dev.minecraftagenttools.protocol.world.WorldSessions;
import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.entity.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.IInventory;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.DimensionManager;

/** Confined to the server thread; returned values are detached from the game. */
final class Observations {
    private final String instance = UUID.randomUUID().toString();
    private final WorldSessions<WorldServer> worlds = new WorldSessions<WorldServer>();
    private final Snapshots snapshots = new Snapshots(32);
    private final Catalog catalog = new Catalog();
    private final WorldMarkers markers = new WorldMarkers();

    void tick() {
        refreshWorlds();
        markers.tick(worlds);
    }

    Object call(String tool, Map<String, Object> args) {
        Tools.validate(tool, args);
        refreshWorlds();
        if (tool.equals("list_sessions")) return discovery();
        int dimension = integer(args, "dimension", 0, Integer.MIN_VALUE, Integer.MAX_VALUE);
        WorldServer world = worlds.require(dimension, string(args, "session"));
        EntityPlayerMP player = findPlayer(string(args, "player"));
        if (player.worldObj != world || player.dimension != dimension)
            throw new IllegalArgumentException("PLAYER_CHANGED_DIMENSION");
        Object result;
        switch (tool) {
            case "get_player_context":
                result = context(player, world);
                break;
            case "get_inventory":
                result = GameData.inventory(player);
                break;
            case "get_interaction":
                result = interaction(player, world);
                break;
            case "observe":
                result =
                        map(
                                "context",
                                context(player, world),
                                "inventory",
                                GameData.inventory(player),
                                "interaction",
                                interaction(player, world),
                                "entities",
                                entities(player, world, args));
                break;
            case "list_entities":
                result = entities(player, world, args);
                break;
            case "inspect_entity":
                {
                    Entity entity =
                            world.getEntityByID(integer(args, "entityId", 0, 0, Integer.MAX_VALUE));
                    if (entity == null) throw new IllegalArgumentException("ENTITY_NOT_LOADED");
                    result = GameData.entity(entity, true);
                    break;
                }
            case "inspect_block":
                result =
                        block(
                                world,
                                coordinate(args, "x"),
                                integer(args, "y", 0, 0, 255),
                                coordinate(args, "z"),
                                true);
                break;
            case "scan_area":
                result = scan(world, args);
                break;
            case "read_terrain":
                result = TerrainReader.read(player, world, args);
                break;
            case "get_changes":
                result = changes(player, world, args);
                break;
            case "get_registry":
                result = catalog.registry(args);
                break;
            case "highlight_entity":
            case "highlight_block":
            case "set_waypoint":
            case "list_markers":
            case "remove_marker":
            case "clear_markers":
                result = markers.call(tool, args, worlds.session(dimension), world, player);
                break;
            default:
                throw new IllegalArgumentException("Unknown tool");
        }
        return map(
                "session",
                worlds.session(dimension),
                "dimension",
                dimension,
                "player",
                player.getUniqueID().toString(),
                "tick",
                world.getTotalWorldTime(),
                "observedAtEpochMs",
                System.currentTimeMillis(),
                "result",
                result);
    }

    private void refreshWorlds() {
        Map<Integer, WorldServer> active = new HashMap<Integer, WorldServer>();
        for (WorldServer world : DimensionManager.getWorlds()) {
            active.put(world.provider.dimensionId, world);
        }
        worlds.refresh(active);
    }

    private Object discovery() {
        List<Object> dimensions = new ArrayList<Object>();
        for (Integer id : worlds.dimensions())
            dimensions.add(
                    map(
                            "dimension",
                            id,
                            "session",
                            worlds.session(id),
                            "name",
                            worlds.world(id).provider.getDimensionName(),
                            "worldName",
                            worlds.world(id).getWorldInfo().getWorldName()));
        List<Object> players = new ArrayList<Object>();
        for (Object o : MinecraftServer.getServer().getConfigurationManager().playerEntityList) {
            EntityPlayerMP p = (EntityPlayerMP) o;
            players.add(
                    map(
                            "uuid",
                            p.getUniqueID().toString(),
                            "name",
                            p.getCommandSenderName(),
                            "dimension",
                            p.dimension,
                            "session",
                            worlds.session(p.dimension)));
        }
        return map(
                "serverInstance",
                instance,
                "minecraftVersion",
                "1.7.10",
                "dimensions",
                dimensions,
                "players",
                players,
                "visibility",
                "server-authoritative loaded worlds; no implicit chunk loading",
                "tools",
                Tools.list());
    }

    private EntityPlayerMP findPlayer(String uuid) {
        for (Object o : MinecraftServer.getServer().getConfigurationManager().playerEntityList) {
            EntityPlayerMP p = (EntityPlayerMP) o;
            if (p.getUniqueID().toString().equals(uuid)) return p;
        }
        throw new IllegalArgumentException("PLAYER_OFFLINE_OR_UNKNOWN_UUID");
    }

    private Object context(EntityPlayerMP p, WorldServer w) {
        int x = MathHelper.floor_double(p.posX),
                y = MathHelper.floor_double(p.posY),
                z = MathHelper.floor_double(p.posZ);
        return map(
                "player",
                GameData.entity(p, false),
                "world",
                map(
                        "timeOfDay",
                        w.getWorldTime(),
                        "totalTicks",
                        w.getTotalWorldTime(),
                        "raining",
                        w.isRaining(),
                        "thundering",
                        w.isThundering(),
                        "difficulty",
                        w.difficultySetting.getDifficultyId(),
                        "seed",
                        Long.toString(w.getSeed())),
                "feetBlock",
                block(w, x, y, z, false),
                "belowFeet",
                block(w, x, y - 1, z, false));
    }

    static int coordinate(Map<String, Object> args, String key) {
        return integer(args, key, 0, -29999984, 29999983);
    }

    static boolean loaded(WorldServer w, int x, int z) {
        return w.getChunkProvider().chunkExists(x >> 4, z >> 4);
    }

    static Map<String, Object> block(WorldServer w, int x, int y, int z, boolean detailed) {
        Map<String, Object> data = map("x", x, "y", y, "z", z);
        if (y < 0 || y > 255) {
            data.put("availability", "outside_world_height");
            return data;
        }
        if (!loaded(w, x, z)) {
            data.put("availability", "chunk_unloaded");
            return data;
        }
        Block b = w.getBlock(x, y, z);
        Chunk chunk = w.getChunkFromChunkCoords(x >> 4, z >> 4);
        BiomeGenBase biome = w.getBiomeGenForCoords(x, z);
        data.putAll(
                map(
                        "availability",
                        "loaded",
                        "block",
                        Block.blockRegistry.getNameForObject(b),
                        "metadata",
                        w.getBlockMetadata(x, y, z),
                        "light",
                        map(
                                "block",
                                chunk.getSavedLightValue(EnumSkyBlock.Block, x & 15, y, z & 15),
                                "sky",
                                w.provider.hasNoSky
                                        ? 0
                                        : chunk.getSavedLightValue(
                                                EnumSkyBlock.Sky, x & 15, y, z & 15),
                                "semantics",
                                "stored light levels, without neighbor brightness"),
                        "skyVisible",
                        !w.provider.hasNoSky && w.canBlockSeeTheSky(x, y, z),
                        "height",
                        w.getHeightValue(x, z),
                        "biome",
                        map("id", biome.biomeID, "name", biome.biomeName),
                        "liquid",
                        b.getMaterial().isLiquid(),
                        "solid",
                        b.getMaterial().isSolid(),
                        "replaceable",
                        b.isReplaceable(w, x, y, z)));
        if (detailed) {
            data.put("hardness", b.getBlockHardness(w, x, y, z));
            TileEntity tile = w.getTileEntity(x, y, z);
            if (tile != null) {
                NBTTagCompound nbt = new NBTTagCompound();
                tile.writeToNBT(nbt);
                data.put("tileNbt", GameData.nbt(nbt));
                if (tile instanceof IInventory) {
                    IInventory inv = (IInventory) tile;
                    data.put(
                            "inventory",
                            map(
                                    "slots",
                                    GameData.inventory(inv),
                                    "totalSlots",
                                    inv.getSizeInventory(),
                                    "truncated",
                                    inv.getSizeInventory() > 256));
                }
            }
        }
        return data;
    }

    private Object scan(WorldServer w, Map<String, Object> a) {
        int minX = coordinate(a, "minX"),
                maxX = coordinate(a, "maxX"),
                minZ = coordinate(a, "minZ"),
                maxZ = coordinate(a, "maxZ");
        int minY = integer(a, "minY", 0, 0, 255), maxY = integer(a, "maxY", 0, 0, 255);
        Cuboid bounds = new Cuboid(minX, minY, minZ, maxX, maxY, maxZ);
        int total = bounds.volume,
                offset = integer(a, "offset", 0, 0, total),
                limit = integer(a, "limit", 256, 1, 512);
        List<Object> cells = new ArrayList<Object>();
        for (int i = offset; i < Math.min(total, offset + limit); i++) {
            int[] position = bounds.at(i);
            cells.add(block(w, position[0], position[1], position[2], false));
        }
        return map(
                "bounds",
                Arrays.asList(minX, minY, minZ, maxX, maxY, maxZ),
                "order",
                "x then y then z",
                "blocks",
                cells,
                "totalCells",
                total,
                "offset",
                offset,
                "nextOffset",
                offset + cells.size() < total ? offset + cells.size() : null,
                "truncated",
                offset + cells.size() < total,
                "consistency",
                "each page sampled independently");
    }

    private List<Entity> nearby(EntityPlayerMP p, WorldServer w, int radius) {
        List<Entity> entities = new ArrayList<Entity>();
        for (Object o :
                w.getEntitiesWithinAABBExcludingEntity(
                        p, p.boundingBox.expand(radius, radius, radius))) {
            Entity e = (Entity) o;
            if (e.getDistanceSqToEntity(p) <= radius * radius) entities.add(e);
        }
        Collections.sort(entities, Comparator.comparingInt(Entity::getEntityId));
        return entities;
    }

    private Object entities(EntityPlayerMP p, WorldServer w, Map<String, Object> a) {
        int radius = integer(a, "radius", 16, 1, 64),
                limit = integer(a, "limit", 64, 1, 256),
                offset = integer(a, "offset", 0, 0, Integer.MAX_VALUE);
        List<Entity> entities = nearby(p, w, radius);
        List<Object> out = new ArrayList<Object>();
        for (int i = Math.min(offset, entities.size());
                i < entities.size() && out.size() < limit;
                i++) out.add(GameData.entity(entities.get(i), false));
        return map(
                "center",
                Arrays.asList(p.posX, p.posY, p.posZ),
                "radius",
                radius,
                "entities",
                out,
                "total",
                entities.size(),
                "offset",
                offset,
                "nextOffset",
                (long) offset + out.size() < entities.size() ? offset + out.size() : null,
                "truncated",
                (long) offset + out.size() < entities.size(),
                "coverage",
                "loaded entities only; pages sampled independently");
    }

    private Object interaction(EntityPlayerMP p, WorldServer w) {
        double reach = p.theItemInWorldManager.getBlockReachDistance();
        Vec3 eye = Vec3.createVectorHelper(p.posX, p.posY + p.getEyeHeight(), p.posZ),
                look = p.getLook(1.0F);
        Vec3 end = eye.addVector(look.xCoord * reach, look.yCoord * reach, look.zCoord * reach);
        // Check all chunks touched by the short segment's bounding rectangle before vanilla ray
        // tracing.
        for (int cx = MathHelper.floor_double(Math.min(eye.xCoord, end.xCoord)) >> 4;
                cx <= (MathHelper.floor_double(Math.max(eye.xCoord, end.xCoord)) >> 4);
                cx++)
            for (int cz = MathHelper.floor_double(Math.min(eye.zCoord, end.zCoord)) >> 4;
                    cz <= (MathHelper.floor_double(Math.max(eye.zCoord, end.zCoord)) >> 4);
                    cz++)
                if (!w.getChunkProvider().chunkExists(cx, cz))
                    return map(
                            "availability",
                            "ray_crosses_unloaded_chunk",
                            "openContainer",
                            GameData.container(p.openContainer));
        MovingObjectPosition hit = w.rayTraceBlocks(eye, end);
        double nearest = hit == null ? reach : eye.distanceTo(hit.hitVec);
        Object target =
                hit == null
                        ? null
                        : map(
                                "kind",
                                "block",
                                "x",
                                hit.blockX,
                                "y",
                                hit.blockY,
                                "z",
                                hit.blockZ,
                                "side",
                                hit.sideHit);
        for (Object o :
                w.getEntitiesWithinAABBExcludingEntity(
                        p,
                        p.boundingBox
                                .addCoord(
                                        look.xCoord * reach,
                                        look.yCoord * reach,
                                        look.zCoord * reach)
                                .expand(1, 1, 1))) {
            Entity e = (Entity) o;
            if (!e.canBeCollidedWith()) continue;
            float border = e.getCollisionBorderSize();
            AxisAlignedBB box = e.boundingBox.expand(border, border, border);
            MovingObjectPosition intercept = box.calculateIntercept(eye, end);
            double distance =
                    box.isVecInside(eye)
                            ? 0
                            : intercept == null
                                    ? Double.POSITIVE_INFINITY
                                    : eye.distanceTo(intercept.hitVec);
            if (distance < nearest) {
                nearest = distance;
                target =
                        map(
                                "kind",
                                "entity",
                                "entityId",
                                e.getEntityId(),
                                "uuid",
                                e.getUniqueID().toString());
            }
        }
        return map(
                "target",
                target,
                "distance",
                target == null ? null : nearest,
                "reach",
                reach,
                "source",
                "server ray approximation; client crosshair/GUI unavailable",
                "openContainer",
                GameData.container(p.openContainer));
    }

    private Object changes(EntityPlayerMP p, WorldServer w, Map<String, Object> a) {
        int radius = integer(a, "radius", 2, 1, 4);
        int x = MathHelper.floor_double(p.posX),
                y = MathHelper.floor_double(p.posY),
                z = MathHelper.floor_double(p.posZ);
        Map<String, Object> values = new LinkedHashMap<String, Object>();
        values.put("player", GameData.entity(p, false));
        values.put("inventory", GameData.inventory(p));
        values.put("weather", map("raining", w.isRaining(), "thundering", w.isThundering()));
        List<Entity> entities = nearby(p, w, radius);
        if (entities.size() > 256)
            throw new IllegalArgumentException(
                    "Too many entities for complete change snapshot; reduce radius");
        for (Entity e : entities)
            values.put("entity:" + e.getUniqueID(), GameData.entity(e, false));
        for (int bx = x - radius; bx <= x + radius; bx++)
            for (int by = Math.max(0, y - radius); by <= Math.min(255, y + radius); by++)
                for (int bz = z - radius; bz <= z + radius; bz++)
                    values.put("block:" + bx + ":" + by + ":" + bz, block(w, bx, by, bz, false));
        String scope = worlds.session(p.dimension) + ":" + p.getUniqueID() + ":" + radius;
        Map<String, Object> result = snapshots.compare(scope, (String) a.get("cursor"), values);
        result.put(
                "bounds",
                Arrays.asList(
                        x - radius,
                        Math.max(0, y - radius),
                        z - radius,
                        x + radius,
                        Math.min(255, y + radius),
                        z + radius));
        result.put(
                "coverage",
                "moving player-centered cube of block states; spherical entities; tile inventories require inspect_block");
        return result;
    }
}
