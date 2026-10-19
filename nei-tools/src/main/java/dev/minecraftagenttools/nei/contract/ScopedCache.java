package dev.minecraftagenttools.nei.contract;

import java.util.*;
import java.util.function.LongSupplier;

/** Game-thread-owned bounded handles. Scope changes revoke every previously issued handle. */
public final class ScopedCache<T> {
    private final int capacity;
    private final long ttlNanos;
    private final LongSupplier clock;
    private final LinkedHashMap<String, Entry<T>> entries = new LinkedHashMap<String, Entry<T>>();

    public ScopedCache(int capacity, long ttlNanos, LongSupplier clock) {
        this.capacity = capacity;
        this.ttlNanos = ttlNanos;
        this.clock = clock;
    }

    public String put(T value) {
        purge();
        while (entries.size() >= capacity) entries.remove(entries.keySet().iterator().next());
        String id = UUID.randomUUID().toString();
        entries.put(id, new Entry<T>(value, clock.getAsLong()));
        return id;
    }

    public T get(String id) {
        purge();
        Entry<T> entry = entries.get(id);
        if (entry == null)
            throw new IllegalArgumentException(
                    "Unknown or expired NEI handle; rediscover items/recipes");
        return entry.value;
    }

    public void clear() {
        entries.clear();
    }

    private void purge() {
        long now = clock.getAsLong();
        entries.values().removeIf(e -> now - e.created >= ttlNanos);
    }

    private static final class Entry<T> {
        final T value;
        final long created;

        Entry(T value, long created) {
            this.value = value;
            this.created = created;
        }
    }
}
