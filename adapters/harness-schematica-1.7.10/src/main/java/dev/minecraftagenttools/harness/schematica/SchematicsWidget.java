package dev.minecraftagenttools.harness.schematica;

import static dev.minecraftagenttools.protocol.data.Data.*;

import com.google.gson.Gson;
import dev.minecraftagenttools.forge.schematica.SchematicClient;
import dev.minecraftagenttools.schematica.contract.Layout;
import dev.minecraftharness.chat.widget.*;
import java.util.*;

/** Optional Harness extension; text supplies only a scope, never executable actions. */
public final class SchematicsWidget implements Widget {
    private final String session;
    private final int dimension;
    private String error = "";

    private SchematicsWidget(String session, int dimension) {
        this.session = session;
        this.dimension = dimension;
    }

    public static String instructions() {
        return "Live visibility controls for agent-authored ghost plans. Use fence minecraftagenttools:schematics-v1 "
                + "with JSON {\"session\":\"value from schematic_status\",\"dimension\":0}. "
                + "Use the exact Schematica scope, not a Minecraft/NEI/GT scope. The panel lists up to eight current plans "
                + "and offers Show/Hide buttons. It never creates, imports or edits a plan by parsing a message. "
                + "Only explicit user clicks toggle visibility; old-world controls become inactive. "
                + "Create the plans with MCP before displaying this widget. Explain the plan's construction steps in prose.";
    }

    @SuppressWarnings("unchecked")
    public static Widget parse(String payload) {
        if (payload.length() > 512) return null;
        Object decoded = new Gson().fromJson(payload, Object.class);
        if (!(decoded instanceof Map)) return null;
        Map<String, Object> data = (Map<String, Object>) decoded;
        Layout.keys(data, "session", "dimension");
        return new SchematicsWidget(
                Layout.text(data, "session", 128),
                Layout.requiredInt(data, "dimension", Integer.MIN_VALUE, Integer.MAX_VALUE));
    }

    public String title() {
        return "Construction plans";
    }

    public String plainText() {
        return "Live Show/Hide controls for agent ghost plans in Schematica session "
                + session
                + ", dimension "
                + dimension
                + ". Stale-world controls are inactive.";
    }

    public boolean includesText() {
        return true;
    }

    public int height(int width) {
        return 210;
    }

    @SuppressWarnings("unchecked")
    public void render(WidgetCanvas canvas) {
        SchematicClient client = SchematicClient.active;
        if (client == null) {
            canvas.text("Schematica adapter is unavailable", 0, 0, 0xffffac92);
            return;
        }
        Map<String, Object> data = client.snapshot(session, dimension);
        if (!Boolean.TRUE.equals(data.get("current"))) {
            canvas.text("Previous world or dimension; request new controls", 0, 0, 0xffb2bac2);
            return;
        }
        List<Map<String, Object>> rows = (List<Map<String, Object>>) data.get("plans");
        canvas.text(
                rows.isEmpty() ? "No agent plans yet" : "Ghost plans - click Show / Hide",
                0,
                0,
                0xffb2bac2);
        int y = 18;
        for (Map<String, Object> row : rows) {
            String id = string(row, "planId");
            int revision = ((Number) row.get("revision")).intValue();
            boolean visible = Boolean.TRUE.equals(row.get("visible"));
            String name = string(row, "name");
            if (row.get("renderError") != null) name = "! " + name;
            int nameWidth = Math.max(0, canvas.width() - 76);
            while (!name.isEmpty() && canvas.textWidth(name) > nameWidth)
                name = name.substring(0, name.length() - 1);
            canvas.text(name, 0, y + 4, visible ? 0xffeeeeee : 0xff999999);
            canvas.button(
                    id,
                    visible ? "Hide" : "Show",
                    Math.max(0, canvas.width() - 68),
                    y,
                    68,
                    19,
                    () -> {
                        try {
                            client.visibility(session, dimension, id, revision, !visible);
                            error = "";
                        } catch (Exception e) {
                            error = "Plan changed; refresh controls";
                        }
                    });
            y += 22;
        }
        if (!error.isEmpty()) canvas.text(error, 0, 196, 0xffffac92);
    }
}
