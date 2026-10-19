package dev.minecraftagenttools.protocol.world;

import java.util.*;
import java.util.function.Predicate;

/** Game-thread owned, bounded temporary state. Time is supplied by a monotonic clock. */
public final class MarkerStore<T> {
    public static final class Entry<T> {
        public final String id, scope;
        public final T value;
        public final long expires;

        Entry(String scope, T value, long expires) {
            this.id = UUID.randomUUID().toString();
            this.scope = scope;
            this.value = value;
            this.expires = expires;
        }
    }

    private final Map<String, Entry<T>> entries = new LinkedHashMap<String, Entry<T>>();

    public Entry<T> add(String scope, T value, long now, int seconds) {
        if (seconds < 1 || seconds > 120)
            throw new IllegalArgumentException("Invalid marker lifetime");
        prune(now, v -> true);
        if (entries.size() >= 256 || list(scope).size() >= 8)
            throw new IllegalStateException("MARKER_LIMIT; remove existing markers first");
        Entry<T> entry = new Entry<T>(scope, value, now + seconds * 1000L);
        entries.put(entry.id, entry);
        return entry;
    }

    public void prune(long now, Predicate<T> valid) {
        entries.values().removeIf(e -> now >= e.expires || !valid.test(e.value));
    }

    public List<Entry<T>> list(String scope) {
        List<Entry<T>> result = new ArrayList<Entry<T>>();
        for (Entry<T> e : entries.values())
            if (scope == null || scope.equals(e.scope)) result.add(e);
        return result;
    }

    public boolean remove(String scope, String id) {
        Entry<T> e = entries.get(id);
        return e != null && e.scope.equals(scope) && entries.remove(id) != null;
    }

    public int clear(String scope) {
        int before = entries.size();
        entries.values().removeIf(e -> e.scope.equals(scope));
        return before - entries.size();
    }
}
