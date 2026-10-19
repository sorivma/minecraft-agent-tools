package dev.minecraftagenttools.gregtech.contract;

import static dev.minecraftagenttools.protocol.data.Data.*;

import java.util.*;

/** Shared Java 8 contract; no Minecraft, GregTech or JSON dependency. */
public final class GtTools {
    private GtTools() {}

    public static List<Map<String, Object>> list() {
        List<Map<String, Object>> tools = new ArrayList<Map<String, Object>>();
        tools.add(
                tool(
                        "gt_status",
                        "Discover installed GregTech version, server world sessions and actual capabilities."));
        tools.add(
                tool(
                        "gt_list_recipe_maps",
                        "List registered GregTech recipe maps, including compatible addon maps.",
                        "query?",
                        "offset?",
                        "limit?"));
        tools.add(
                tool(
                        "gt_search_recipes",
                        "Search a map by exact item or fluid registry name (optional meta), direction input/output/either. Reuse catalogId for paging. Includes fuel maps and fake/hidden flags.",
                        "map",
                        "catalogId?",
                        "item?",
                        "meta?",
                        "fluid?",
                        "direction?",
                        "offset?",
                        "limit?"));
        tools.add(
                tool(
                        "gt_get_recipe",
                        "Read source GTRecipe: item/fluid inputs and outputs, chances, base EU/t, duration, special slot/value and metadata. Does not calculate machine overclocks.",
                        "catalogId",
                        "recipeIndex"));
        tools.add(
                tool(
                        "gt_list_machines",
                        "Search registered meta-tile machine definitions by literal name/class substring.",
                        "query?",
                        "offset?",
                        "limit?"));
        tools.add(
                tool(
                        "gt_get_machine",
                        "Read a registered machine's description, capabilities, recipe map and public scalar definition fields.",
                        "machineId"));
        tools.add(
                tool(
                        "gt_get_structure",
                        "Read available StructureLib pieces and paginated local cell requirements. No checks, placement or hints. Contextual offsets/repetitions are not inferred.",
                        "machineId",
                        "piece?",
                        "offset?",
                        "limit?"));
        tools.add(
                tool(
                        "gt_inspect_machine",
                        "Read a loaded GT machine: energy/steam buffers, voltage/amperage, average I/O, inventories/tanks, progress, cached multiblock state, hatches and public state. Never forces a structure check.",
                        "x",
                        "y",
                        "z"));
        tools.add(
                tool(
                        "gt_list_materials",
                        "Search registered GregTech materials by literal name/formula substring.",
                        "query?",
                        "offset?",
                        "limit?"));
        tools.add(
                tool(
                        "gt_get_material",
                        "Read material properties, fuel fields, composition, byproducts, processing targets and registered fluid forms.",
                        "material"));
        tools.add(
                tool(
                        "gt_list_ore_prefixes",
                        "List ore-prefix definitions, material amounts and generation properties.",
                        "query?",
                        "offset?",
                        "limit?"));
        tools.add(
                tool(
                        "gt_get_energy_tiers",
                        "Read GregTech voltage tiers, names and recipe voltage thresholds. Values retain EU/p units."));
        return tools;
    }

    private static Map<String, Object> tool(String name, String description, String... fields) {
        Map<String, Object> props = new LinkedHashMap<String, Object>();
        List<String> required = new ArrayList<String>();
        if (!name.equals("gt_status")) {
            props.put("session", text(128));
            props.put("dimension", map("type", "integer"));
            required.add("session");
            required.add("dimension");
        }
        for (String field : fields) {
            boolean optional = field.endsWith("?");
            String key = optional ? field.substring(0, field.length() - 1) : field;
            Object prop;
            switch (key) {
                case "offset":
                case "recipeIndex":
                    prop = number(0, Integer.MAX_VALUE);
                    break;
                case "machineId":
                case "meta":
                    prop = number(0, 32767);
                    break;
                case "limit":
                    prop = number(1, 64);
                    break;
                case "x":
                case "z":
                    prop = number(-29999984, 29999983);
                    break;
                case "y":
                    prop = number(0, 255);
                    break;
                case "direction":
                    prop =
                            map(
                                    "type",
                                    "string",
                                    "enum",
                                    Arrays.asList("input", "output", "either"));
                    break;
                default:
                    prop = text(512);
            }
            props.put(key, prop);
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

    private static Map<String, Object> text(int max) {
        return map("type", "string", "minLength", 1, "maxLength", max);
    }

    private static Map<String, Object> number(int min, int max) {
        return map("type", "integer", "minimum", min, "maximum", max);
    }

    @SuppressWarnings("unchecked")
    public static void validate(String name, Map<String, Object> args) {
        Map<String, Object> schema = null;
        for (Map<String, Object> spec : list())
            if (name.equals(spec.get("name")))
                schema = (Map<String, Object>) spec.get("inputSchema");
        if (schema == null || args == null)
            throw new IllegalArgumentException("Unknown GregTech tool or missing arguments");
        Map<String, Map<String, Object>> props =
                (Map<String, Map<String, Object>>) (Object) schema.get("properties");
        for (String key : (List<String>) schema.get("required"))
            if (!args.containsKey(key)) throw new IllegalArgumentException("Missing: " + key);
        for (String key : args.keySet()) {
            Map<String, Object> prop = props.get(key);
            if (prop == null || args.get(key) == null)
                throw new IllegalArgumentException("Unknown/null argument: " + key);
            if (prop.get("type").equals("string")) {
                String value = string(args, key);
                if (value.length() > ((Number) prop.getOrDefault("maxLength", 512)).intValue())
                    throw new IllegalArgumentException("String too long: " + key);
                if (prop.containsKey("enum") && !((List<?>) prop.get("enum")).contains(value))
                    throw new IllegalArgumentException("Invalid: " + key);
            } else
                integer(
                        args,
                        key,
                        0,
                        ((Number) prop.getOrDefault("minimum", Integer.MIN_VALUE)).intValue(),
                        ((Number) prop.getOrDefault("maximum", Integer.MAX_VALUE)).intValue());
        }
        if (args.containsKey("meta") && !args.containsKey("item"))
            throw new IllegalArgumentException("meta requires item");
    }
}
