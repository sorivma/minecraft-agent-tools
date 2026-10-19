package dev.minecraftagenttools.nei.contract;

import java.util.UUID;

/** Identity-based epoch for a client world and its asynchronously replaced NEI catalog. */
public final class NeiScope {
    private Object world, catalog;
    private int dimension;
    private boolean ready;
    private String session;

    public boolean update(Object world, int dimension, Object catalog, boolean ready) {
        if (this.world == world
                && this.dimension == dimension
                && this.catalog == catalog
                && this.ready == ready) return false;
        this.world = world;
        this.dimension = dimension;
        this.catalog = catalog;
        this.ready = ready;
        session = world == null ? null : UUID.randomUUID().toString();
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
            throw new IllegalArgumentException("Stale NEI scope; call nei_status again");
        if (!ready) throw new IllegalStateException("NEI catalog is not ready");
    }
}
