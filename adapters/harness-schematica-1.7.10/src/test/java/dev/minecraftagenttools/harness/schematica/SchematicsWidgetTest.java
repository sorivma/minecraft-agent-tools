package dev.minecraftagenttools.harness.schematica;

import static dev.minecraftagenttools.protocol.data.Data.*;
import static org.junit.Assert.*;

import dev.minecraftagenttools.forge.schematica.SchematicClient;
import dev.minecraftagenttools.schematica.contract.Plans;
import dev.minecraftharness.chat.Surface;
import dev.minecraftharness.chat.widget.*;
import java.util.*;
import org.junit.Test;

public class SchematicsWidgetTest {
    private static final class Preview implements Surface {
        final List<String> text = new ArrayList<String>();

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

    @Test
    public void textAndRenderingNeverTogglePlansAndCallbacksExpireWithScope() throws Exception {
        Object[] world = {new Object()};
        List<Ghost> ghosts = new ArrayList<Ghost>();
        SchematicClient client =
                new SchematicClient(
                        () -> new SchematicClient.State(world[0], 0),
                        (l, x, y, z) -> {
                            Ghost g = new Ghost();
                            ghosts.add(g);
                            return g;
                        },
                        null);
        SchematicClient.active = client;
        try {
            String session = (String) client.call("schematic_status", map()).get("session");
            for (String name : Arrays.asList("Machines", "Aisle"))
                client.call(
                        "schematic_put",
                        map(
                                "session",
                                session,
                                "dimension",
                                0,
                                "name",
                                name,
                                "x",
                                0,
                                "y",
                                64,
                                "z",
                                0,
                                "width",
                                2,
                                "height",
                                1,
                                "length",
                                2,
                                "palette",
                                Arrays.asList(map("block", "minecraft:stone")),
                                "boxes",
                                Collections.emptyList()));
            Widget widget =
                    SchematicsWidget.parse("{\"session\":\"" + session + "\",\"dimension\":0}");
            assertNotNull(widget);
            List<Runnable> actions = new ArrayList<Runnable>();
            Preview surface = new Preview();
            WidgetCanvas canvas =
                    new WidgetCanvas(
                            surface,
                            0,
                            0,
                            300,
                            210,
                            (id, label, x, y, w, h, callback) -> actions.add(callback));
            widget.render(canvas);
            assertEquals(2, actions.size());
            assertTrue(ghosts.get(0).visible);
            assertTrue(ghosts.get(1).visible);
            actions.get(0).run();
            assertFalse(ghosts.get(0).visible);
            assertTrue(ghosts.get(1).visible);
            Runnable old = actions.get(1);
            world[0] = new Object();
            actions.clear();
            surface.text.clear();
            widget.render(canvas);
            assertTrue(actions.isEmpty());
            assertTrue(surface.text.get(0).contains("Previous world"));
            old.run();
            assertTrue(ghosts.get(1).visible);
        } finally {
            SchematicClient.active = null;
        }
    }
}
