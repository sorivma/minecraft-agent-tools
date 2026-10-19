package dev.minecraftagenttools.protocol.world;

import static dev.minecraftagenttools.protocol.data.Data.map;

import java.util.*;

/** Bounded, per-query history. All calls belong to the game thread. */
public final class Snapshots {
    private final LinkedHashMap<String, Entry> history = new LinkedHashMap<String, Entry>();
    private final int capacity;

    public Snapshots(int capacity) {
        this.capacity = capacity;
    }

    public Map<String, Object> compare(String scope, String cursor, Map<String, Object> current) {
        Entry previous = cursor == null ? null : history.get(cursor);
        if (cursor != null && (previous == null || !previous.scope.equals(scope)))
            throw new IllegalArgumentException(
                    "CURSOR_EXPIRED_OR_SCOPE_CHANGED: request a fresh baseline");
        Map<String, Object> changed = new LinkedHashMap<String, Object>();
        List<String> removed = new ArrayList<String>();
        for (Map.Entry<String, Object> e : current.entrySet())
            if (previous == null || !Objects.equals(previous.values.get(e.getKey()), e.getValue()))
                changed.put(e.getKey(), e.getValue());
        if (previous != null)
            for (String key : previous.values.keySet())
                if (!current.containsKey(key)) removed.add(key);
        String next = UUID.randomUUID().toString();
        history.put(next, new Entry(scope, new LinkedHashMap<String, Object>(current)));
        while (history.size() > capacity) history.remove(history.keySet().iterator().next());
        return map(
                "cursor",
                next,
                "baseline",
                previous == null,
                "changed",
                changed,
                "removedFromObservation",
                removed,
                "semantics",
                "sampled differences, not an event log; removal may mean outside scope or unloaded");
    }

    private static final class Entry {
        final String scope;
        final Map<String, Object> values;

        Entry(String scope, Map<String, Object> values) {
            this.scope = scope;
            this.values = values;
        }
    }
}
