package dev.minecraftagenttools.schematica.contract;

import static dev.minecraftagenttools.protocol.data.Data.*;

import java.util.*;

/** Owned by the client thread. Session changes revoke IDs, callbacks and all plans. */
public final class Plans {
    public static final int MAX_PLANS = 8, MAX_TOTAL_VOLUME = 131072;
    private Object world;
    private int dimension;
    private String session;
    private final Map<String, Plan> plans = new LinkedHashMap<String, Plan>();

    public boolean update(Object world, int dimension) {
        if (this.world == world && this.dimension == dimension) return false;
        this.world = world;
        this.dimension = dimension;
        session = world == null ? null : UUID.randomUUID().toString();
        plans.clear();
        return true;
    }

    public String session() {
        return session;
    }

    public int dimension() {
        return dimension;
    }

    public void require(String session, int dimension) {
        if (this.session == null || !this.session.equals(session) || this.dimension != dimension)
            throw new IllegalArgumentException("Stale Schematica scope; call schematic_status");
    }

    public Collection<Plan> all() {
        return Collections.unmodifiableCollection(plans.values());
    }

    public Plan get(String id) {
        Plan p = plans.get(id);
        if (p == null) throw new IllegalArgumentException("Unknown plan");
        return p;
    }

    public void checkPut(String id, int revision, Layout layout) {
        Plan old = id == null ? null : get(id);
        if (old != null && old.revision != revision)
            throw new IllegalArgumentException("Stale plan revision");
        if (old == null && plans.size() >= MAX_PLANS)
            throw new IllegalArgumentException("Plan limit reached");
        int volume = layout.volume();
        for (Plan p : plans.values()) if (p != old) volume += p.layout.volume();
        if (volume > MAX_TOTAL_VOLUME)
            throw new IllegalArgumentException("Total plan volume limit reached");
    }

    public Plan put(String id, int revision, String name, Layout layout, int x, int y, int z) {
        checkPut(id, revision, layout);
        Plan p =
                new Plan(
                        id == null ? UUID.randomUUID().toString() : id,
                        revision + 1,
                        name,
                        layout,
                        x,
                        y,
                        z);
        Plan old = id == null ? null : plans.get(id);
        if (old != null) p.visible = old.visible;
        plans.put(p.id, p);
        return p;
    }

    public void remove(String id) {
        get(id);
        plans.remove(id);
    }

    public static final class Plan {
        public final String id, name;
        public final int revision;
        public final Layout layout;
        public int x, y, z, layer = -1;
        public boolean visible = true;

        Plan(String id, int revision, String name, Layout layout, int x, int y, int z) {
            this.id = id;
            this.revision = revision;
            this.name = name;
            this.layout = layout;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public Map<String, Object> summary() {
            return map(
                    "planId",
                    id,
                    "revision",
                    revision,
                    "name",
                    name,
                    "x",
                    x,
                    "y",
                    y,
                    "z",
                    z,
                    "visible",
                    visible,
                    "layer",
                    layer,
                    "width",
                    layout.width,
                    "height",
                    layout.height,
                    "length",
                    layout.length);
        }
    }
}
