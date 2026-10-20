package dev.minecraftagenttools.forge.schematica;

import static dev.minecraftagenttools.protocol.data.Data.*;

import com.google.gson.Gson;
import dev.minecraftagenttools.schematica.contract.*;
import java.util.*;
import java.util.function.Supplier;

/** Client-thread manager and typed optional integration entry point. No Harness dependency. */
public final class SchematicClient {
    public interface Ghost {
        void display(int x, int y, int z, boolean visible, int layer) throws Exception;

        void render() throws Exception;

        void dirty() throws Exception;

        void close() throws Exception;

        byte[] export(Plans.Plan plan) throws Exception;
    }

    public interface Factory {
        Ghost build(Layout layout, int x, int y, int z) throws Exception;
    }

    public static final class State {
        final Object world;
        final int dimension;

        public State(Object world, int dimension) {
            this.world = world;
            this.dimension = dimension;
        }
    }

    public static SchematicClient active;
    private final Plans plans = new Plans();
    private final Map<String, Ghost> ghosts = new LinkedHashMap<String, Ghost>();
    private final Map<String, String> renderErrors = new HashMap<String, String>();
    private final Supplier<State> state;
    private final Factory factory;
    private final String unavailable;
    private int ticks;

    public SchematicClient(Supplier<State> state, Factory factory, String unavailable) {
        this.state = state;
        this.factory = factory;
        this.unavailable = unavailable;
    }

    public void tick() {
        sync();
        if (++ticks % 20 == 0)
            for (Map.Entry<String, Ghost> entry : ghosts.entrySet()) {
                try {
                    entry.getValue().dirty();
                } catch (Exception e) {
                    renderErrors.put(entry.getKey(), clip(e.toString()));
                }
            }
    }

    private void sync() {
        State current = state.get();
        if (plans.update(current.world, current.dimension)) {
            for (Ghost g : ghosts.values()) dispose(g);
            ghosts.clear();
            renderErrors.clear();
        }
    }

    public Map<String, Object> call(String tool, Map<String, Object> args) throws Exception {
        sync();
        SchematicaTools.validate(tool, args);
        if (tool.equals("schematic_status"))
            return map(
                    "session",
                    plans.session(),
                    "dimension",
                    plans.session() == null ? null : plans.dimension(),
                    "ready",
                    factory != null && plans.session() != null,
                    "apiBaseline",
                    "Schematica GTNH 1.12.6",
                    "unavailable",
                    unavailable,
                    "maxPlans",
                    Plans.MAX_PLANS,
                    "maxPlanVolume",
                    Layout.MAX_VOLUME,
                    "maxTotalVolume",
                    Plans.MAX_TOTAL_VOLUME,
                    "capabilities",
                    Arrays.asList(
                            "authorBoxes",
                            "gregtechMachines",
                            "gregtechPipes",
                            "modTileNbt",
                            "simultaneousPlans",
                            "visibility",
                            "layers",
                            "export"),
                    "lifetime",
                    "Plans and widget controls expire on world/dimension changes; export to retain a copy");
        plans.require(
                string(args, "session"),
                integer(args, "dimension", 0, Integer.MIN_VALUE, Integer.MAX_VALUE));
        if (factory == null)
            throw new IllegalStateException("Schematica unavailable: " + unavailable);
        if (tool.equals("schematic_list"))
            return snapshot(string(args, "session"), plans.dimension());
        Plans.Plan plan;
        if (tool.equals("schematic_put")) {
            Layout layout = new Layout(args);
            String id = args.containsKey("planId") ? string(args, "planId") : null;
            int revision = integer(args, "revision", 0, 0, Integer.MAX_VALUE - 1);
            int x = integer(args, "x", 0, -29999872, 29999872),
                    y = integer(args, "y", 0, 0, 255),
                    z = integer(args, "z", 0, -29999872, 29999872);
            plans.checkPut(id, revision, layout);
            // Resolve and initialize off-screen first. A bad palette leaves the old plan intact.
            Ghost next = factory.build(layout, x, y, z);
            Plans.Plan old = id == null ? null : plans.get(id);
            try {
                next.display(x, y, z, old == null || old.visible, -1);
            } catch (Exception e) {
                dispose(next);
                throw e;
            }
            plan = plans.put(id, revision, string(args, "name"), layout, x, y, z);
            Ghost previous = ghosts.put(plan.id, next);
            renderErrors.remove(plan.id);
            if (previous != null) dispose(previous);
        } else {
            plan = plans.get(string(args, "planId"));
            if (args.containsKey("revision")
                    && plan.revision != integer(args, "revision", 0, 1, Integer.MAX_VALUE - 1))
                throw new IllegalArgumentException("Stale plan revision; refresh schematic_list");
            if (tool.equals("schematic_display")) {
                int x = integer(args, "x", plan.x, -29999872, 29999872),
                        y = integer(args, "y", plan.y, 0, 255),
                        z = integer(args, "z", plan.z, -29999872, 29999872);
                int layer = integer(args, "layer", plan.layer, -1, plan.layout.height - 1);
                boolean visible =
                        args.containsKey("visible") ? (Boolean) args.get("visible") : plan.visible;
                if (y + plan.layout.height > 256)
                    throw new IllegalArgumentException("Plan exceeds world height");
                ghosts.get(plan.id).display(x, y, z, visible, layer);
                plan.x = x;
                plan.y = y;
                plan.z = z;
                plan.layer = layer;
                plan.visible = visible;
                renderErrors.remove(plan.id);
            }
            if (tool.equals("schematic_remove")) {
                dispose(ghosts.remove(plan.id));
                plans.remove(plan.id);
                renderErrors.remove(plan.id);
                return map(
                        "session",
                        plans.session(),
                        "dimension",
                        plans.dimension(),
                        "removed",
                        plan.id);
            }
            if (tool.equals("schematic_export")) {
                Map<String, Object> source = plan.summary();
                source.putAll(plan.layout.data());
                return map(
                        "session",
                        plans.session(),
                        "dimension",
                        plans.dimension(),
                        "planId",
                        plan.id,
                        "schematicBase64",
                        Base64.getEncoder().encodeToString(ghosts.get(plan.id).export(plan)),
                        "layoutJson",
                        new Gson().toJson(source));
            }
        }
        Map<String, Object> result = plan.summary();
        if (tool.equals("schematic_get")) result.putAll(plan.layout.data());
        return map("session", plans.session(), "dimension", plans.dimension(), "result", result);
    }

    /**
     * Read-only, detached snapshot. Old widgets are disabled rather than rebound to a new world.
     */
    public Map<String, Object> snapshot(String session, int dimension) {
        sync();
        boolean current =
                Objects.equals(session, plans.session())
                        && plans.session() != null
                        && dimension == plans.dimension();
        List<Object> rows = new ArrayList<Object>();
        if (current)
            for (Plans.Plan p : plans.all()) {
                Map<String, Object> row = p.summary();
                row.put("renderError", renderErrors.get(p.id));
                rows.add(row);
            }
        return map(
                "session",
                plans.session(),
                "dimension",
                plans.dimension(),
                "current",
                current,
                "plans",
                rows);
    }

    /** Call only from an explicit user action, never a parser or renderer. */
    public void visibility(String session, int dimension, String id, int revision, boolean visible)
            throws Exception {
        call(
                "schematic_display",
                map(
                        "session",
                        session,
                        "dimension",
                        dimension,
                        "planId",
                        id,
                        "revision",
                        revision,
                        "visible",
                        visible));
    }

    public void render() {
        sync();
        for (Plans.Plan p : plans.all())
            if (p.visible && !renderErrors.containsKey(p.id)) {
                try {
                    ghosts.get(p.id).render();
                } catch (Exception | LinkageError e) {
                    renderErrors.put(p.id, clip(e.toString()));
                }
            }
    }

    private static String clip(String s) {
        return s.substring(0, Math.min(512, s.length()));
    }

    private static void dispose(Ghost g) {
        if (g == null) return;
        try {
            g.close();
        } catch (Exception e) {
            System.err.println("MAT schematic cleanup: " + clip(e.toString()));
        }
    }
}
