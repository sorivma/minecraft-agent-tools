package dev.minecraftagenttools.schematica.contract;

import static dev.minecraftagenttools.protocol.data.Data.*;
import static org.junit.Assert.*;

import java.util.*;
import org.junit.Test;

public class PlanPresentationTest {
    @Test
    public void countsFinalCellsMergesAliasesAndDistinguishesVariants() {
        Layout layout =
                new Layout(
                        map(
                                "width",
                                8,
                                "height",
                                1,
                                "length",
                                1,
                                "palette",
                                Arrays.asList(
                                        map("block", "minecraft:stone", "label", "Floor"),
                                        map(
                                                "block",
                                                "minecraft:stone",
                                                "meta",
                                                0,
                                                "label",
                                                "Other"),
                                        map("block", "minecraft:stone", "meta", 1),
                                        map("block", "minecraft:air"),
                                        map(
                                                "block",
                                                "gregtech:gt.blockmachines",
                                                "machineId",
                                                123,
                                                "facing",
                                                2),
                                        map(
                                                "block",
                                                "gregtech:gt.blockmachines",
                                                "machineId",
                                                123,
                                                "facing",
                                                3),
                                        map(
                                                "block",
                                                "gregtech:gt.blockmachines",
                                                "machineId",
                                                124)),
                                "boxes",
                                Arrays.asList(
                                        box(0, 7, 0),
                                        box(1, 1, 1),
                                        box(2, 2, 2),
                                        box(3, 3, 3),
                                        box(4, 4, 4),
                                        box(5, 5, 5),
                                        box(6, 6, 6))));
        List<Map<String, Object>> rows = PlanPresentation.materials(layout);
        assertEquals(4, rows.size());
        assertEquals(3, rows.get(0).get("count"));
        assertEquals(2, rows.get(1).get("count"));
        assertEquals(123, rows.get(1).get("machineId"));
        assertEquals(7, rows.stream().mapToInt(r -> ((Number) r.get("count")).intValue()).sum());
        assertFalse(rows.get(1).containsKey("facing"));
    }

    private Map<String, Object> box(int from, int to, int palette) {
        return map("x1", from, "y1", 0, "z1", 0, "x2", to, "y2", 0, "z2", 0, "palette", palette);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void treeSharesAncestorsAndPreservesPlanIdentityAcrossReplacement() {
        Plans plans = new Plans();
        plans.update(this, 0);
        Layout layout = new Layout(AuthoringTest.layout());
        Plans.Plan first = plans.put(null, 0, "A", "Factory/Power", layout, 0, 60, 0);
        Plans.Plan second = plans.put(null, 0, "B", "Factory/Power", layout, 0, 60, 0);
        List<Map<String, Object>> tree =
                PlanPresentation.hierarchy(Arrays.asList(first.summary(), second.summary()));
        assertEquals(1, tree.size());
        List<Map<String, Object>> groups = (List<Map<String, Object>>) tree.get(0).get("children");
        assertEquals("Factory/Power", groups.get(0).get("path"));
        assertEquals(2, ((List<?>) groups.get(0).get("children")).size());
        Plans.Plan moved = plans.put(first.id, 1, "A", "Factory/Machines", layout, 0, 60, 0);
        assertEquals(first.id, moved.id);
        assertEquals(2, moved.revision);
        assertEquals("Factory/Machines", moved.summary().get("group"));
        assertEquals(2, plans.all().size());
    }

    @Test
    public void pathsAreBoundedAndEmptyRootIsAcceptedByTool() {
        Map<String, Object> args = AuthoringTest.layout();
        args.putAll(
                map(
                        "session",
                        "s",
                        "dimension",
                        0,
                        "name",
                        "A",
                        "x",
                        0,
                        "y",
                        64,
                        "z",
                        0,
                        "group",
                        ""));
        SchematicaTools.validate("schematic_put", args);
        for (String group :
                Arrays.asList("/A", "A/", "A//B", "A/../B", "A/ B", "A/B/C/D/E", "A/\nB")) {
            args.put("group", group);
            try {
                SchematicaTools.validate("schematic_put", args);
                fail(group);
            } catch (IllegalArgumentException expected) {
            }
        }
    }

    @Test
    public void manyPlansStillRespectCountAndTotalVolumeBudgets() {
        Plans plans = new Plans();
        plans.update(this, 0);
        Layout layout = new Layout(AuthoringTest.layout());
        for (int i = 0; i < 32; i++) plans.put(null, 0, "P" + i, layout, 0, 60, 0);
        try {
            plans.put(null, 0, "Overflow", layout, 0, 60, 0);
            fail();
        } catch (IllegalArgumentException expected) {
        }
        plans.update(new Object(), 0);
        Map<String, Object> large = AuthoringTest.layout();
        large.putAll(map("width", 128, "height", 4, "length", 128));
        Layout full = new Layout(large);
        plans.put(null, 0, "A", full, 0, 60, 0);
        plans.put(null, 0, "B", full, 0, 60, 0);
        try {
            plans.put(null, 0, "Too large", layout, 0, 60, 0);
            fail();
        } catch (IllegalArgumentException expected) {
        }
    }
}
