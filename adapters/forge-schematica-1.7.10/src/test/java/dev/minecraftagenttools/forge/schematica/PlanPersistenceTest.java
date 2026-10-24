package dev.minecraftagenttools.forge.schematica;

import static dev.minecraftagenttools.protocol.data.Data.*;
import static org.junit.Assert.*;

import dev.minecraftagenttools.schematica.contract.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.*;
import org.junit.rules.TemporaryFolder;

public class PlanPersistenceTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    private static final class Ghost implements SchematicClient.Ghost {
        boolean closed;

        public void display(int x, int y, int z, boolean visible, int layer) {}

        public void render() {}

        public void dirty() {}

        public void close() {
            closed = true;
        }

        public byte[] export(Plans.Plan p) {
            return new byte[0];
        }
    }

    private Map<String, Object> put(String session, String name) {
        return map(
                "session",
                session,
                "dimension",
                0,
                "name",
                name,
                "group",
                "Factory/Power",
                "x",
                10,
                "y",
                64,
                "z",
                -20,
                "width",
                2,
                "height",
                2,
                "length",
                1,
                "palette",
                Arrays.asList(
                        map(
                                "block",
                                "gregtech:gt.blockmachines",
                                "machineId",
                                123,
                                "facing",
                                2,
                                "label",
                                "Machine"),
                        map("block", "minecraft:stone", "meta", 3, "tileNbt", "{test:1}")),
                "boxes",
                Arrays.asList(
                        map("x1", 0, "y1", 0, "z1", 0, "x2", 0, "y2", 0, "z2", 0, "palette", 0),
                        map("x1", 1, "y1", 0, "z1", 0, "x2", 1, "y2", 0, "z2", 0, "palette", 1)));
    }

    private String ready(SchematicClient client, PlanStore store, String identity, int dimension)
            throws Exception {
        String session = (String) client.call("schematic_status", map()).get("session");
        store.load(PlanStore.key(identity, dimension), dimension).get(5, TimeUnit.SECONDS);
        client.tick();
        return session;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> rows(SchematicClient client, String session, int dimension) {
        return (List<Map<String, Object>>) client.snapshot(session, dimension).get("plans");
    }

    @SuppressWarnings("unchecked")
    @Test
    public void survivesProcessRestartAndRestoresFullTreeOnlyOnExplicitAction() throws Exception {
        Path dir = temporary.newFolder().toPath();
        String key = PlanStore.key("world-A", 0), oldSession;
        Map<String, Object> original;
        try (PlanStore store = new PlanStore(dir)) {
            SchematicClient client =
                    new SchematicClient(
                            () -> new SchematicClient.State(this, 0, "world-A"),
                            (l, x, y, z) -> new Ghost(),
                            null,
                            store);
            oldSession = ready(client, store, "world-A", 0);
            original =
                    (Map<String, Object>)
                            client.call("schematic_put", put(oldSession, "Power")).get("result");
            Map<String, Object> other = put(oldSession, "Water");
            other.put("group", "Factory/Water");
            client.call("schematic_put", other);
            client.call(
                    "schematic_display",
                    map(
                            "session",
                            oldSession,
                            "dimension",
                            0,
                            "planId",
                            original.get("planId"),
                            "revision",
                            1,
                            "visible",
                            false,
                            "layer",
                            1,
                            "x",
                            15,
                            "y",
                            70,
                            "z",
                            -22));
            assertEquals(
                    2, ((List<?>) store.load(key, 0).get(5, TimeUnit.SECONDS).get("plans")).size());
        }
        try (PlanStore store = new PlanStore(dir)) {
            List<Ghost> built = new ArrayList<Ghost>();
            SchematicClient client =
                    new SchematicClient(
                            () -> new SchematicClient.State(this, 0, "world-A"),
                            (l, x, y, z) -> {
                                Ghost g = new Ghost();
                                built.add(g);
                                return g;
                            },
                            null,
                            store);
            String session = ready(client, store, "world-A", 0);
            assertNotEquals(oldSession, session);
            assertTrue(rows(client, session, 0).isEmpty());
            assertTrue(built.isEmpty());
            assertEquals(
                    true,
                    client.recovery(oldSession, 0, null)
                            .get("available")); // Legacy card session alias.
            try {
                client.call("schematic_put", put(session, "Overwrite"));
                fail();
            } catch (IllegalStateException expected) {
            }
            client.restore(session, 0, key);
            List<Map<String, Object>> restored = rows(client, session, 0);
            assertEquals(2, restored.size());
            assertEquals(2, built.size());
            assertNotEquals(original.get("planId"), restored.get(0).get("planId"));
            assertEquals(original.get("persistentId"), restored.get(0).get("persistentId"));
            assertEquals(false, restored.get(0).get("visible"));
            assertEquals(1, restored.get(0).get("layer"));
            assertEquals(15, restored.get(0).get("x"));
            assertEquals(70, restored.get(0).get("y"));
            assertEquals(-22, restored.get(0).get("z"));
            assertEquals("Factory/Power", restored.get(0).get("group"));
            Map<String, Object> full =
                    (Map<String, Object>)
                            client.call(
                                            "schematic_get",
                                            map(
                                                    "session",
                                                    session,
                                                    "dimension",
                                                    0,
                                                    "planId",
                                                    restored.get(0).get("planId")))
                                    .get("result");
            List<Map<String, Object>> palette = (List<Map<String, Object>>) full.get("palette");
            assertEquals(123, ((Number) palette.get(0).get("machineId")).intValue());
            assertEquals("{test:1}", palette.get(1).get("tileNbt"));
            assertEquals(2, ((List<?>) full.get("boxes")).size());
            client.restore(session, 0, key);
            assertEquals(2, built.size()); // Two chat cards cannot duplicate the saved set.
            try {
                client.visibility(oldSession, 0, (String) original.get("planId"), 1, true);
                fail();
            } catch (IllegalArgumentException expected) {
            }
            client.call(
                    "schematic_remove",
                    map(
                            "session",
                            session,
                            "dimension",
                            0,
                            "planId",
                            restored.get(0).get("planId"),
                            "revision",
                            1));
            assertEquals(
                    1, ((List<?>) store.load(key, 0).get(5, TimeUnit.SECONDS).get("plans")).size());
        }
    }

    @Test
    public void worldAndDimensionChangesRejectSavedRestoreCallbacks() throws Exception {
        String[] identity = {"world-A"};
        int[] dimension = {0};
        Object[] world = {new Object()};
        try (PlanStore store = new PlanStore(temporary.newFolder().toPath())) {
            SchematicClient client =
                    new SchematicClient(
                            () -> new SchematicClient.State(world[0], dimension[0], identity[0]),
                            (l, x, y, z) -> new Ghost(),
                            null,
                            store);
            String old = ready(client, store, identity[0], 0);
            client.call("schematic_put", put(old, "Power"));
            store.load(PlanStore.key(identity[0], 0), 0).get(5, TimeUnit.SECONDS);
            world[0] = new Object();
            identity[0] = "world-B";
            String next = ready(client, store, identity[0], 0);
            assertEquals(
                    false, client.recovery(old, 0, PlanStore.key("world-A", 0)).get("available"));
            try {
                client.restore(old, 0, PlanStore.key("world-A", 0));
                fail();
            } catch (IllegalArgumentException expected) {
            }
            try {
                client.restore(next, 0, PlanStore.key("world-A", 0));
                fail();
            } catch (IllegalArgumentException expected) {
            }
            assertTrue(rows(client, next, 0).isEmpty());
            world[0] = new Object();
            identity[0] = "world-A";
            dimension[0] = -1;
            String nether = ready(client, store, identity[0], -1);
            assertTrue(rows(client, nether, -1).isEmpty());
            assertEquals(
                    false, client.recovery(old, 0, PlanStore.key("world-A", 0)).get("available"));
            world[0] = new Object();
            dimension[0] = 0;
            String returned = ready(client, store, identity[0], 0);
            client.restore(returned, 0, PlanStore.key("world-A", 0));
            assertEquals(1, rows(client, returned, 0).size());
        }
    }

    @Test
    public void failedRestorePublishesNothingAndKeepsArchiveForRetry() throws Exception {
        Path dir = temporary.newFolder().toPath();
        String key = PlanStore.key("world-A", 0);
        try (PlanStore store = new PlanStore(dir)) {
            SchematicClient first =
                    new SchematicClient(
                            () -> new SchematicClient.State(this, 0, "world-A"),
                            (l, x, y, z) -> new Ghost(),
                            null,
                            store);
            String old = ready(first, store, "world-A", 0);
            first.call("schematic_put", put(old, "One"));
            first.call("schematic_put", put(old, "Two"));
        }
        byte[] before = Files.readAllBytes(dir.resolve(key + ".json"));
        try (PlanStore store = new PlanStore(dir)) {
            List<Ghost> built = new ArrayList<Ghost>();
            boolean[] fail = {true};
            SchematicClient client =
                    new SchematicClient(
                            () -> new SchematicClient.State(this, 0, "world-A"),
                            (l, x, y, z) -> {
                                if (fail[0] && built.size() == 1)
                                    throw new IllegalArgumentException("Missing mod block");
                                Ghost g = new Ghost();
                                built.add(g);
                                return g;
                            },
                            null,
                            store);
            String session = ready(client, store, "world-A", 0);
            try {
                client.restore(session, 0, key);
                fail();
            } catch (IllegalArgumentException expected) {
            }
            assertTrue(rows(client, session, 0).isEmpty());
            assertTrue(built.get(0).closed);
            assertArrayEquals(before, Files.readAllBytes(dir.resolve(key + ".json")));
            fail[0] = false;
            client.restore(session, 0, key);
            assertEquals(2, rows(client, session, 0).size());
        }
    }

    @Test
    public void corruptArchiveCannotBeOverwrittenByNewPlans() throws Exception {
        Path dir = temporary.newFolder().toPath();
        String key = PlanStore.key("world-A", 0);
        byte[] broken = "{broken".getBytes(StandardCharsets.UTF_8);
        Files.write(dir.resolve(key + ".json"), broken);
        try (PlanStore store = new PlanStore(dir)) {
            SchematicClient client =
                    new SchematicClient(
                            () -> new SchematicClient.State(this, 0, "world-A"),
                            (l, x, y, z) -> new Ghost(),
                            null,
                            store);
            String session = (String) client.call("schematic_status", map()).get("session");
            try {
                store.load(key, 0).get(5, TimeUnit.SECONDS);
                fail();
            } catch (java.util.concurrent.ExecutionException expected) {
            }
            client.tick();
            assertFalse(((String) client.snapshot(session, 0).get("storageError")).isEmpty());
            try {
                client.call("schematic_put", put(session, "Overwrite"));
                fail();
            } catch (IllegalStateException expected) {
            }
            assertArrayEquals(broken, Files.readAllBytes(dir.resolve(key + ".json")));
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    public void legacyArchivesAssignDistinctIdentitiesAndPersistThemOnRestore() throws Exception {
        Path dir = temporary.newFolder().toPath();
        String key = PlanStore.key("world-A", 0);
        List<Map<String, Object>> legacy = new ArrayList<Map<String, Object>>();
        for (int i = 0; i < 2; i++) {
            Map<String, Object> row = put("old", "Same name");
            row.remove("session");
            row.remove("dimension");
            row.put("visible", true);
            row.put("layer", -1);
            legacy.add(row);
        }
        Object firstId;
        try (PlanStore store = new PlanStore(dir)) {
            store.save(key, 0, Collections.singleton("old"), legacy).get(5, TimeUnit.SECONDS);
            SchematicClient client =
                    new SchematicClient(
                            () -> new SchematicClient.State(this, 0, "world-A"),
                            (l, x, y, z) -> new Ghost(),
                            null,
                            store);
            String session = ready(client, store, "world-A", 0);
            client.restore(session, 0, key);
            List<Map<String, Object>> plans = rows(client, session, 0);
            firstId = plans.get(0).get("persistentId");
            assertNotNull(firstId);
            assertNotEquals(firstId, plans.get(1).get("persistentId"));
        }
        try (PlanStore store = new PlanStore(dir)) {
            List<Map<String, Object>> saved =
                    (List<Map<String, Object>>)
                            store.load(key, 0).get(5, TimeUnit.SECONDS).get("plans");
            assertEquals(firstId, saved.get(0).get("persistentId"));
        }
    }

    @Test
    public void archivesRejectDuplicateOrMalformedCardIdentities() throws Exception {
        Path dir = temporary.newFolder().toPath();
        String key = PlanStore.key("world-A", 0);
        String valid = UUID.randomUUID().toString();
        for (String id : Arrays.asList(valid, "not-an-id")) {
            List<Map<String, Object>> saved = new ArrayList<Map<String, Object>>();
            for (int i = 0; i < 2; i++) {
                Map<String, Object> row = put("old", "Plan");
                row.remove("session");
                row.remove("dimension");
                row.put("visible", true);
                row.put("layer", -1);
                row.put("persistentId", id);
                saved.add(row);
            }
            try (PlanStore store = new PlanStore(dir)) {
                store.save(key, 0, Collections.singleton("old"), saved).get(5, TimeUnit.SECONDS);
                try {
                    store.load(key, 0).get(5, TimeUnit.SECONDS);
                    fail("Ambiguous or malformed identities must not be loaded");
                } catch (java.util.concurrent.ExecutionException expected) {
                    assertTrue(expected.getCause() instanceof java.io.IOException);
                }
            }
        }
    }
}
