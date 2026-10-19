package dev.minecraftagenttools.minecraft.contract;

import static dev.minecraftagenttools.protocol.data.Data.*;

import dev.minecraftagenttools.protocol.world.TerrainPage;
import java.util.*;

public final class Tools {
    private Tools() {}

    public static List<Map<String, Object>> list() {
        List<Map<String, Object>> tools = new ArrayList<Map<String, Object>>();
        tools.add(
                tool(
                        "list_sessions",
                        "Discover server session, loaded dimensions and online player UUIDs.",
                        false));
        tools.add(
                tool(
                        "observe",
                        "Player, environment, nearby entities, inventory and server-computed targeting.",
                        true,
                        "radius",
                        "limit"));
        tools.add(
                tool(
                        "get_player_context",
                        "Authoritative player state and local environment.",
                        true));
        tools.add(
                tool(
                        "get_inventory",
                        "Player inventory, armor, ender chest and server-side open container slots.",
                        true));
        tools.add(
                tool(
                        "get_interaction",
                        "Server-computed ray target and open container. Client-only GUI is unavailable.",
                        true));
        tools.add(
                tool(
                        "list_entities",
                        "Loaded entities within radius of player; bounded page sorted by entity ID.",
                        true,
                        "radius",
                        "offset",
                        "limit"));
        tools.add(
                tool(
                        "inspect_entity",
                        "Detailed loaded entity state and NBT by ID.",
                        true,
                        "entityId"));
        tools.add(
                tool(
                        "inspect_block",
                        "Block, metadata, light, biome and tile NBT including closed containers; no chunk loading.",
                        true,
                        "x",
                        "y",
                        "z"));
        tools.add(
                tool(
                        "read_terrain",
                        "Read terrain around player across chunk boundaries: compact non-air block runs, tile/container locations and entities. Defaults: player's chunk, chunkRadius 1 (3x3 chunks), player Y +/-8. Optional minY/maxY 0-255 for taller/full-height scans. Follow nextOffset for remaining layers, reusing centerChunk and requestedBounds heights. Unloaded chunks are explicit, never loaded. Use inspect_block at tile coordinates for contents; inspect_entity by entityId for details. Follow tiles.nextOffset using tileOffset and entities.nextOffset using entityOffset, independently of terrain offset.",
                        true,
                        "chunkX",
                        "chunkZ",
                        "chunkRadius",
                        "minY",
                        "maxY",
                        "offset",
                        "entityOffset",
                        "tileOffset"));
        tools.add(
                tool(
                        "scan_area",
                        "Page of blocks in an inclusive cuboid, including air and explicit unloaded cells. Max volume 32768.",
                        true,
                        "minX",
                        "minY",
                        "minZ",
                        "maxX",
                        "maxY",
                        "maxZ",
                        "offset",
                        "limit"));
        tools.add(
                tool(
                        "get_changes",
                        "Compare sampled player, inventory, entities and nearby blocks. Omit cursor for baseline. Fixed radius <=4.",
                        true,
                        "radius",
                        "cursor"));
        tools.add(
                tool(
                        "get_registry",
                        "Paginated item or block registry; stable registry names, properties and translation keys.",
                        true,
                        "kind",
                        "offset",
                        "limit"));
        tools.add(
                tool(
                        "highlight_entity",
                        "Temporarily surround a loaded mob or dropped item with particles visible only to player. UUID guards against recycled IDs. durationSeconds 1-120 (10).",
                        true,
                        "entityId",
                        "entityUuid",
                        "durationSeconds"));
        tools.add(
                tool(
                        "highlight_block",
                        "Temporarily mark a loaded block with particles for player. durationSeconds 1-120 (10).",
                        true,
                        "x",
                        "y",
                        "z",
                        "durationSeconds"));
        tools.add(
                tool(
                        "set_waypoint",
                        "Show a temporary destination and direction particles to player. Visual guidance only, no automatic movement or pathfinding. durationSeconds 1-120 (10).",
                        true,
                        "x",
                        "y",
                        "z",
                        "durationSeconds"));
        tools.add(
                tool(
                        "list_markers",
                        "List player's active markers in this world session, with remaining lifetime and coordinates.",
                        true));
        tools.add(
                tool(
                        "remove_marker",
                        "Remove one marker owned by player in this world session.",
                        true,
                        "markerId"));
        tools.add(
                tool(
                        "clear_markers",
                        "Remove all markers owned by player in this world session.",
                        true));
        return tools;
    }

    private static Map<String, Object> tool(
            String name, String description, boolean scoped, String... extra) {
        Map<String, Object> props = new LinkedHashMap<String, Object>();
        List<String> required = new ArrayList<String>();
        if (scoped) {
            for (String s : Arrays.asList("session", "player")) {
                props.put(s, map("type", "string"));
                required.add(s);
            }
            props.put("dimension", map("type", "integer"));
            required.add("dimension");
        }
        for (String key : extra) {
            boolean string =
                    Arrays.asList("cursor", "kind", "entityUuid", "markerId").contains(key);
            props.put(key, map("type", string ? "string" : "integer"));
            if (Arrays.asList(
                            "entityId",
                            "entityUuid",
                            "markerId",
                            "x",
                            "y",
                            "z",
                            "minX",
                            "minY",
                            "minZ",
                            "maxX",
                            "maxY",
                            "maxZ",
                            "kind")
                    .contains(key)) required.add(key);
        }
        if (props.containsKey("durationSeconds"))
            props.put(
                    "durationSeconds",
                    map("type", "integer", "minimum", 1, "maximum", 120, "default", 10));
        if (name.equals("read_terrain")) {
            required.removeAll(Arrays.asList("minY", "maxY"));
            props.put(
                    "chunkX",
                    map(
                            "type",
                            "integer",
                            "minimum",
                            TerrainPage.MIN_CHUNK,
                            "maximum",
                            TerrainPage.MAX_CHUNK));
            props.put(
                    "chunkZ",
                    map(
                            "type",
                            "integer",
                            "minimum",
                            TerrainPage.MIN_CHUNK,
                            "maximum",
                            TerrainPage.MAX_CHUNK));
            props.put(
                    "chunkRadius",
                    map("type", "integer", "minimum", 0, "maximum", 2, "default", 1));
            props.put("minY", map("type", "integer", "minimum", 0, "maximum", 255));
            props.put("maxY", map("type", "integer", "minimum", 0, "maximum", 255));
            props.put("offset", map("type", "integer", "minimum", 0, "maximum", 255, "default", 0));
            props.put("entityOffset", map("type", "integer", "minimum", 0, "default", 0));
            props.put("tileOffset", map("type", "integer", "minimum", 0, "default", 0));
        }
        return map(
                "name",
                name,
                "description",
                description,
                "inputSchema",
                map(
                        "type",
                        "object",
                        "properties",
                        props,
                        "required",
                        required,
                        "additionalProperties",
                        false),
                "annotations",
                map(
                        "readOnlyHint",
                        !Arrays.asList(
                                        "highlight_entity",
                                        "highlight_block",
                                        "set_waypoint",
                                        "remove_marker",
                                        "clear_markers")
                                .contains(name),
                        "destructiveHint",
                        false,
                        "openWorldHint",
                        false));
    }

    @SuppressWarnings("unchecked")
    public static void validate(String name, Map<String, Object> args) {
        Map<String, Object> spec = null;
        for (Map<String, Object> t : list()) if (name.equals(t.get("name"))) spec = t;
        if (spec == null) throw new IllegalArgumentException("Unknown tool: " + name);
        Map<String, Object> schema = (Map<String, Object>) spec.get("inputSchema");
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        for (String k : (List<String>) schema.get("required"))
            if (!args.containsKey(k)) throw new IllegalArgumentException("Missing: " + k);
        for (String k : args.keySet()) {
            if (!properties.containsKey(k))
                throw new IllegalArgumentException("Unknown argument: " + k);
            String type = (String) ((Map<String, Object>) properties.get(k)).get("type");
            if (type.equals("string")) string(args, k);
            else {
                if (args.get(k) == null) throw new IllegalArgumentException("Null argument: " + k);
                integer(args, k, 0, Integer.MIN_VALUE, Integer.MAX_VALUE);
            }
        }
        if (args.containsKey("durationSeconds")) integer(args, "durationSeconds", 10, 1, 120);
        if (args.containsKey("entityUuid")) UUID.fromString(string(args, "entityUuid"));
        if (name.equals("read_terrain")) {
            if (args.containsKey("chunkX"))
                integer(args, "chunkX", 0, TerrainPage.MIN_CHUNK, TerrainPage.MAX_CHUNK);
            if (args.containsKey("chunkZ"))
                integer(args, "chunkZ", 0, TerrainPage.MIN_CHUNK, TerrainPage.MAX_CHUNK);
            if (args.containsKey("chunkRadius")) integer(args, "chunkRadius", 1, 0, 2);
            for (String key : Arrays.asList("minY", "maxY", "offset"))
                if (args.containsKey(key)) integer(args, key, 0, 0, 255);
            if (args.containsKey("entityOffset"))
                integer(args, "entityOffset", 0, 0, Integer.MAX_VALUE);
            if (args.containsKey("tileOffset"))
                integer(args, "tileOffset", 0, 0, Integer.MAX_VALUE);
        }
    }
}
