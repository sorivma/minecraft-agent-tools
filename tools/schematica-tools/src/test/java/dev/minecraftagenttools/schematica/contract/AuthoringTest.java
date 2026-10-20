package dev.minecraftagenttools.schematica.contract;

import static dev.minecraftagenttools.protocol.data.Data.*;
import static org.junit.Assert.*;

import java.util.*;
import org.junit.Test;

public class AuthoringTest {
    @Test
    public void pipesAndModTileDataSurviveTheAuthoringContract() {
        Map<String, Object> data = layout();
        data.put(
                "palette",
                Arrays.asList(
                        map(
                                "block",
                                "gregtech:gt.blockmachines",
                                "machineId",
                                5000,
                                "connections",
                                48),
                        map("block", "example:pipe", "meta", 2, "tileNbt", "{type:42}")));
        Layout parsed = new Layout(data);
        assertEquals(48, parsed.palette.get(0).get("connections"));
        assertEquals("{type:42}", parsed.palette.get(1).get("tileNbt"));
        data.put("palette", Arrays.asList(map("block", "example:pipe", "connections", 48)));
        reject(() -> new Layout(data));
    }

    public static Map<String, Object> layout() {
        return map(
                "width",
                4,
                "height",
                2,
                "length",
                3,
                "palette",
                Arrays.asList(map("block", "minecraft:stone"), map("block", "minecraft:air")),
                "boxes",
                Arrays.asList(
                        map("x1", 0, "y1", 0, "z1", 0, "x2", 3, "y2", 0, "z2", 2, "palette", 0),
                        map("x1", 1, "y1", 0, "z1", 1, "x2", 1, "y2", 0, "z2", 1, "palette", 1)));
    }

    @Test
    public void inclusiveBoxesAndLastWriterWinInSchematicOrder() {
        Layout layout = new Layout(layout());
        int[] cells = layout.cells();
        assertEquals(24, cells.length);
        assertEquals(0, cells[0]);
        assertEquals(1, cells[5]);
        assertEquals(-1, cells[12]);
    }

    @Test
    public void authoringLimitsAndTypedMachineDataAreEnforced() {
        Map<String, Object> data = layout();
        data.put("width", 128);
        data.put("height", 128);
        data.put("length", 128);
        reject(() -> new Layout(data));
        data.put("height", 2);
        data.put("length", 3);
        data.put(
                "palette",
                Arrays.asList(
                        map("block", "gregtech:gt.blockmachines", "meta", 4, "machineId", 123)));
        reject(() -> new Layout(data));
        data.put("palette", Arrays.asList(map("block", "minecraft:stone", "facing", 2)));
        reject(() -> new Layout(data));
        Map<String, Object> args = layout();
        args.putAll(map("session", "s", "dimension", 0, "name", "base", "x", 0, "y", 255, "z", 0));
        reject(() -> SchematicaTools.validate("schematic_put", args));
        args.put("y", 60);
        args.put("planId", "x");
        reject(() -> SchematicaTools.validate("schematic_put", args));
        reject(
                () ->
                        SchematicaTools.validate(
                                "schematic_display",
                                map(
                                        "session",
                                        "s",
                                        "dimension",
                                        0,
                                        "planId",
                                        "p",
                                        "revision",
                                        1,
                                        "visible",
                                        "false")));
        reject(
                () ->
                        SchematicaTools.validate(
                                "schematic_display",
                                map(
                                        "session",
                                        "s",
                                        "dimension",
                                        0,
                                        "planId",
                                        "p",
                                        "revision",
                                        1,
                                        "x",
                                        0)));
    }

    @Test
    public void worldIdentityDimensionAndRevisionsRevokeOldHandles() {
        Plans plans = new Plans();
        Object world = new Object();
        plans.update(world, 0);
        String first = plans.session();
        Plans.Plan plan = plans.put(null, 0, "base", new Layout(layout()), 0, 60, 0);
        reject(() -> plans.put(plan.id, 99, "bad", plan.layout, 0, 0, 0));
        assertEquals(1, plans.all().size());
        plans.update(world, 1);
        reject(() -> plans.require(first, 0));
        assertTrue(plans.all().isEmpty());
        String second = plans.session();
        plans.update(new Object(), 1);
        reject(() -> plans.require(second, 1));
    }

    private static void reject(Runnable action) {
        try {
            action.run();
            fail("Expected validation failure");
        } catch (IllegalArgumentException expected) {
        }
    }
}
