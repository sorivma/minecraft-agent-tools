package dev.minecraftagenttools.schematica.contract;

import static dev.minecraftagenttools.protocol.data.Data.*;

import java.util.*;

/** Bounded detached authoring format. Boxes are inclusive, later boxes overwrite earlier ones. */
public final class Layout {
    public static final int MAX_VOLUME = 65536;
    public final int width, height, length;
    public final List<Map<String, Object>> palette, boxes;

    public Layout(Map<String, Object> args) {
        width = requiredInt(args, "width", 1, 128);
        height = requiredInt(args, "height", 1, 128);
        length = requiredInt(args, "length", 1, 128);
        if ((long) width * height * length > MAX_VOLUME)
            throw new IllegalArgumentException("Plan volume exceeds " + MAX_VOLUME);
        palette = objects(args, "palette", 1, 128);
        for (Map<String, Object> p : palette) {
            keys(p, "block", "meta", "machineId", "facing", "connections", "tileNbt", "label");
            String block = text(p, "block", 256);
            if (!block.matches("[a-zA-Z0-9_.-]+:[a-zA-Z0-9_./-]+"))
                throw new IllegalArgumentException("Use a registry block name");
            if (p.containsKey("meta")) requiredInt(p, "meta", 0, 15);
            if (p.containsKey("label")) text(p, "label", 128);
            if (p.containsKey("tileNbt")) text(p, "tileNbt", 8192);
            if (p.containsKey("connections")) {
                if (!p.containsKey("machineId"))
                    throw new IllegalArgumentException(
                            "connections requires a GregTech pipe machineId");
                requiredInt(p, "connections", 0, 63);
            }
            if (p.containsKey("machineId")) {
                if (!block.equals("gregtech:gt.blockmachines") || p.containsKey("meta"))
                    throw new IllegalArgumentException(
                            "machineId requires gregtech:gt.blockmachines without meta");
                requiredInt(p, "machineId", 1, 32767);
                if (p.containsKey("facing")) requiredInt(p, "facing", 0, 5);
            } else if (p.containsKey("facing") || block.equals("gregtech:gt.blockmachines"))
                throw new IllegalArgumentException(
                        "GregTech machines require machineId; facing is machine-only");
        }
        boxes = objects(args, "boxes", 0, 256);
        long writes = 0;
        for (Map<String, Object> b : boxes) {
            keys(b, "x1", "y1", "z1", "x2", "y2", "z2", "palette");
            int x1 = requiredInt(b, "x1", 0, width - 1), x2 = requiredInt(b, "x2", x1, width - 1);
            int y1 = requiredInt(b, "y1", 0, height - 1), y2 = requiredInt(b, "y2", y1, height - 1);
            int z1 = requiredInt(b, "z1", 0, length - 1), z2 = requiredInt(b, "z2", z1, length - 1);
            requiredInt(b, "palette", 0, palette.size() - 1);
            writes += (long) (x2 - x1 + 1) * (y2 - y1 + 1) * (z2 - z1 + 1);
            if (writes > MAX_VOLUME * 4L) throw new IllegalArgumentException("Too many box writes");
        }
    }

    public int volume() {
        return width * height * length;
    }

    /** -1 means unspecified air, other values index the palette. */
    public int[] cells() {
        int[] result = new int[volume()];
        Arrays.fill(result, -1);
        for (Map<String, Object> b : boxes) {
            int p = ((Number) b.get("palette")).intValue();
            for (int y = ((Number) b.get("y1")).intValue();
                    y <= ((Number) b.get("y2")).intValue();
                    y++)
                for (int z = ((Number) b.get("z1")).intValue();
                        z <= ((Number) b.get("z2")).intValue();
                        z++)
                    for (int x = ((Number) b.get("x1")).intValue();
                            x <= ((Number) b.get("x2")).intValue();
                            x++) result[x + (y * length + z) * width] = p;
        }
        return result;
    }

    public Map<String, Object> data() {
        return map(
                "width", width, "height", height, "length", length, "palette", palette, "boxes",
                boxes);
    }

    public static int requiredInt(Map<String, Object> args, String key, int min, int max) {
        if (!args.containsKey(key) || args.get(key) == null)
            throw new IllegalArgumentException("Missing integer: " + key);
        return integer(args, key, 0, min, max);
    }

    public static String text(Map<String, Object> args, String key, int max) {
        String s = string(args, key);
        if (s.length() > max) throw new IllegalArgumentException("Text too long: " + key);
        return s;
    }

    public static void keys(Map<String, Object> args, String... allowed) {
        Set<String> names = new HashSet<String>(Arrays.asList(allowed));
        for (String key : args.keySet())
            if (!names.contains(key))
                throw new IllegalArgumentException("Unknown argument: " + key);
    }

    private static List<Map<String, Object>> objects(
            Map<String, Object> args, String key, int min, int max) {
        Object v = args.get(key);
        if (!(v instanceof List) || ((List<?>) v).size() < min || ((List<?>) v).size() > max)
            throw new IllegalArgumentException("Invalid list: " + key);
        List<Map<String, Object>> copy = new ArrayList<Map<String, Object>>();
        for (Object entry : (List<?>) v) {
            if (!(entry instanceof Map))
                throw new IllegalArgumentException("Expected object in " + key);
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            for (Map.Entry<?, ?> field : ((Map<?, ?>) entry).entrySet()) {
                if (!(field.getKey() instanceof String))
                    throw new IllegalArgumentException("Invalid key");
                row.put((String) field.getKey(), field.getValue());
            }
            copy.add(Collections.unmodifiableMap(row));
        }
        return Collections.unmodifiableList(copy);
    }
}
