package dev.minecraftagenttools.schematica.contract;

import static dev.minecraftagenttools.protocol.data.Data.*;

import java.util.*;

/** Detached hierarchy and bill of materials; no game or UI dependencies. */
public final class PlanPresentation {
    private PlanPresentation() {}

    public static String group(String value) {
        if (value == null || value.length() > 128)
            throw new IllegalArgumentException("Invalid group");
        if (value.isEmpty()) return value;
        String[] parts = value.split("/", -1);
        if (parts.length > 4) throw new IllegalArgumentException("At most four group levels");
        for (String part : parts)
            if (part.trim().isEmpty()
                    || !part.equals(part.trim())
                    || part.equals(".")
                    || part.equals("..")
                    || part.matches(".*[\\p{Cntrl}\\u00a7].*"))
                throw new IllegalArgumentException("Invalid group segment");
        return value;
    }

    public static List<Map<String, Object>> materials(Layout layout) {
        int[] counts = new int[layout.palette.size()];
        for (int cell : layout.cells()) if (cell >= 0) counts[cell]++;
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        for (int i = 0; i < counts.length; i++) {
            Map<String, Object> p = layout.palette.get(i);
            if (counts[i] == 0 || "minecraft:air".equals(p.get("block"))) continue;
            Map<String, Object> row = new LinkedHashMap<String, Object>(p);
            row.remove("facing");
            row.remove("connections");
            if (!row.containsKey("machineId"))
                row.put("meta", integer(p, "meta", 0, 0, Layout.MAX_META));
            row.put("count", counts[i]);
            rows.add(row);
        }
        return merge(rows);
    }

    public static List<Map<String, Object>> merge(Collection<Map<String, Object>> rows) {
        Map<Map<String, Object>, Map<String, Object>> result =
                new LinkedHashMap<Map<String, Object>, Map<String, Object>>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> key = new LinkedHashMap<String, Object>(row);
            key.remove("count");
            key.remove("label");
            Map<String, Object> old = result.get(key);
            if (old == null) result.put(key, new LinkedHashMap<String, Object>(row));
            else
                old.put(
                        "count",
                        ((Number) old.get("count")).intValue()
                                + ((Number) row.get("count")).intValue());
        }
        List<Map<String, Object>> sorted = new ArrayList<Map<String, Object>>(result.values());
        sorted.sort(
                (a, b) -> {
                    int count =
                            Integer.compare(
                                    ((Number) b.get("count")).intValue(),
                                    ((Number) a.get("count")).intValue());
                    return count != 0 ? count : a.toString().compareTo(b.toString());
                });
        List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> row : sorted) out.add(Collections.unmodifiableMap(row));
        return Collections.unmodifiableList(out);
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> hierarchy(List<Map<String, Object>> plans) {
        List<Map<String, Object>> root = new ArrayList<Map<String, Object>>();
        Map<String, Map<String, Object>> groups = new LinkedHashMap<String, Map<String, Object>>();
        for (Map<String, Object> plan : plans) {
            List<Map<String, Object>> children = root;
            String path = "";
            String group = (String) plan.get("group");
            if (group != null && !group.isEmpty())
                for (String part : group.split("/")) {
                    path = path.isEmpty() ? part : path + "/" + part;
                    Map<String, Object> node = groups.get(path);
                    if (node == null) {
                        node =
                                map(
                                        "type",
                                        "group",
                                        "name",
                                        part,
                                        "path",
                                        path,
                                        "children",
                                        new ArrayList<Map<String, Object>>());
                        groups.put(path, node);
                        children.add(node);
                    }
                    children = (List<Map<String, Object>>) node.get("children");
                }
            Map<String, Object> leaf = new LinkedHashMap<String, Object>(plan);
            leaf.put("type", "plan");
            children.add(leaf);
        }
        return root;
    }
}
