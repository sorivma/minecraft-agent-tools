package dev.minecraftagenttools.harness.schematica;

import static dev.minecraftagenttools.protocol.data.Data.*;
import static org.junit.Assert.*;

import com.google.gson.Gson;
import dev.minecraftagenttools.forge.schematica.PlanStore;
import dev.minecraftagenttools.forge.schematica.SchematicClient;
import dev.minecraftagenttools.schematica.contract.Plans;
import dev.minecraftharness.chat.Surface;
import dev.minecraftharness.chat.widget.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class SchematicWidgetTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    private static final class Preview implements Surface {
        final List<String> text = new ArrayList<String>();
        final List<Integer> items = new ArrayList<Integer>();
        boolean nativePreview = true;

        public boolean item(String id, int meta, int count, int x, int y) {
            items.add(meta);
            return true;
        }

        public boolean custom(int left, int top, int width, int height, Runnable draw) {
            if (height == 20 || !nativePreview)
                return false; // Portable host: no native material hover.
            draw.run();
            return true;
        }

        public int textWidth(String s) {
            return s.length() * 6;
        }

        public int lineHeight() {
            return 9;
        }

        public void rectangle(int l, int t, int r, int b, int color) {}

        public void text(String s, int x, int y, int color) {
            text.add(s);
        }

        public void clip(int l, int t, int r, int b) {}

        public void unclip() {}

        public String clipboard() {
            return "";
        }

        public void clipboard(String s) {}

        public void openResource(String s) {}

        public void closePanel() {}

        public void ordinaryChat() {}
    }

    private static final class Ghost implements SchematicClient.Ghost {
        boolean visible;
        int previews;
        boolean failPreview;

        public void preview(int left, int top, int width, int height) {
            previews++;
            if (failPreview) throw new IllegalStateException("Preview failed");
        }

        public void display(int x, int y, int z, boolean visible, int layer) {
            this.visible = visible;
        }

        public void dirty() {}

        public void render() {}

        public void close() {}

        public byte[] export(Plans.Plan plan) {
            return new byte[0];
        }
    }

    private static final class Fixture implements AutoCloseable {
        final Object[] world = {new Object()};
        final int[] dimension = {0};
        final String[] identity = {"world-A"};
        final List<Ghost> ghosts = new ArrayList<Ghost>();
        final SchematicClient client;
        String session;

        Fixture() throws Exception {
            this(null);
        }

        Fixture(PlanStore store) throws Exception {
            client =
                    new SchematicClient(
                            () -> new SchematicClient.State(world[0], dimension[0], identity[0]),
                            (l, x, y, z) -> {
                                Ghost ghost = new Ghost();
                                ghosts.add(ghost);
                                return ghost;
                            },
                            null,
                            store);
            SchematicClient.active = client;
            refresh(store);
        }

        void refresh(PlanStore store) throws Exception {
            session = (String) client.call("schematic_status", map()).get("session");
            if (store != null)
                store.load(PlanStore.key(identity[0], dimension[0]), dimension[0])
                        .get(5, TimeUnit.SECONDS);
            client.tick();
        }

        Map<String, Object> args(String name, int materials) {
            List<Map<String, Object>> palette = new ArrayList<Map<String, Object>>();
            List<Map<String, Object>> boxes = new ArrayList<Map<String, Object>>();
            for (int i = 0; i < materials; i++) {
                palette.add(map("block", "minecraft:stone", "meta", i));
                boxes.add(map("x1", i, "y1", 0, "z1", 0, "x2", i, "y2", 0, "z2", 0, "palette", i));
            }
            return map(
                    "session",
                    session,
                    "dimension",
                    dimension[0],
                    "name",
                    name,
                    "group",
                    "Factory/Power",
                    "x",
                    0,
                    "y",
                    64,
                    "z",
                    0,
                    "width",
                    materials,
                    "height",
                    1,
                    "length",
                    1,
                    "palette",
                    palette,
                    "boxes",
                    boxes);
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> put(String name, int materials) throws Exception {
            return (Map<String, Object>)
                    client.call("schematic_put", args(name, materials)).get("result");
        }

        String payload(Map<String, Object> plan) {
            return new Gson()
                    .toJson(
                            map(
                                    "session",
                                    session,
                                    "dimension",
                                    dimension[0],
                                    "world",
                                    PlanStore.key(identity[0], dimension[0]),
                                    "persistentId",
                                    plan.get("persistentId")));
        }

        public void close() {
            SchematicClient.active = null;
        }
    }

    private static final class Card {
        final Widget widget;
        final Preview surface = new Preview();
        final Map<String, Runnable> actions = new LinkedHashMap<String, Runnable>();
        final int width;

        Card(String payload) {
            this(SchematicWidget.parse(payload), 300);
        }

        Card(Widget widget, int width) {
            this.widget = widget;
            this.width = width;
        }

        void render() {
            actions.clear();
            surface.text.clear();
            surface.items.clear();
            widget.render(
                    new WidgetCanvas(
                            surface,
                            0,
                            0,
                            width,
                            widget.height(width),
                            (id, label, x, y, w, h, action) -> {
                                assertTrue(x >= 0 && x + w <= width);
                                assertTrue(y >= 0 && y + h <= widget.height(width));
                                actions.put(id, action);
                            }));
        }
    }

    @Test
    public void separateFencesProduceSeparateHarnessWidgetsBesideTheirExplanations()
            throws Exception {
        try (Fixture f = new Fixture()) {
            String first = f.payload(f.put("Machines", 1));
            String second = f.payload(f.put("Aisle", 2));
            WidgetRegistry registry = new WidgetRegistry();
            registry.register(
                    "minecraftagenttools:schematic-v1",
                    SchematicWidget.instructions(),
                    SchematicWidget::parse);
            WidgetRegistry.Message message =
                    registry.parse(
                            "Machines explanation\n```minecraftagenttools:schematic-v1\n"
                                    + first
                                    + "\n```\nAisle explanation\n```minecraftagenttools:schematic-v1\n"
                                    + second
                                    + "\n```\n");
            assertEquals(2, message.widgets.size());
            assertTrue(message.prose.contains("Machines explanation"));
            assertTrue(message.prose.contains("Aisle explanation"));
            for (int i = 0; i < 2; i++) {
                Preview surface = new Preview();
                message.widgets.get(i).render(new WidgetCanvas(surface, 0, 0, 300, 224));
                assertTrue(surface.text.contains(i == 0 ? "Machines" : "Aisle"));
                assertFalse(surface.text.contains(i == 0 ? "Aisle" : "Machines"));
            }
            assertTrue(f.ghosts.get(0).visible);
            assertTrue(f.ghosts.get(1).visible);
        }
    }

    @Test
    public void eachCardRendersAndControlsOnlyItsPlanAndReflectsOtherCards() throws Exception {
        try (Fixture f = new Fixture()) {
            Map<String, Object> machines = f.put("Machines", 2);
            Map<String, Object> aisle = f.put("Aisle", 4);
            Card first = new Card(f.payload(machines));
            Card duplicate = new Card(f.payload(machines));
            Card second = new Card(f.payload(aisle));
            assertEquals(0, f.ghosts.get(0).previews);
            first.render();
            assertEquals(Collections.singleton("visibility"), first.actions.keySet());
            assertTrue(first.surface.text.contains("Machines"));
            assertTrue(first.surface.text.contains("2 blocks"));
            assertFalse(first.surface.text.contains("Aisle"));
            assertEquals(1, f.ghosts.get(0).previews);
            assertEquals(0, f.ghosts.get(1).previews);
            assertTrue(f.ghosts.get(0).visible);
            second.render();
            assertTrue(second.surface.text.contains("4 blocks"));
            first.actions.get("visibility").run();
            assertFalse(f.ghosts.get(0).visible);
            assertTrue(f.ghosts.get(1).visible);
            duplicate.render();
            assertTrue(duplicate.surface.text.contains("Show"));
            duplicate.actions.get("visibility").run();
            first.render();
            assertTrue(first.surface.text.contains("Hide"));
            f.put("New unrelated plan", 1);
            first.render();
            assertFalse(first.surface.text.contains("New unrelated plan"));
            assertEquals(Collections.singleton("visibility"), first.actions.keySet());
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    public void replacementUpdatesOwnCardButDeletionNeverRetargetsToSameName() throws Exception {
        try (Fixture f = new Fixture()) {
            Map<String, Object> original = f.put("Same name", 1);
            f.put("Same name", 2);
            Card card = new Card(f.payload(original));
            card.render();
            Runnable stale = card.actions.get("visibility");
            Map<String, Object> replacement = f.args("Renamed", 3);
            replacement.put("planId", original.get("planId"));
            replacement.put("revision", original.get("revision"));
            Map<String, Object> updated =
                    (Map<String, Object>) f.client.call("schematic_put", replacement).get("result");
            assertEquals(original.get("persistentId"), updated.get("persistentId"));
            stale.run();
            assertTrue(f.ghosts.get(2).visible);
            card.render();
            assertTrue(card.surface.text.contains("Renamed"));
            assertEquals("Renamed", card.widget.title());
            assertTrue(card.surface.text.contains("3 blocks"));
            card.actions.get("visibility").run();
            assertFalse(f.ghosts.get(2).visible);
            assertTrue(f.ghosts.get(1).visible);
            f.client.call(
                    "schematic_remove",
                    map(
                            "session",
                            f.session,
                            "dimension",
                            0,
                            "planId",
                            updated.get("planId"),
                            "revision",
                            updated.get("revision")));
            f.put("Renamed", 3);
            card.render();
            assertTrue(card.actions.isEmpty());
            assertTrue(card.surface.text.contains("This schematic is no longer available"));
        }
    }

    @Test
    public void worldAndDimensionChangesExpireCapturedVisibility() throws Exception {
        try (Fixture f = new Fixture()) {
            Card card = new Card(f.payload(f.put("Old world", 1)));
            card.render();
            Runnable stale = card.actions.get("visibility");
            f.dimension[0] = -1;
            f.refresh(null);
            card.render();
            assertTrue(card.actions.isEmpty());
            stale.run();
            assertTrue(f.ghosts.get(0).visible);
            f.world[0] = new Object();
            f.dimension[0] = 0;
            f.refresh(null);
            f.put("Old world", 1);
            card.render();
            assertTrue(card.actions.isEmpty());
            assertTrue(card.surface.text.get(0).startsWith("Previous world"));
            stale.run();
            assertTrue(f.ghosts.get(1).visible);
        }
    }

    @Test
    public void legendPaginationIsIndependentAndClampsAfterReplacementOrResize() throws Exception {
        try (Fixture f = new Fixture()) {
            Map<String, Object> plan = f.put("Legend", 15);
            Card first = new Card(SchematicWidget.parse(f.payload(plan)), 220);
            Card second = new Card(SchematicWidget.parse(f.payload(plan)), 220);
            Set<Integer> seen = new HashSet<Integer>();
            for (int page = 0; page < 3; page++) {
                first.render();
                seen.addAll(first.surface.items);
                assertTrue(first.actions.containsKey("visibility"));
                if (page < 2) first.actions.get("legend-next").run();
                else assertFalse(first.actions.containsKey("legend-next"));
            }
            assertEquals(15, seen.size());
            second.render();
            assertEquals(Integer.valueOf(0), second.surface.items.get(0));
            assertFalse(second.actions.containsKey("legend-prev"));
            Card wider = new Card(first.widget, 700);
            wider.render();
            assertFalse(wider.actions.containsKey("legend-prev"));
            assertEquals(15, wider.surface.items.size());
            Map<String, Object> replacement = f.args("Legend", 1);
            replacement.put("planId", plan.get("planId"));
            replacement.put("revision", plan.get("revision"));
            f.client.call("schematic_put", replacement);
            first.render();
            assertEquals(Collections.singletonList(0), first.surface.items);
            assertFalse(first.actions.containsKey("legend-next"));
            assertEquals(0, first.widget.height(219));
        }
    }

    @Test
    public void previewFailureDoesNotDisableControlsOrOtherCards() throws Exception {
        try (Fixture f = new Fixture()) {
            Card first = new Card(f.payload(f.put("Broken preview", 1)));
            Card second = new Card(f.payload(f.put("Healthy preview", 1)));
            f.ghosts.get(0).failPreview = true;
            first.render();
            first.render();
            assertEquals(1, f.ghosts.get(0).previews);
            assertTrue(first.surface.text.contains("3D error"));
            assertTrue(first.actions.containsKey("visibility"));
            second.render();
            assertEquals(1, f.ghosts.get(1).previews);
            second.surface.nativePreview = false;
            second.render();
            assertTrue(second.surface.text.contains("3D"));
            assertTrue(second.actions.containsKey("visibility"));
        }
    }

    @Test
    public void missingIdentityCannotBecomeAGlobalCard() throws Exception {
        try (Fixture f = new Fixture()) {
            f.put("Only plan", 1);
            String payload = new Gson().toJson(map("session", f.session, "dimension", 0));
            try {
                SchematicWidget.parse(payload);
                fail("An individual card requires an identity");
            } catch (IllegalArgumentException expected) {
            }
            Card legacy = new Card(SchematicWidget.parseLegacy(payload), 300);
            legacy.render();
            assertTrue(legacy.actions.isEmpty());
            assertFalse(legacy.surface.text.contains("Only plan"));
            assertEquals(0, f.ghosts.get(0).previews);
            assertTrue(f.ghosts.get(0).visible);
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    public void savedCardsReconnectByIdentityAcrossRestartsWithoutDuplicatesOrRetargeting()
            throws Exception {
        Path dir = temporary.newFolder().toPath();
        String firstPayload, secondPayload, deletedPayload, legacyPayload, oldSession;
        Map<String, Object> first;
        try (PlanStore store = new PlanStore(dir);
                Fixture f = new Fixture(store)) {
            first = f.put("Same name", 1);
            firstPayload = f.payload(first);
            secondPayload = f.payload(f.put("Same name", 2));
            Map<String, Object> deleted = f.put("Same name", 3);
            deletedPayload = f.payload(deleted);
            f.client.call(
                    "schematic_remove",
                    map(
                            "session",
                            f.session,
                            "dimension",
                            0,
                            "planId",
                            deleted.get("planId"),
                            "revision",
                            1));
            oldSession = f.session;
            legacyPayload = new Gson().toJson(map("session", f.session, "dimension", 0));
        }
        try (PlanStore store = new PlanStore(dir);
                Fixture f = new Fixture(store)) {
            Card one = new Card(firstPayload),
                    two = new Card(secondPayload),
                    deleted = new Card(deletedPayload);
            one.render();
            assertEquals(Collections.singleton("restore"), one.actions.keySet());
            assertTrue(f.ghosts.isEmpty());
            Runnable capturedRestore = one.actions.get("restore");
            f.world[0] = new Object();
            f.identity[0] = "world-B";
            f.refresh(store);
            capturedRestore.run();
            assertTrue(f.ghosts.isEmpty());
            one.render();
            assertTrue(one.actions.isEmpty());
            f.world[0] = new Object();
            f.identity[0] = "world-A";
            f.refresh(store);
            one.render();
            one.actions.get("restore").run();
            assertEquals(2, f.ghosts.size());
            one.render();
            assertTrue(one.surface.text.contains("1 blocks"));
            assertFalse(one.actions.containsKey("restore"));
            two.render();
            assertEquals(Collections.singleton("restore"), two.actions.keySet());
            two.actions.get("restore").run();
            two.render();
            assertTrue(two.surface.text.contains("2 blocks"));
            assertEquals(2, f.ghosts.size());
            one.actions.get("visibility").run();
            assertFalse(f.ghosts.get(0).visible);
            assertTrue(f.ghosts.get(1).visible);
            deleted.render();
            deleted.actions.get("restore").run();
            deleted.render();
            assertTrue(deleted.actions.isEmpty());
            assertTrue(deleted.surface.text.contains("This schematic is no longer available"));
            try {
                f.client.visibility(oldSession, 0, (String) first.get("planId"), 1, false);
                fail("Old command IDs must expire");
            } catch (IllegalArgumentException expected) {
            }
            Card legacy = new Card(SchematicWidget.parseLegacy(legacyPayload), 300);
            legacy.render();
            legacy.actions.get("restore").run();
            legacy.render();
            assertTrue(legacy.actions.isEmpty());
            assertEquals(2, f.ghosts.size());
        }
        // Reopening the original saved chat after another restart still finds the same blueprint.
        try (PlanStore store = new PlanStore(dir);
                Fixture f = new Fixture(store)) {
            Card card = new Card(firstPayload);
            card.render();
            card.actions.get("restore").run();
            card.render();
            assertTrue(card.surface.text.contains("1 blocks"));
            assertTrue(card.surface.text.contains("Show"));
            assertEquals(2, f.ghosts.size());
            List<Map<String, Object>> plans =
                    (List<Map<String, Object>>) f.client.snapshot(f.session, 0).get("plans");
            assertNotEquals(first.get("planId"), plans.get(0).get("planId"));
            assertEquals(first.get("persistentId"), plans.get(0).get("persistentId"));
        }
    }
}
