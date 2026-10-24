package dev.minecraftagenttools.betterquesting.contract;

import static dev.minecraftagenttools.protocol.data.Data.*;

import java.util.*;

/** Java 8 wire contract, independent of Minecraft and BetterQuesting. */
public final class BqTools {
    private BqTools() {}

    public static List<Map<String, Object>> list() {
        return Arrays.asList(
                tool(
                        "bq_status",
                        "Discover BetterQuesting client scope, database availability and progress limitations."),
                tool(
                        "bq_list_lines",
                        "Browse ordered quest chapters. Optional literal name/description query.",
                        "query?",
                        "offset?",
                        "limit?"),
                tool(
                        "bq_get_line",
                        "Read chapter description and paged quest summaries with book coordinates.",
                        "lineId",
                        "offset?",
                        "limit?",
                        "textOffset?"),
                tool(
                        "bq_search_quests",
                        "Search literal text in quest names, descriptions, task/reward types and item names/registry IDs. Omit query to browse. Scans up to 128 quests per call; follow nextOffset even on empty pages.",
                        "query?",
                        "lineId?",
                        "state?",
                        "visibility?",
                        "offset?",
                        "limit?"),
                tool(
                        "bq_get_quest",
                        "Read quest description, BQ state, completion, logic, repeat settings, chapter memberships and paged task/reward summaries. All native properties are also available under nbt; use path to inspect nested config/progress.",
                        "questId",
                        "offset?",
                        "limit?",
                        "textOffset?",
                        "path?"),
                tool(
                        "bq_get_task",
                        "Read a task's configuration and current-player progress. Native typed NBT data supports path (JSON Pointer) and pagination; follow omittedPaths for full nested data.",
                        "questId",
                        "taskId",
                        "path?",
                        "offset?",
                        "limit?"),
                tool(
                        "bq_get_reward",
                        "Read reward configuration, including choices, items and commands as data. Native NBT supports path (JSON Pointer) and pagination. Does not claim rewards.",
                        "questId",
                        "rewardId",
                        "path?",
                        "offset?",
                        "limit?"),
                tool(
                        "bq_get_relations",
                        "Navigate direct prerequisite or dependent quests with their state and requirement type. Repeat for transitive navigation; honor questLogic.",
                        "questId",
                        "direction",
                        "offset?",
                        "limit?"));
    }

    private static Map<String, Object> tool(String name, String description, String... keys) {
        Map<String, Object> props = new LinkedHashMap<String, Object>();
        List<String> required = new ArrayList<String>();
        if (!name.equals("bq_status")) {
            props.put("session", map("type", "string", "minLength", 1, "maxLength", 128));
            required.add("session");
            props.put(
                    "dimension",
                    map(
                            "type",
                            "integer",
                            "minimum",
                            Integer.MIN_VALUE,
                            "maximum",
                            Integer.MAX_VALUE));
            required.add("dimension");
            props.put(
                    "revision", map("type", "integer", "minimum", 0, "maximum", Integer.MAX_VALUE));
        }
        for (String arg : keys) {
            boolean optional = arg.endsWith("?");
            String key = optional ? arg.substring(0, arg.length() - 1) : arg;
            Map<String, Object> spec;
            if (Arrays.asList("offset", "textOffset", "taskId", "rewardId", "limit").contains(key))
                spec =
                        map(
                                "type",
                                "integer",
                                "minimum",
                                key.equals("limit") ? 1 : 0,
                                "maximum",
                                key.equals("limit") ? 64 : Integer.MAX_VALUE);
            else
                spec =
                        map(
                                "type",
                                "string",
                                "minLength",
                                1,
                                "maxLength",
                                key.equals("path") ? 2048 : 256);
            if (key.equals("questId") || key.equals("lineId")) spec.put("format", "uuid");
            if (key.equals("state"))
                spec.put(
                        "enum",
                        Arrays.asList(
                                "all",
                                "locked",
                                "unlocked",
                                "unclaimed",
                                "completed",
                                "repeatable",
                                "complete",
                                "incomplete"));
            if (key.equals("visibility"))
                spec.put("enum", Arrays.asList("all", "visible", "hidden"));
            if (key.equals("direction"))
                spec.put("enum", Arrays.asList("prerequisites", "dependents"));
            props.put(key, spec);
            if (!optional) required.add(key);
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
                map("readOnlyHint", true, "destructiveHint", false, "openWorldHint", false));
    }

    @SuppressWarnings("unchecked")
    public static void validate(String tool, Map<String, Object> args) {
        Map<String, Object> schema = null;
        for (Map<String, Object> spec : list())
            if (spec.get("name").equals(tool))
                schema = (Map<String, Object>) spec.get("inputSchema");
        if (schema == null)
            throw new IllegalArgumentException("Unknown BetterQuesting tool: " + tool);
        if (args == null) throw new IllegalArgumentException("Missing arguments");
        for (String key : (List<String>) schema.get("required"))
            if (!args.containsKey(key)) throw new IllegalArgumentException("Missing: " + key);
        Map<String, Object> props = (Map<String, Object>) schema.get("properties");
        for (String key : args.keySet()) {
            Map<String, Object> prop = (Map<String, Object>) props.get(key);
            if (prop == null) throw new IllegalArgumentException("Unknown argument: " + key);
            if (args.get(key) == null) throw new IllegalArgumentException("Null argument: " + key);
            if (prop.get("type").equals("integer"))
                integer(
                        args,
                        key,
                        0,
                        ((Number) prop.get("minimum")).intValue(),
                        ((Number) prop.get("maximum")).intValue());
            else {
                String value = string(args, key);
                if (value.length() > ((Number) prop.get("maxLength")).intValue())
                    throw new IllegalArgumentException("String too long: " + key);
                if (prop.containsKey("enum") && !((List<?>) prop.get("enum")).contains(value))
                    throw new IllegalArgumentException("Invalid " + key);
                if ("uuid".equals(prop.get("format"))
                        && !UUID.fromString(value).toString().equalsIgnoreCase(value))
                    throw new IllegalArgumentException("Invalid UUID: " + key);
                if (key.equals("path")
                        && (!value.startsWith("/") || value.matches(".*~(?![01]).*")))
                    throw new IllegalArgumentException("Expected JSON Pointer path");
            }
        }
    }
}
