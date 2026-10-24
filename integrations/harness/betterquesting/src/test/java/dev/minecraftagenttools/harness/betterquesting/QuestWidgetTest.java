package dev.minecraftagenttools.harness.betterquesting;

import static dev.minecraftagenttools.protocol.data.Data.*;
import static org.junit.Assert.*;

import com.google.gson.Gson;
import cpw.mods.fml.common.Mod;
import dev.minecraftharness.chat.Surface;
import dev.minecraftharness.chat.widget.*;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;
import javax.imageio.ImageIO;
import org.junit.Test;

public class QuestWidgetTest {
    private static final UUID FIRST = new UUID(0, 1), SECOND = new UUID(0, 2);

    private static final class Backend implements QuestWidget.Backend {
        boolean current = true;
        int reads, opens, icons, taskReads;
        int progress = 12;
        String taskLogic = "OR";
        UUID iconQuest;
        final List<String> hoverText = new ArrayList<String>();

        public void hover(String text, int x, int y, int width, int height) {
            hoverText.add(text);
        }

        UUID lastQuest;
        String lastSection;
        int lastOffset;

        public Map<String, Object> snapshot(
                String session, int dimension, UUID quest, String section, int offset) {
            reads++;
            lastQuest = quest;
            lastSection = section;
            lastOffset = offset;
            if (!current) throw new IllegalArgumentException("Old session");
            StringBuilder description = new StringBuilder();
            for (int i = 0; i < 80; i++) description.append("Read this quest carefully. ");
            Map<String, Object> data =
                    map(
                            "name",
                            quest.equals(FIRST) ? "Steam quest" : "Next quest",
                            "state",
                            "unclaimed",
                            "completedTasks",
                            1,
                            "countedTasks",
                            2,
                            "ignoredTasks",
                            0,
                            "taskLogic",
                            taskLogic,
                            "questLogic",
                            "AND",
                            "repeatTimeTicks",
                            -1,
                            "description",
                            description.toString(),
                            "descriptionTruncated",
                            false);
            data.put("rewardCount", 2);
            data.put("prerequisiteCount", 3);
            data.put("completedPrerequisites", 1);
            List<Object> entries = new ArrayList<Object>();
            if (section.equals("tasks") || section.equals("rewards")) {
                for (int i = 0; i < 4; i++)
                    entries.add(
                            map(
                                    "name",
                                    "Component " + (offset + i),
                                    "taskId",
                                    offset + i,
                                    "action",
                                    "Submit items",
                                    "requirementLogic",
                                    "ALL",
                                    "totalConditions",
                                    7,
                                    "notes",
                                    Arrays.asList("Items are consumed", "Exact NBT required"),
                                    "conditions",
                                    Collections.singletonList(condition(offset + i)),
                                    "type",
                                    "test:type",
                                    "complete",
                                    true,
                                    "items",
                                    map(
                                            "entries",
                                            Collections.singletonList(
                                                    map(
                                                            "registryName",
                                                            "minecraft:stone",
                                                            "count",
                                                            100000,
                                                            "damage",
                                                            0,
                                                            "displayName",
                                                            "Stone",
                                                            "hasNbt",
                                                            false)),
                                            "nextOffset",
                                            null)));
                data.put("nextOffset", offset == 0 ? 4 : null);
            } else if (section.equals("before") || section.equals("after")) {
                entries.add(
                        map(
                                "name",
                                "Related quest",
                                "questId",
                                SECOND.toString(),
                                "state",
                                "locked",
                                "requirementType",
                                "NORMAL"));
                entries.add(
                        map(
                                "questId",
                                new UUID(0, 99).toString(),
                                "missing",
                                true,
                                "requirementType",
                                "HIDDEN"));
            }
            data.put("entries", entries);
            return data;
        }

        public void open(String session, int dimension, UUID quest) {
            if (!current) throw new IllegalArgumentException("Old session");
            opens++;
            lastQuest = quest;
        }

        private Map<String, Object> condition(int index) {
            return map(
                    "name",
                    index == 0 ? "Iron Ingot" : "Requirement " + index,
                    "registryName",
                    "minecraft:iron_ingot",
                    "damage",
                    0,
                    "hasNbt",
                    false,
                    "current",
                    progress,
                    "required",
                    64,
                    "remaining",
                    Math.max(0, 64 - progress),
                    "unit",
                    "items",
                    "satisfied",
                    progress >= 64,
                    "match",
                    "Any ore: ingotIron");
        }

        public Map<String, Object> task(
                String session, int dimension, UUID quest, int taskId, int offset) {
            if (!current) throw new IllegalArgumentException("Old session");
            taskReads++;
            List<Object> conditions = new ArrayList<Object>();
            for (int i = offset; i < Math.min(7, offset + 4); i++) conditions.add(condition(i));
            return map(
                    "taskId",
                    taskId,
                    "name",
                    "Item retrieval",
                    "action",
                    "Submit items",
                    "requirementLogic",
                    "ALL",
                    "totalConditions",
                    7,
                    "conditions",
                    conditions,
                    "nextConditionOffset",
                    offset == 0 ? 4 : null,
                    "notes",
                    Arrays.asList("Items are consumed", "Exact NBT required"));
        }

        public boolean icon(String session, int dimension, UUID quest, int x, int y, int size) {
            if (!current) throw new IllegalArgumentException("Old session");
            icons++;
            iconQuest = quest;
            return true;
        }
    }

    private static String payload() {
        return new Gson()
                .toJson(
                        map(
                                "session",
                                "test-session",
                                "dimension",
                                0,
                                "questId",
                                FIRST.toString()));
    }

    private static final class Preview implements Surface {
        final List<String> text = new ArrayList<String>();
        final int width, lh;
        int height, items;
        BufferedImage canvas;

        Preview(int width, int lh) {
            this.width = width;
            this.lh = lh;
        }

        public int textWidth(String s) {
            return s.codePointCount(0, s.length()) * (lh == 9 ? 6 : 9);
        }

        public int lineHeight() {
            return lh;
        }

        public void rectangle(int l, int t, int r, int b, int color) {
            assertTrue(l >= 0 && t >= 0 && r <= width && b <= height);
            if (canvas != null) {
                Graphics2D g = canvas.createGraphics();
                g.setColor(new Color(color, true));
                g.fillRect(l, t, r - l, b - t);
                g.dispose();
            }
        }

        public void text(String s, int x, int y, int color) {
            assertTrue(s, x >= 0 && y >= 0 && x + textWidth(s) <= width && y + lh <= height);
            text.add(s);
            if (canvas != null) {
                Graphics2D g = canvas.createGraphics();
                g.setColor(new Color(color, true));
                g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, lh == 9 ? 10 : 15));
                g.drawString(s, x, y + lh - 1);
                g.dispose();
            }
        }

        public boolean item(String id, int meta, int count, int x, int y) {
            assertTrue(x + 16 <= width && y + 16 <= height);
            items++;
            if (canvas != null) rectangle(x, y, x + 16, y + 16, 0xff859cab);
            return true;
        }

        public void clip(int l, int t, int r, int b) {}

        public void unclip() {}

        public String clipboard() {
            return "";
        }

        public void clipboard(String s) {}

        public void openResource(String s) {
            fail("Widget must not execute text links");
        }

        public void closePanel() {}

        public void ordinaryChat() {}

        public boolean custom(int x, int y, int width, int height, Runnable draw) {
            draw.run();
            return true;
        }
    }

    private static final class Card {
        final QuestWidget widget;
        final Preview surface;
        final Map<String, Runnable> actions = new HashMap<String, Runnable>();

        Card(Backend backend, int width, int lh) {
            widget = QuestWidget.parse(payload(), backend);
            surface = new Preview(width, lh);
        }

        void render() {
            actions.clear();
            surface.text.clear();
            surface.height = widget.height(surface.width, surface.lh, surface::textWidth);
            surface.canvas =
                    new BufferedImage(surface.width, surface.height, BufferedImage.TYPE_INT_ARGB);
            widget.render(
                    new WidgetCanvas(
                            surface,
                            0,
                            0,
                            surface.width,
                            surface.height,
                            (id, label, x, y, w, h, action) -> {
                                assertTrue(
                                        x >= 0
                                                && y >= 0
                                                && x + w <= surface.width
                                                && y + h <= surface.height);
                                actions.put(id, action);
                            }));
        }

        void click(String id) {
            assertNotNull(id, actions.get(id));
            actions.get(id).run();
        }

        void preview(String name) throws Exception {
            File directory = new File("build/previews");
            directory.mkdirs();
            ImageIO.write(surface.canvas, "png", new File(directory, name + ".png"));
        }
    }

    @Test
    public void strictPayloadParsingHasNoGameReadsOrActions() {
        Backend backend = new Backend();
        assertNotNull(QuestWidget.parse(payload(), backend));
        assertEquals(0, backend.reads);
        assertEquals(0, backend.opens);
        for (String json :
                Arrays.asList(
                        "",
                        "[]",
                        "null",
                        "{",
                        "{}",
                        payload().replace("test-session", ""),
                        payload().replace("\"dimension\":0", "\"dimension\":null"),
                        payload().replace("\"dimension\":0", "\"dimension\":0.5"),
                        payload().replace(FIRST.toString(), "1-1-1-1-1"),
                        payload().replace("}", ",\"command\":\"/op player\"}")))
            assertNull(json, QuestWidget.parse(json, backend));
        assertEquals(0, backend.reads);
    }

    @Test
    public void renderingNeverOpensBookAndAllTabsFitAtDifferentSizes() throws Exception {
        for (int width : new int[] {260, 300, 600})
            for (int lh : new int[] {9, 16}) {
                Backend backend = new Backend();
                Card card = new Card(backend, width, lh);
                card.render();
                if (width == 600 && lh == 9) card.preview("tasks");
                for (String section :
                        new String[] {"info", "tasks", "rewards", "before", "after", "info"}) {
                    card.click("tab-" + section);
                    card.render();
                    assertEquals(section, backend.lastSection);
                    assertEquals(0, backend.opens);
                    if (width == 600 && lh == 9) card.preview(section);
                }
                card.click("open");
                assertEquals(1, backend.opens);
                assertEquals(FIRST, backend.lastQuest);
                assertTrue(backend.icons > 0);
            }
    }

    @Test
    public void paginationAndRelationshipNavigationPreserveOriginalQuestAndScope() {
        Backend backend = new Backend();
        Card card = new Card(backend, 300, 9);
        card.render();
        card.click("tab-info");
        card.render();
        card.click("next");
        card.render();
        assertTrue(card.actions.containsKey("previous"));
        card.click("tab-tasks");
        card.render();
        assertEquals(0, backend.lastOffset);
        card.click("next");
        card.render();
        assertEquals(4, backend.lastOffset);
        card.click("previous");
        card.render();
        assertEquals(0, backend.lastOffset);
        card.click("tab-after");
        card.render();
        assertFalse(card.actions.containsKey("quest-1"));
        card.click("quest-0");
        card.render();
        assertEquals(SECOND, backend.lastQuest);
        assertEquals("tasks", backend.lastSection);
        card.click("home");
        card.render();
        assertEquals(FIRST, backend.lastQuest);
        assertEquals(0, backend.opens);
    }

    @Test
    public void conditionsAreDefaultAndEveryRequirementIsAccessibleWithoutOpeningBook()
            throws Exception {
        for (int width : new int[] {260, 300, 600})
            for (int lh : new int[] {9, 16}) {
                Backend backend = new Backend();
                Card card = new Card(backend, width, lh);
                card.render();
                assertEquals("tasks", backend.lastSection);
                assertTrue(card.surface.text.stream().anyMatch(t -> t.contains("Iron")));
                card.click("task-0");
                card.render();
                assertEquals(1, backend.taskReads);
                if (width == 600 && lh == 9) card.preview("conditions");
                assertTrue(card.surface.text.stream().anyMatch(t -> t.contains("12 / 64")));
                assertTrue(
                        backend.hoverText.stream().anyMatch(t -> t.contains("Exact NBT required")));
                card.click("next");
                card.render();
                assertTrue(card.surface.text.stream().anyMatch(t -> t.contains("Requirement 4")));
                card.click("previous");
                card.render();
                backend.progress = 64;
                card.render();
                assertTrue(card.surface.text.stream().anyMatch(t -> t.contains("64 / 64")));
                card.click("back-task");
                card.render();
                assertTrue(card.actions.containsKey("task-0"));
                assertEquals(0, backend.opens);
            }
    }

    @Test
    public void nonAndCompletionLogicIsReadableAndNeverReplacedByAPercentage() {
        for (String logic : new String[] {"AND", "OR", "NAND", "NOR", "XOR", "XNOR"}) {
            Backend backend = new Backend();
            backend.taskLogic = logic;
            Card card = new Card(backend, 900, 9);
            card.render();
            assertTrue(
                    logic, card.surface.text.stream().anyMatch(t -> t.contains("(" + logic + ")")));
            assertFalse(card.surface.text.stream().anyMatch(t -> t.contains("%")));
        }
    }

    @Test
    public void staleButtonsCannotOpenOrRebindAndNarrowCardsHaveFallback() {
        Backend backend = new Backend();
        Card card = new Card(backend, 300, 9);
        card.render();
        Runnable oldOpen = card.actions.get("open");
        card.click("tab-before");
        card.render();
        Runnable oldGo = card.actions.get("quest-0");
        backend.current = false;
        oldOpen.run();
        oldGo.run();
        card.render();
        assertEquals(0, backend.opens);
        assertEquals(FIRST, backend.lastQuest);
        assertTrue(card.actions.isEmpty());
        assertTrue(card.surface.text.stream().anyMatch(t -> t.contains("fresh card")));
        QuestWidget narrow = QuestWidget.parse(payload(), backend);
        assertEquals(0, narrow.height(200));
        assertTrue(narrow.plainText().contains(FIRST.toString()));
    }

    @Test
    public void harnessRegistryParsesCardsBesideProseAndModHasExactDependencies() {
        WidgetRegistry registry = new WidgetRegistry();
        registry.register(
                "minecraftagenttools:quest-v1", QuestWidget.instructions(), QuestWidget::parse);
        WidgetRegistry.Message parsed =
                registry.parse(
                        "Do this next.\n```minecraftagenttools:quest-v1\n"
                                + payload()
                                + "\n```\nThen build a machine.");
        assertEquals(1, parsed.widgets.size());
        assertTrue(parsed.prose.contains("Do this next."));
        assertEquals(
                "required-after:minecraftharness;required-after:minecraftagenttoolsbetterquesting",
                BetterQuestingHarnessMod.class.getAnnotation(Mod.class).dependencies());
    }
}
