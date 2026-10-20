package dev.minecraftagenttools.gregtech.contract;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.*;

/**
 * Bounded recipe-index snapshots, revoked by world identity, TTL or capacity. Game-thread-owned.
 */
public final class GtCatalogs {
    private final LinkedHashMap<String, Entry> entries = new LinkedHashMap<String, Entry>();
    private final LongSupplier clock;

    public GtCatalogs(LongSupplier clock) {
        this.clock = clock;
    }

    public String create(String map, int dimension, String session, Collection<?> recipes) {
        if (recipes.size() > 200000)
            throw new IllegalStateException("Recipe map exceeds 200000-entry snapshot limit");
        expire();
        while (entries.size() >= 4) entries.remove(entries.keySet().iterator().next());
        String id = UUID.randomUUID().toString();
        entries.put(id, new Entry(map, dimension, session, recipes, clock.getAsLong()));
        return id;
    }

    public Entry require(String id, int dimension, String session) {
        expire();
        Entry entry = entries.get(id);
        if (entry == null || entry.dimension != dimension || !entry.session.equals(session))
            throw new IllegalArgumentException(
                    "Expired or foreign recipe catalog; repeat gt_search_recipes");
        return entry;
    }

    public void refresh(IntFunction<String> worldSession) {
        expire();
        entries.values().removeIf(e -> !Objects.equals(e.session, worldSession.apply(e.dimension)));
    }

    private void expire() {
        long now = clock.getAsLong();
        entries.values().removeIf(e -> now - e.created >= TimeUnit.SECONDS.toNanos(60));
    }

    public static final class Entry {
        public final String map, session;
        public final int dimension;
        public final List<Object> recipes;
        private final long created;

        private Entry(
                String map, int dimension, String session, Collection<?> recipes, long created) {
            this.map = map;
            this.dimension = dimension;
            this.session = session;
            this.recipes = Collections.unmodifiableList(new ArrayList<Object>(recipes));
            this.created = created;
        }
    }
}
