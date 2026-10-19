package dev.minecraftagenttools.nei.contract;

import static dev.minecraftagenttools.protocol.data.Data.*;

import java.util.*;

/** Java 8 wire contract shared with the client adapter; no Minecraft or MCP SDK. */
public final class NeiTools {
    private NeiTools() {}

    public static List<Map<String, Object>> list() {
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        result.add(
                tool(
                        "nei_status",
                        "Discover client world session and NEI catalog readiness.",
                        false));
        result.add(
                tool(
                        "nei_list_handlers",
                        "List installed NEI recipe handlers, including serial handlers. Follow nextOffset.",
                        true,
                        "offset",
                        "limit"));
        result.add(
                tool(
                        "nei_search_items",
                        "Search NEI catalog by literal registry/display-name substring. Scans at most 512 entries per page; follow nextOffset even for empty pages. Returns opaque itemId handles.",
                        true,
                        "query",
                        "offset",
                        "limit"));
        result.add(
                tool(
                        "nei_find_recipes",
                        "Query one discovered handler for recipes or usages of an itemId. Returns queryId and recipeCount; use nei_get_recipe for indices. No GUI changes or crafting.",
                        true,
                        "handler",
                        "itemId",
                        "mode"));
        result.add(
                tool(
                        "nei_get_recipe",
                        "Read one recipe from queryId: ingredients with alternatives, primary result and otherStacks of unspecified role. GUI-only machine properties are unavailable. Truncation is explicit.",
                        true,
                        "queryId",
                        "recipeIndex"));
        return result;
    }

    private static Map<String, Object> tool(
            String name, String description, boolean scoped, String... keys) {
        Map<String, Object> props = new LinkedHashMap<String, Object>();
        List<String> required = new ArrayList<String>();
        if (scoped) {
            props.put("session", map("type", "string", "maxLength", 128));
            required.add("session");
            props.put("dimension", map("type", "integer"));
            required.add("dimension");
        }
        for (String key : keys) {
            if (key.equals("offset") || key.equals("recipeIndex"))
                props.put(key, map("type", "integer", "minimum", 0));
            else if (key.equals("limit"))
                props.put(key, map("type", "integer", "minimum", 1, "maximum", 64, "default", 32));
            else if (key.equals("mode"))
                props.put(key, map("type", "string", "enum", Arrays.asList("recipes", "usages")));
            else
                props.put(
                        key,
                        map(
                                "type",
                                "string",
                                "minLength",
                                1,
                                "maxLength",
                                key.equals("query") ? 256 : 512));
            if (!key.equals("offset") && !key.equals("limit")) required.add(key);
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
        if (schema == null) throw new IllegalArgumentException("Unknown NEI tool: " + tool);
        if (args == null) throw new IllegalArgumentException("Missing arguments");
        for (String key : (List<String>) schema.get("required"))
            if (!args.containsKey(key)) throw new IllegalArgumentException("Missing: " + key);
        Map<String, Object> props = (Map<String, Object>) schema.get("properties");
        for (String key : args.keySet()) {
            Map<String, Object> prop = (Map<String, Object>) props.get(key);
            if (prop == null) throw new IllegalArgumentException("Unknown argument: " + key);
            if ("string".equals(prop.get("type"))) {
                String value = string(args, key);
                int max = ((Number) prop.getOrDefault("maxLength", 512)).intValue();
                if (value.length() > max)
                    throw new IllegalArgumentException("String too long: " + key);
                if (prop.containsKey("enum") && !((List<?>) prop.get("enum")).contains(value))
                    throw new IllegalArgumentException("Invalid " + key);
            } else {
                if (args.get(key) == null)
                    throw new IllegalArgumentException("Null argument: " + key);
                integer(
                        args,
                        key,
                        0,
                        key.equals("dimension") ? Integer.MIN_VALUE : key.equals("limit") ? 1 : 0,
                        key.equals("limit") ? 64 : Integer.MAX_VALUE);
            }
        }
    }
}
