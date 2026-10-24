package dev.minecraftagenttools.forge.schematica;

import static dev.minecraftagenttools.protocol.data.Data.*;

import com.google.gson.Gson;
import dev.minecraftagenttools.schematica.contract.*;
import java.util.*;
import java.util.concurrent.Future;
import java.util.function.Supplier;

/** Client-thread manager and typed optional integration entry point. No Harness dependency. */
public final class SchematicClient {
    public interface Ghost {
        void display(int x, int y, int z, boolean visible, int layer) throws Exception;

        void render() throws Exception;

        void dirty() throws Exception;

        void close() throws Exception;

        byte[] export(Plans.Plan plan) throws Exception;

        default void preview(int left, int top, int width, int height) throws Exception {}
    }

    public interface Factory {
        Ghost build(Layout layout, int x, int y, int z) throws Exception;
    }

    public static final class State {
        final Object world;
        final int dimension;
        final String identity;

        public State(Object world, int dimension) {
            this(world, dimension, null);
        }

        public State(Object world, int dimension, String identity) {
            this.world = world;
            this.dimension = dimension;
            this.identity = identity;
        }
    }

    public static SchematicClient active;
    private final Plans plans = new Plans();
    private final Map<String, Ghost> ghosts = new LinkedHashMap<String, Ghost>();
    private final Map<String, String> renderErrors = new HashMap<String, String>();
    private final Supplier<State> state;
    private final Factory factory;
    private final String unavailable;
    private int ticks, persistenceBatch;
    private final PlanStore store;
    private String worldKey, storageError = "";
    private Future<Map<String, Object>> loading;
    private Future<?> writing;
    private List<Map<String, Object>> saved = Collections.emptyList();
    private final Set<String> savedSessions = new LinkedHashSet<String>();
    private boolean restored = true;

    public SchematicClient(Supplier<State> state, Factory factory, String unavailable) {
        this(state, factory, unavailable, null);
    }

    public SchematicClient(
            Supplier<State> state, Factory factory, String unavailable, PlanStore store) {
        this.store = store;
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
            worldKey =
                    current.world == null
                            ? null
                            : PlanStore.key(current.identity, current.dimension);
            saved = Collections.emptyList();
            savedSessions.clear();
            restored = true;
            storageError = "";
            writing = null;
            loading =
                    store != null && worldKey != null
                            ? store.load(worldKey, current.dimension)
                            : null;
        }
        pollStorage();
    }

    @SuppressWarnings("unchecked")
    private void pollStorage() {
        if (loading != null && loading.isDone()) {
            try {
                Map<String, Object> data = loading.get();
                saved = (List<Map<String, Object>>) data.get("plans");
                savedSessions.addAll((List<String>) data.get("sessions"));
                restored = saved.isEmpty();
            } catch (Exception e) {
                storageError = "Cannot read saved plans; original file kept";
            }
            loading = null;
        }
        if (writing != null && writing.isDone()) {
            try {
                writing.get();
                storageError = "";
            } catch (Exception e) {
                storageError = "Cannot save plans; check storage permissions and disk space";
            }
            writing = null;
        }
    }

    private void requireEditableStorage() {
        if (store == null || worldKey == null) return;
        if (loading != null)
            throw new IllegalStateException("Saved plans are loading; retry shortly");
        if (!restored)
            throw new IllegalStateException(
                    "Restore saved plans in the chat widget before editing");
        if (!storageError.isEmpty()) throw new IllegalStateException(storageError);
    }

    private void savePlans() {
        if (store == null || worldKey == null || !restored || persistenceBatch > 0) return;
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        for (Plans.Plan plan : plans.all()) {
            Map<String, Object> row =
                    map(
                            "persistentId",
                            plan.persistentId,
                            "name",
                            plan.name,
                            "group",
                            plan.group,
                            "x",
                            plan.x,
                            "y",
                            plan.y,
                            "z",
                            plan.z,
                            "visible",
                            plan.visible,
                            "layer",
                            plan.layer);
            row.putAll(plan.layout.data());
            rows.add(row);
        }
        savedSessions.add(plans.session());
        while (savedSessions.size() > 4096) savedSessions.remove(savedSessions.iterator().next());
        saved = rows;
        writing = store.save(worldKey, plans.dimension(), savedSessions, rows);
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
                    "world",
                    worldKey,
                    "savedPlans",
                    saved.size(),
                    "restoreRequired",
                    !restored,
                    "storageLoading",
                    loading != null,
                    "storageError",
                    storageError,
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
                            "hierarchy",
                            "materials",
                            "texturedPreview",
                            "visibility",
                            "layers",
                            "export",
                            "persistentPlans"),
                    "lifetime",
                    "Plans are saved locally per world/dimension; restore explicitly in chat after rejoining. Session controls expire.");
        plans.require(
                string(args, "session"),
                integer(args, "dimension", 0, Integer.MIN_VALUE, Integer.MAX_VALUE));
        if (factory == null)
            throw new IllegalStateException("Schematica unavailable: " + unavailable);
        if (tool.equals("schematic_list"))
            return snapshot(string(args, "session"), plans.dimension());
        if (tool.equals("schematic_put")
                || tool.equals("schematic_display")
                || tool.equals("schematic_remove")) requireEditableStorage();
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
            plan =
                    plans.put(
                            id,
                            revision,
                            string(args, "name"),
                            args.containsKey("group") ? (String) args.get("group") : "",
                            layout,
                            x,
                            y,
                            z);
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
                savePlans();
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
        if (tool.equals("schematic_put") || tool.equals("schematic_display")) savePlans();
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
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        List<Map<String, Object>> materials = new ArrayList<Map<String, Object>>();
        if (current)
            for (Plans.Plan p : plans.all()) {
                Map<String, Object> row = p.summary();
                row.put("renderError", renderErrors.get(p.id));
                rows.add(row);
                materials.addAll(p.materials);
            }
        return map(
                "session",
                plans.session(),
                "dimension",
                plans.dimension(),
                "current",
                current,
                "world",
                worldKey,
                "savedPlans",
                saved.size(),
                "restoreRequired",
                !restored,
                "storageLoading",
                loading != null,
                "storageSaving",
                writing != null,
                "storageError",
                storageError,
                "plans",
                rows,
                "hierarchy",
                PlanPresentation.hierarchy(rows),
                "materials",
                PlanPresentation.merge(materials));
    }

    /** A read-only offer. Matching an old card never automatically rebinds its controls. */
    public Map<String, Object> recovery(String oldSession, int dimension, String cardWorld) {
        sync();
        boolean available =
                store != null
                        && worldKey != null
                        && plans.session() != null
                        && dimension == plans.dimension()
                        && (worldKey.equals(cardWorld)
                                || savedSessions.contains(oldSession)
                                || Objects.equals(oldSession, plans.session()));
        return map(
                "available",
                available,
                "session",
                plans.session(),
                "world",
                worldKey,
                "savedPlans",
                saved.size(),
                "loading",
                loading != null,
                "error",
                storageError);
    }

    /** Explicit restoration only. Build every ghost before publishing any restored plan. */
    public void restore(String session, int dimension, String expectedWorld) throws Exception {
        sync();
        plans.require(session, dimension);
        if (store == null || worldKey == null || !worldKey.equals(expectedWorld))
            throw new IllegalArgumentException("Saved plans belong to a different world");
        if (loading != null) throw new IllegalStateException("Saved plans are still loading");
        if (!storageError.isEmpty()) throw new IllegalStateException(storageError);
        if (restored) return; // Another card already loaded them; never duplicate the plans.
        if (factory == null) throw new IllegalStateException("Schematica unavailable");
        Plans staged = new Plans();
        staged.update(new Object(), dimension);
        List<Ghost> built = new ArrayList<Ghost>();
        try {
            for (Map<String, Object> row : saved) {
                Layout layout = new Layout(row);
                Plans.Plan p =
                        staged.restore(
                                string(row, "persistentId"),
                                string(row, "name"),
                                string(row, "group"),
                                layout,
                                Layout.requiredInt(row, "x", -29999872, 29999872),
                                Layout.requiredInt(row, "y", 0, 255),
                                Layout.requiredInt(row, "z", -29999872, 29999872));
                p.visible = (Boolean) row.get("visible");
                p.layer = Layout.requiredInt(row, "layer", -1, layout.height - 1);
                Ghost ghost = factory.build(layout, p.x, p.y, p.z);
                built.add(ghost);
                ghost.display(p.x, p.y, p.z, p.visible, p.layer);
            }
        } catch (Exception | LinkageError e) {
            for (Ghost ghost : built) dispose(ghost);
            throw e;
        }
        int i = 0;
        for (Plans.Plan p : staged.all()) {
            Plans.Plan live =
                    plans.restore(p.persistentId, p.name, p.group, p.layout, p.x, p.y, p.z);
            live.visible = p.visible;
            live.layer = p.layer;
            ghosts.put(live.id, built.get(i++));
        }
        restored = true;
        savePlans();
    }

    /** Render-thread only; a read-only preview never changes ghost visibility or origin. */
    public void preview(
            String session,
            int dimension,
            String id,
            int revision,
            int left,
            int top,
            int width,
            int height)
            throws Exception {
        sync();
        plans.require(session, dimension);
        Plans.Plan plan = plans.get(id);
        if (plan.revision != revision) throw new IllegalArgumentException("Stale preview");
        ghosts.get(id).preview(left, top, width, height);
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

    /** Validate every captured target before applying an explicit subtree/all visibility action. */
    public void visibilityBatch(
            String session, int dimension, List<Map<String, Object>> targets, boolean visible)
            throws Exception {
        sync();
        plans.require(session, dimension);
        for (Map<String, Object> target : targets) {
            Plans.Plan plan = plans.get(string(target, "planId"));
            if (plan.revision != ((Number) target.get("revision")).intValue())
                throw new IllegalArgumentException("Stale plan revision; refresh schematic_list");
        }
        requireEditableStorage();
        persistenceBatch++;
        try {
            for (Map<String, Object> target : targets)
                visibility(
                        session,
                        dimension,
                        string(target, "planId"),
                        ((Number) target.get("revision")).intValue(),
                        visible);
        } finally {
            persistenceBatch--;
            savePlans();
        }
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
