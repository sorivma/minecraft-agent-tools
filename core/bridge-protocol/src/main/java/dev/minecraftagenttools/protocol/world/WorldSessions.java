package dev.minecraftagenttools.protocol.world;

import java.util.*;

/** Server-thread identity registry; a reloaded dimension always gets a new session. */
public final class WorldSessions<W> {
    private final Map<Integer, W> worlds = new HashMap<Integer, W>();
    private final Map<Integer, String> sessions = new HashMap<Integer, String>();

    public void refresh(Map<Integer, W> active) {
        for (Map.Entry<Integer, W> entry : active.entrySet()) {
            if (worlds.get(entry.getKey()) != entry.getValue()) {
                worlds.put(entry.getKey(), entry.getValue());
                sessions.put(entry.getKey(), UUID.randomUUID().toString());
            }
        }
        worlds.keySet().retainAll(active.keySet());
        sessions.keySet().retainAll(active.keySet());
    }

    public W world(int dimension) {
        return worlds.get(dimension);
    }

    public String session(int dimension) {
        return sessions.get(dimension);
    }

    public Set<Integer> dimensions() {
        return new TreeSet<Integer>(worlds.keySet());
    }

    public W require(int dimension, String session) {
        if (!worlds.containsKey(dimension) || !Objects.equals(sessions.get(dimension), session))
            throw new IllegalArgumentException("STALE_SESSION_OR_UNLOADED_DIMENSION");
        return worlds.get(dimension);
    }
}
