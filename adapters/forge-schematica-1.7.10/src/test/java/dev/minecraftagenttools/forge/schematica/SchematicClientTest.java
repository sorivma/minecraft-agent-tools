package dev.minecraftagenttools.forge.schematica;

import static dev.minecraftagenttools.protocol.data.Data.*;
import static org.junit.Assert.*;

import dev.minecraftagenttools.schematica.contract.*;
import java.util.*;
import org.junit.Test;

public class SchematicClientTest {
    private static final class FakeGhost implements SchematicClient.Ghost {
        int renders, closed, previews;
        boolean visible;

        public void display(int x, int y, int z, boolean visible, int layer) {
            this.visible = visible;
        }

        public void render() {
            renders++;
        }

        public void dirty() {}

        public void preview(int left, int top, int width, int height) {
            previews++;
        }

        public void close() {
            closed++;
        }

        public byte[] export(Plans.Plan p) {
            return new byte[] {1, 2, 3};
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    public void hierarchyMaterialsExportAndPreviewRespectScopeAndRevision() throws Exception {
        List<FakeGhost> ghosts = new ArrayList<FakeGhost>();
        SchematicClient client =
                new SchematicClient(
                        () -> new SchematicClient.State(this, 0),
                        (l, x, y, z) -> {
                            FakeGhost ghost = new FakeGhost();
                            ghosts.add(ghost);
                            return ghost;
                        },
                        null);
        String session = (String) client.call("schematic_status", map()).get("session");
        Map<String, Object> args = put(session);
        args.put("group", "Factory/Power");
        Map<String, Object> first =
                (Map<String, Object>) client.call("schematic_put", args).get("result");
        String id = (String) first.get("planId");
        client.call("schematic_put", args);
        Map<String, Object> snapshot =
                client.call("schematic_list", map("session", session, "dimension", 0));
        assertEquals(1, ((List<?>) snapshot.get("hierarchy")).size());
        assertEquals(
                24, ((List<Map<String, Object>>) snapshot.get("materials")).get(0).get("count"));
        client.visibility(session, 0, id, 1, false);
        client.preview(session, 0, id, 1, 0, 0, 80, 80);
        assertEquals(1, ghosts.get(0).previews);
        assertFalse(ghosts.get(0).visible);
        Map<String, Object> exported =
                client.call(
                        "schematic_export", map("session", session, "dimension", 0, "planId", id));
        assertTrue(((String) exported.get("layoutJson")).contains("Factory/Power"));
        args.putAll(map("planId", id, "revision", 1, "group", "Factory/Machines"));
        client.call("schematic_put", args);
        try {
            client.preview(session, 0, id, 1, 0, 0, 80, 80);
            fail();
        } catch (IllegalArgumentException expected) {
        }
        try {
            client.preview(session, 1, id, 2, 0, 0, 80, 80);
            fail();
        } catch (IllegalArgumentException expected) {
        }
        assertEquals(0, ghosts.get(2).previews);
        assertEquals(1, ghosts.get(0).closed);
        Map<String, Object> result =
                (Map<String, Object>)
                        client.call(
                                        "schematic_get",
                                        map("session", session, "dimension", 0, "planId", id))
                                .get("result");
        assertEquals("Factory/Machines", result.get("group"));
        assertEquals(false, result.get("visible"));
    }

    private Map<String, Object> put(String session) {
        return map(
                "session",
                session,
                "dimension",
                0,
                "name",
                "Machines",
                "x",
                0,
                "y",
                60,
                "z",
                0,
                "width",
                4,
                "height",
                2,
                "length",
                3,
                "palette",
                Arrays.asList(map("block", "minecraft:stone")),
                "boxes",
                Arrays.asList(
                        map("x1", 0, "y1", 0, "z1", 0, "x2", 3, "y2", 0, "z2", 2, "palette", 0)));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void multiplePlansIndependentVisibilityAndStaleCallbacks() throws Exception {
        Object[] world = {new Object()};
        List<FakeGhost> created = new ArrayList<FakeGhost>();
        SchematicClient client =
                new SchematicClient(
                        () -> new SchematicClient.State(world[0], 0),
                        (l, x, y, z) -> {
                            FakeGhost g = new FakeGhost();
                            created.add(g);
                            return g;
                        },
                        null);
        String session = (String) client.call("schematic_status", map()).get("session");
        Map<String, Object> first =
                (Map<String, Object>) client.call("schematic_put", put(session)).get("result");
        client.call("schematic_put", put(session));
        client.render();
        assertEquals(1, created.get(0).renders);
        assertEquals(1, created.get(1).renders);
        client.visibility(session, 0, (String) first.get("planId"), 1, false);
        client.render();
        assertEquals(1, created.get(0).renders);
        assertEquals(2, created.get(1).renders);
        world[0] = new Object();
        assertFalse((Boolean) client.snapshot(session, 0).get("current"));
        assertEquals(1, created.get(0).closed);
        assertEquals(1, created.get(1).closed);
        try {
            client.visibility(session, 0, (String) first.get("planId"), 1, true);
            fail();
        } catch (IllegalArgumentException expected) {
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    public void failedReplacementLeavesPreviousGhostAndRevision() throws Exception {
        boolean[] fail = {false};
        FakeGhost ghost = new FakeGhost();
        SchematicClient client =
                new SchematicClient(
                        () -> new SchematicClient.State(this, 0),
                        (l, x, y, z) -> {
                            if (fail[0]) throw new IllegalArgumentException("Unknown block");
                            return ghost;
                        },
                        null);
        String session = (String) client.call("schematic_status", map()).get("session");
        Map<String, Object> first =
                (Map<String, Object>) client.call("schematic_put", put(session)).get("result");
        Map<String, Object> replacement = put(session);
        replacement.put("planId", first.get("planId"));
        replacement.put("revision", 1);
        fail[0] = true;
        try {
            client.call("schematic_put", replacement);
            fail();
        } catch (IllegalArgumentException expected) {
        }
        client.render();
        assertEquals(1, ghost.renders);
        assertEquals(0, ghost.closed);
        assertEquals(1, ((List<?>) client.snapshot(session, 0).get("plans")).size());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void batchChecksAllRevisionsBeforeChangingAnyGhostAndRejectsOldWorld() throws Exception {
        Object[] world = {new Object()};
        List<FakeGhost> ghosts = new ArrayList<FakeGhost>();
        SchematicClient client =
                new SchematicClient(
                        () -> new SchematicClient.State(world[0], 0),
                        (l, x, y, z) -> {
                            FakeGhost g = new FakeGhost();
                            ghosts.add(g);
                            return g;
                        },
                        null);
        String session = (String) client.call("schematic_status", map()).get("session");
        client.call("schematic_put", put(session));
        Map<String, Object> second =
                (Map<String, Object>) client.call("schematic_put", put(session)).get("result");
        List<Map<String, Object>> captured =
                (List<Map<String, Object>>) client.snapshot(session, 0).get("plans");
        Map<String, Object> replacement = put(session);
        replacement.put("planId", second.get("planId"));
        replacement.put("revision", 1);
        client.call("schematic_put", replacement);
        try {
            client.visibilityBatch(session, 0, captured, false);
            fail();
        } catch (IllegalArgumentException expected) {
        }
        assertEquals(
                true,
                ((List<Map<String, Object>>) client.snapshot(session, 0).get("plans"))
                        .get(0)
                        .get("visible"));
        captured = (List<Map<String, Object>>) client.snapshot(session, 0).get("plans");
        client.visibilityBatch(session, 0, captured, false);
        for (Map<String, Object> row :
                (List<Map<String, Object>>) client.snapshot(session, 0).get("plans"))
            assertEquals(false, row.get("visible"));
        world[0] = new Object();
        try {
            client.visibilityBatch(session, 0, captured, true);
            fail();
        } catch (IllegalArgumentException expected) {
        }
    }
}
