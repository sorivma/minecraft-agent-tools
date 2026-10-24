package dev.minecraftagenttools.schematica.contract;

import static dev.minecraftagenttools.protocol.data.Data.*;

import java.util.*;

/** Java 8 wire contract shared by the provider and the client adapter. */
public final class SchematicaTools {
    private SchematicaTools() {}

    private static Map<String, Object> integerSpec(int min, int max) {
        return map("type", "integer", "minimum", min, "maximum", max);
    }

    private static Map<String, Object> textSpec(int max) {
        return map("type", "string", "minLength", 1, "maxLength", max);
    }

    private static Map<String, Object> object(Map<String, Object> props, String... required) {
        return map(
                "type",
                "object",
                "properties",
                props,
                "required",
                Arrays.asList(required),
                "additionalProperties",
                false);
    }

    public static List<Map<String, Object>> list() {
        List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
        Map<String, Object> palette =
                object(
                        map(
                                "block",
                                textSpec(256),
                                "meta",
                                integerSpec(0, Layout.MAX_META),
                                "machineId",
                                integerSpec(1, 32767),
                                "facing",
                                integerSpec(0, 5),
                                "connections",
                                integerSpec(0, 63),
                                "tileNbt",
                                textSpec(8192),
                                "label",
                                textSpec(128)),
                        "block");
        Map<String, Object> box =
                object(
                        map(
                                "x1",
                                integerSpec(0, 127),
                                "y1",
                                integerSpec(0, 127),
                                "z1",
                                integerSpec(0, 127),
                                "x2",
                                integerSpec(0, 127),
                                "y2",
                                integerSpec(0, 127),
                                "z2",
                                integerSpec(0, 127),
                                "palette",
                                integerSpec(0, 127)),
                        "x1",
                        "y1",
                        "z1",
                        "x2",
                        "y2",
                        "z2",
                        "palette");
        for (String name :
                Arrays.asList("status", "list", "get", "put", "display", "remove", "export")) {
            Map<String, Object> props = new LinkedHashMap<String, Object>();
            List<String> required = new ArrayList<String>();
            if (!name.equals("status")) {
                props.put("session", textSpec(128));
                props.put("dimension", integerSpec(Integer.MIN_VALUE, Integer.MAX_VALUE));
                required.addAll(Arrays.asList("session", "dimension"));
            }
            if (!name.equals("status") && !name.equals("list")) {
                props.put("planId", textSpec(128));
                if (!name.equals("put")) required.add("planId");
            }
            if (name.equals("put") || name.equals("display") || name.equals("remove")) {
                props.put("revision", integerSpec(1, Integer.MAX_VALUE - 1));
                if (!name.equals("put")) required.add("revision");
            }
            if (name.equals("put") || name.equals("display")) {
                props.put("x", integerSpec(-29999872, 29999872));
                props.put("y", integerSpec(0, 255));
                props.put("z", integerSpec(-29999872, 29999872));
            }
            if (name.equals("put")) {
                props.put("name", textSpec(128));
                props.put("group", map("type", "string", "minLength", 0, "maxLength", 128));
                props.put("width", integerSpec(1, 128));
                props.put("height", integerSpec(1, 128));
                props.put("length", integerSpec(1, 128));
                props.put(
                        "palette",
                        map("type", "array", "items", palette, "minItems", 1, "maxItems", 128));
                props.put("boxes", map("type", "array", "items", box, "maxItems", 256));
                required.addAll(
                        Arrays.asList(
                                "name", "x", "y", "z", "width", "height", "length", "palette",
                                "boxes"));
            }
            if (name.equals("display")) {
                props.put("visible", map("type", "boolean"));
                props.put("layer", integerSpec(-1, 127));
            }
            String description;
            switch (name) {
                case "status":
                    description =
                            "Discover client scope, persistent world key, saved-plan count, loading/errors, Schematica readiness and authoring limits. Include world in the chat widget JSON to enable explicit restoration after rejoining.";
                    break;
                case "list":
                    description =
                            "List plans, nested hierarchy and required material counts in this client world. Groups are slash-separated paths assigned by schematic_put.";
                    break;
                case "put":
                    description =
                            "Create or replace a ghost construction plan at world x/y/z. Optional group is a slash-separated hierarchy path (up to four levels, 128 characters); omitted or empty puts the plan at root. Group related plans, e.g. Factory/Power, when creating many. Optional planId requires its revision. Palette uses registry block names, meta 0..65535 (extended GTNH block metadata, e.g. gregtech:gt.blockframes meta 300); GregTech machines, pipes and cables use block gregtech:gt.blockmachines, machineId from gt_list_machines (no meta), optional facing 0 down/1 up/2 north/3 south/4 west/5 east. GT pipes accept connections bitmask 0..63 (bits down,up,north,south,west,east); omitted means connect to adjacent GT tiles, a visual layout assumption, not a functional check. Other tile entity blocks/pipes require tileNbt, an SNBT compound up to 8192 characters with mod-specific type/render data; obtain real data rather than guessing. Coordinates are replaced with local schematic coordinates. Tiles do not tick. Inclusive local boxes reference zero-based palette entries; later boxes overwrite earlier ones, unspecified cells are air. At most 65536 cells/plan, 512 tiles/plan, 32 plans and 131072 cells total. No real blocks are placed. Use wool + label only for explicitly conceptual placeholders; explain these to the user.";
                    break;
                case "display":
                    description =
                            "Set ghost visibility, local layer (-1 all), or absolute origin (x/y/z together). Does not alter real blocks or enable the printer.";
                    break;
                case "get":
                    description =
                            "Read the full authoring layout so it can be revised without losing previous boxes.";
                    break;
                case "remove":
                    description = "Remove an agent ghost plan; no real blocks are removed.";
                    break;
                default:
                    description =
                            "Export an agent plan as .schematic and authoring .json files in the MCP working directory's schematics folder. Does not overwrite files. Files use registry mappings and typed GregTech tile data; display in-game is independent of export. Alpha export rejects metadata above 15 to avoid truncation; use schematic_get for lossless layout JSON in that case.";
            }
            boolean read = name.equals("status") || name.equals("list") || name.equals("get");
            out.add(
                    map(
                            "name",
                            "schematic_" + name,
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
                                    read,
                                    "destructiveHint",
                                    false,
                                    "openWorldHint",
                                    false)));
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    public static void validate(String tool, Map<String, Object> args) {
        Map<String, Object> schema = null;
        for (Map<String, Object> t : list())
            if (t.get("name").equals(tool)) schema = (Map<String, Object>) t.get("inputSchema");
        if (schema == null || args == null)
            throw new IllegalArgumentException("Unknown Schematica tool or missing arguments");
        Map<String, Object> props = (Map<String, Object>) schema.get("properties");
        for (String key : (List<String>) schema.get("required"))
            if (!args.containsKey(key) || args.get(key) == null)
                throw new IllegalArgumentException("Missing: " + key);
        for (String key : args.keySet()) {
            Map<String, Object> p = (Map<String, Object>) props.get(key);
            if (p == null) throw new IllegalArgumentException("Unknown argument: " + key);
            String type = (String) p.get("type");
            if (key.equals("group")) {
                if (!(args.get(key) instanceof String))
                    throw new IllegalArgumentException("Expected group string");
                PlanPresentation.group((String) args.get(key));
            } else if (type.equals("string"))
                Layout.text(args, key, ((Number) p.get("maxLength")).intValue());
            if (type.equals("integer"))
                Layout.requiredInt(
                        args,
                        key,
                        ((Number) p.get("minimum")).intValue(),
                        ((Number) p.get("maximum")).intValue());
            if (type.equals("boolean") && !(args.get(key) instanceof Boolean))
                throw new IllegalArgumentException("Expected boolean: " + key);
        }
        if (tool.equals("schematic_put")) {
            if (args.containsKey("group")) PlanPresentation.group((String) args.get("group"));
            if (args.containsKey("planId") != args.containsKey("revision"))
                throw new IllegalArgumentException("planId and revision must be supplied together");
            Layout layout = new Layout(args);
            if (integer(args, "y", 0, 0, 255) + layout.height > 256)
                throw new IllegalArgumentException("Plan exceeds world height");
        }
        if (tool.equals("schematic_display")) {
            int coords =
                    (args.containsKey("x") ? 1 : 0)
                            + (args.containsKey("y") ? 1 : 0)
                            + (args.containsKey("z") ? 1 : 0);
            if (coords != 0 && coords != 3)
                throw new IllegalArgumentException("Supply x/y/z together");
        }
    }
}
