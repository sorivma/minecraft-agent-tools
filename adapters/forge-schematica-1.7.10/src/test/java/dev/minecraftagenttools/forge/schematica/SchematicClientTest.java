package dev.minecraftagenttools.forge.schematica;

import static dev.minecraftagenttools.protocol.data.Data.*;
import static org.junit.Assert.*;

import dev.minecraftagenttools.schematica.contract.*;
import java.util.*;
import org.junit.Test;

public class SchematicClientTest {
    private static final class FakeGhost implements SchematicClient.Ghost {
        int renders, closed;
        boolean visible;

        public void display(int x, int y, int z, boolean visible, int layer) {
            this.visible = visible;
        }

        public void render() {
            renders++;
        }

        public void dirty() {}

        public void close() {
            closed++;
        }

        public byte[] export(Plans.Plan p) {
            return new byte[] {1, 2, 3};
        }
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
}
