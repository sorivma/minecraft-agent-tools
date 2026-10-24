package dev.minecraftagenttools.harness.schematica;

import static dev.minecraftagenttools.protocol.data.Data.*;

import com.google.gson.Gson;
import dev.minecraftagenttools.forge.schematica.SchematicClient;
import dev.minecraftagenttools.schematica.contract.*;
import dev.minecraftharness.chat.widget.*;
import java.util.*;

/** One live plan per card. Parsing and rendering never change ghost visibility. */
public final class SchematicWidget implements Widget {
    private String session, world;
    private final int dimension;
    private final String persistentId;
    private Integer previewError;
    private int legendPage;
    private String name = "Construction plan", error = "";

    private SchematicWidget(String session, int dimension, String world, String persistentId) {
        this.session = session;
        this.dimension = dimension;
        this.world = world;
        this.persistentId = persistentId;
    }

    public static String instructions() {
        return "Show one live construction plan per widget, next to the explanation of that plan. "
                + "Create plans using schematic_put first, then emit a separate minecraftagenttools:schematic-v1 fence for EACH plan. "
                + "JSON: {\"session\":\"from schematic_status\",\"dimension\":0,\"world\":\"from schematic_status\",\"persistentId\":\"from that plan's schematic_put result or schematic_list entry\"}. Omit world only if unavailable. "
                + "Use the exact Schematica scope, not Minecraft/NEI/GT scope. persistentId identifies the blueprint across restoration; planId is for session-scoped tool commands. "
                + "Each card shows only its plan's textured isometric preview, group path, dimensions, position, Show/Hide and required materials. "
                + "Materials have icon/count legends with localized hover names and independent pages. Counts cover the complete blueprint, not missing blocks or crafting ingredients. "
                + "Text never creates or edits plans. Only explicit clicks toggle ghosts; old-world controls expire. "
                + "After rejoining the same world/dimension, Restore saved plans explicitly restores the saved set and reconnects this card only to its own blueprint. Other old cards require their own explicit reconnect. "
                + "Deleted plans stay unavailable; never substitute another plan by name or position. Explain construction steps and conceptual placeholders in prose.";
    }

    public static Widget parse(String payload) {
        return parse(payload, false);
    }

    /** Old global cards remain useful for restoration, but never choose a plan implicitly. */
    public static Widget parseLegacy(String payload) {
        return parse(payload, true);
    }

    @SuppressWarnings("unchecked")
    private static Widget parse(String payload, boolean legacy) {
        if (payload == null || payload.length() > 512) return null;
        Object decoded = new Gson().fromJson(payload, Object.class);
        if (!(decoded instanceof Map)) return null;
        Map<String, Object> data = (Map<String, Object>) decoded;
        Layout.keys(data, "session", "dimension", "world", "persistentId");
        String world = data.containsKey("world") ? Layout.text(data, "world", 64) : null;
        if (world != null && !world.matches("[a-f0-9]{64}")) return null;
        String id =
                legacy && !data.containsKey("persistentId")
                        ? null
                        : Plans.requirePersistentId(Layout.text(data, "persistentId", 36));
        return new SchematicWidget(
                Layout.text(data, "session", 128),
                Layout.requiredInt(data, "dimension", Integer.MIN_VALUE, Integer.MAX_VALUE),
                world,
                id);
    }

    public String title() {
        return persistentId == null ? "Saved construction plans" : name;
    }

    public String plainText() {
        return persistentId == null
                ? "Legacy construction card. Request a separate card for each schematic."
                : "Construction plan "
                        + persistentId
                        + ", Schematica session "
                        + session
                        + ", dimension "
                        + dimension
                        + ". Live preview, Show/Hide and complete blueprint material counts. Stale controls are inactive.";
    }

    public boolean includesText() {
        return true;
    }

    public int height(int width) {
        return width < 220 ? 0 : 224;
    }

    @SuppressWarnings("unchecked")
    public void render(WidgetCanvas c) {
        SchematicClient client = SchematicClient.active;
        if (client == null) {
            c.text("Schematica adapter is unavailable", 0, 0, 0xffffac92);
            return;
        }
        Map<String, Object> data = client.snapshot(session, dimension);
        if (!Boolean.TRUE.equals(data.get("current"))
                || Boolean.TRUE.equals(data.get("restoreRequired"))
                || Boolean.TRUE.equals(data.get("storageLoading"))) {
            renderRecovery(c, client);
            return;
        }
        if (world == null) world = (String) data.get("world");
        if (persistentId == null) {
            c.text(
                    fit(c, "Request a separate card for each schematic", c.width()),
                    0,
                    4,
                    0xffb2bac2);
            return;
        }
        Map<String, Object> plan = null;
        for (Map<String, Object> candidate : (List<Map<String, Object>>) data.get("plans"))
            if (persistentId.equals(candidate.get("persistentId"))) {
                plan = candidate;
                break;
            }
        if (plan == null) {
            c.text("This schematic is no longer available", 0, 4, 0xffb2bac2);
            return;
        }
        name = string(plan, "name");
        boolean visible = Boolean.TRUE.equals(plan.get("visible"));
        c.text(
                fit(c, (plan.get("renderError") == null ? "" : "! ") + name, c.width() - 64),
                0,
                5,
                visible ? 0xffeeeeee : 0xff999999);
        visibilityButton(c, client, plan);
        String group = string(plan, "group");
        c.text(fit(c, group.isEmpty() ? "Construction plan" : group, c.width()), 0, 27, 0xff9cabb7);
        preview(c, client, plan, 0, 44, 96, 82);
        String[] details = {
            plan.get("width") + "x" + plan.get("height") + "x" + plan.get("length"),
            count((List<Map<String, Object>>) plan.get("materials")) + " blocks",
            plan.get("x") + ", " + plan.get("y") + ", " + plan.get("z"),
            visible ? "Shown" : "Hidden"
        };
        for (int i = 0; i < details.length; i++)
            c.text(fit(c, details[i], c.width() - 104), 104, 48 + i * 20, 0xffb2bac2);
        renderLegend(c, plan, 0, 134, c.width());
        c.text(
                fit(
                        c,
                        Boolean.TRUE.equals(data.get("storageSaving"))
                                ? "Saving plans..."
                                : "Whole blueprint / hover materials for names",
                        c.width()),
                0,
                190,
                0xff9cabb7);
        String storageError = (String) data.get("storageError");
        String message = error.isEmpty() ? storageError : error;
        if (message != null && !message.isEmpty())
            c.text(fit(c, message, c.width()), 0, 210, 0xffffac92);
    }

    private void renderRecovery(WidgetCanvas c, SchematicClient client) {
        Map<String, Object> offer = client.recovery(session, dimension, world);
        if (!Boolean.TRUE.equals(offer.get("available"))) {
            c.text(
                    fit(c, "Previous world or dimension; request new controls", c.width()),
                    0,
                    0,
                    0xffb2bac2);
            return;
        }
        boolean loading = Boolean.TRUE.equals(offer.get("loading"));
        String failure = (String) offer.get("error");
        c.text(
                loading
                        ? "Reading saved construction plans..."
                        : offer.get("savedPlans") + " saved construction plans",
                0,
                4,
                0xffd7e4ef);
        c.text(fit(c, "Restore saved set and reconnect this card", c.width()), 0, 24, 0xff9cabb7);
        String nextSession = (String) offer.get("session"), nextWorld = (String) offer.get("world");
        c.button(
                "restore",
                "Restore saved plans",
                0,
                46,
                140,
                21,
                !loading && failure.isEmpty(),
                () -> {
                    try {
                        client.restore(nextSession, dimension, nextWorld);
                        session = nextSession;
                        world = nextWorld;
                        previewError = null;
                        legendPage = 0;
                        error = "";
                    } catch (Exception | LinkageError e) {
                        error = "Cannot restore plans; saved copy kept. " + e.getMessage();
                    }
                });
        if (!failure.isEmpty()) c.text(fit(c, failure, c.width()), 0, 78, 0xffffac92);
        if (!error.isEmpty()) c.text(fit(c, error, c.width()), 0, 96, 0xffffac92);
    }

    private void visibilityButton(
            WidgetCanvas c, SchematicClient client, Map<String, Object> plan) {
        String actionSession = session, id = string(plan, "planId");
        int revision = ((Number) plan.get("revision")).intValue();
        boolean show = !Boolean.TRUE.equals(plan.get("visible"));
        c.button(
                "visibility",
                show ? "Show" : "Hide",
                c.width() - 58,
                0,
                56,
                19,
                () -> {
                    try {
                        client.visibility(actionSession, dimension, id, revision, show);
                        error = "";
                    } catch (Exception e) {
                        error = "Plan changed or display failed; refresh controls";
                    }
                });
    }

    @SuppressWarnings("unchecked")
    private void renderLegend(WidgetCanvas c, Map<String, Object> plan, int x, int y, int width) {
        List<Map<String, Object>> rows = (List<Map<String, Object>>) plan.get("materials");
        int columns = Math.max(1, (width - 24) / 58);
        int capacity = columns * 2;
        int pages = Math.max(1, (rows.size() + capacity - 1) / capacity);
        int current = Math.min(legendPage, pages - 1);
        legendPage = current;
        if (rows.isEmpty()) c.text("No materials", x, y + 4, 0xff9cabb7);
        for (int i = current * capacity; i < Math.min(rows.size(), (current + 1) * capacity); i++) {
            Map<String, Object> row = rows.get(i);
            int slot = i - current * capacity;
            int left = x + (slot % columns) * ((width - 24) / columns);
            int top = y + (slot / columns) * 23;
            c.rectangle(left, top, (width - 24) / columns - 3, 20, 0xff19232d);
            String block = string(row, "block");
            int meta =
                    ((Number) row.get(row.containsKey("machineId") ? "machineId" : "meta"))
                            .intValue();
            if (!c.item(block, meta, 1, left + 2, top + 2))
                c.text("?", left + 6, top + 5, 0xff9cabb7);
            c.text(row.get("count").toString(), left + 21, top + 6, 0xffdceec0);
            c.custom(
                    left,
                    top,
                    (width - 24) / columns - 3,
                    20,
                    (l, t, w, h) -> MaterialTooltip.offer(row, l, t, w, h));
        }
        if (pages > 1) {
            c.button(
                    "legend-prev",
                    "<",
                    x + width - 21,
                    y,
                    20,
                    19,
                    current > 0,
                    () -> legendPage = current - 1);
            c.button(
                    "legend-next",
                    ">",
                    x + width - 21,
                    y + 23,
                    20,
                    19,
                    current + 1 < pages,
                    () -> legendPage = current + 1);
        }
    }

    private void preview(
            WidgetCanvas c,
            SchematicClient client,
            Map<String, Object> row,
            int x,
            int y,
            int w,
            int h) {
        String id = string(row, "planId");
        int revision = ((Number) row.get("revision")).intValue();
        c.rectangle(x, y, w, h, 0xff17212b);
        if (Objects.equals(previewError, revision)) {
            c.text("3D error", x + 2, y + h / 2, 0xffffac92);
            return;
        }
        String previewSession = session;
        boolean supported =
                c.custom(
                        x,
                        y,
                        w,
                        h,
                        (left, top, width, height) -> {
                            try {
                                client.preview(
                                        previewSession,
                                        dimension,
                                        id,
                                        revision,
                                        left,
                                        top,
                                        width,
                                        height);
                            } catch (Exception | LinkageError e) {
                                previewError = revision;
                            }
                        });
        if (!supported) c.text("3D", x + 4, y + h / 2, 0xff9cabb7);
    }

    private static String fit(WidgetCanvas c, String value, int width) {
        while (!value.isEmpty() && c.textWidth(value) > Math.max(0, width))
            value = value.substring(0, value.length() - 1);
        return value;
    }

    private static int count(List<Map<String, Object>> rows) {
        int count = 0;
        for (Map<String, Object> row : rows) count += ((Number) row.get("count")).intValue();
        return count;
    }
}
