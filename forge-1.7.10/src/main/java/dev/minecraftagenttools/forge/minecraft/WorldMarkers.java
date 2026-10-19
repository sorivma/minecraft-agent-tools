package dev.minecraftagenttools.forge.minecraft;

import static dev.minecraftagenttools.protocol.data.Data.*;

import dev.minecraftagenttools.protocol.world.MarkerStore;
import dev.minecraftagenttools.protocol.world.WorldSessions;
import java.util.*;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.play.server.S2APacketParticles;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.MathHelper;
import net.minecraft.world.WorldServer;

/** All access on the server thread. Vanilla packet delivery is queued by Minecraft/Netty. */
final class WorldMarkers {
    private final MarkerStore<Marker> store = new MarkerStore<Marker>();
    private int ticks;

    private static long now() {
        return System.nanoTime() / 1000000L;
    }

    private static String scope(String session, EntityPlayerMP p) {
        return session + ":" + p.dimension + ":" + p.getUniqueID();
    }

    Object call(
            String tool, Map<String, Object> a, String session, WorldServer w, EntityPlayerMP p) {
        String scope = scope(session, p);
        if (tool.equals("list_markers")) {
            List<Object> result = new ArrayList<Object>();
            for (MarkerStore.Entry<Marker> e : store.list(scope)) result.add(describe(e));
            return map("markers", result);
        }
        if (tool.equals("clear_markers")) return map("removed", store.clear(scope));
        if (tool.equals("remove_marker"))
            return map("removed", store.remove(scope, string(a, "markerId")));
        Entity entity = null;
        double x, y, z;
        if (tool.equals("highlight_entity")) {
            entity = w.getEntityByID(integer(a, "entityId", 0, 0, Integer.MAX_VALUE));
            if (entity == null
                    || entity.isDead
                    || !entity.getUniqueID().toString().equals(string(a, "entityUuid")))
                throw new IllegalArgumentException("ENTITY_NOT_LOADED_OR_UUID_CHANGED");
            x = entity.posX;
            y = entity.posY;
            z = entity.posZ;
        } else {
            x = Observations.coordinate(a, "x") + 0.5;
            y = integer(a, "y", 0, 0, 255);
            z = Observations.coordinate(a, "z") + 0.5;
        }
        if (!Observations.loaded(w, MathHelper.floor_double(x), MathHelper.floor_double(z)))
            throw new IllegalArgumentException("CHUNK_UNLOADED");
        if (p.getDistanceSq(x, y, z) > 64 * 64)
            throw new IllegalArgumentException("MARKER_TOO_FAR; maximum 64 blocks");
        Marker marker = new Marker(tool, session, w, p, entity, x, y, z);
        return describe(store.add(scope, marker, now(), integer(a, "durationSeconds", 10, 1, 120)));
    }

    void tick(WorldSessions<WorldServer> worlds) {
        store.prune(
                now(),
                m ->
                        worlds.world(m.dimension) == m.world
                                && m.session.equals(worlds.session(m.dimension))
                                && m.player.worldObj == m.world
                                && m.player.dimension == m.dimension
                                && !m.player.isDead
                                && MinecraftServer.getServer()
                                        .getConfigurationManager()
                                        .playerEntityList
                                        .contains(m.player)
                                && (m.entity == null
                                        || (!m.entity.isDead
                                                && m.entity.worldObj == m.world
                                                && m.world.getEntityByID(m.entity.getEntityId())
                                                        == m.entity))
                                && Observations.loaded(
                                        m.world,
                                        MathHelper.floor_double(m.x()),
                                        MathHelper.floor_double(m.z())));
        if (++ticks % 10 != 0) return;
        for (MarkerStore.Entry<Marker> e : store.list(null)) {
            Marker m = e.value;
            double x = m.x(), y = m.y(), z = m.z();
            if (m.player.getDistanceSq(x, y, z) > 64 * 64) continue;
            double radius =
                    m.entity == null ? 0.65 : Math.min(4, Math.max(0.4, m.entity.width * 0.65));
            double height = m.entity == null ? 1 : Math.min(8, Math.max(0.3, m.entity.height));
            for (int i = 0; i < 12; i++) {
                double angle = i * Math.PI / 6;
                particle(
                        m,
                        "happyVillager",
                        x + Math.cos(angle) * radius,
                        y + height + 0.15,
                        z + Math.sin(angle) * radius);
            }
            if (m.kind.equals("set_waypoint")) {
                double dx = x - m.player.posX, dy = y - m.player.posY, dz = z - m.player.posZ;
                double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
                for (int i = 1; i <= Math.min(12, (int) length); i++)
                    particle(
                            m,
                            "happyVillager",
                            m.player.posX + dx * i / length,
                            m.player.posY + 0.5 + dy * i / length,
                            m.player.posZ + dz * i / length);
            }
        }
    }

    private static void particle(Marker m, String type, double x, double y, double z) {
        m.player.playerNetServerHandler.sendPacket(
                new S2APacketParticles(type, (float) x, (float) y, (float) z, 0, 0, 0, 0, 1));
    }

    private static Object describe(MarkerStore.Entry<Marker> e) {
        Marker m = e.value;
        return map(
                "markerId",
                e.id,
                "kind",
                m.kind,
                "x",
                m.x(),
                "y",
                m.y(),
                "z",
                m.z(),
                "remainingMs",
                Math.max(0, e.expires - now()),
                "entityId",
                m.entity == null ? null : m.entity.getEntityId(),
                "entityUuid",
                m.entity == null ? null : m.entity.getUniqueID().toString(),
                "visibility",
                "recipient-only vanilla particles; client particle settings and distance apply");
    }

    private static final class Marker {
        final String kind, session;
        final WorldServer world;
        final EntityPlayerMP player;
        final Entity entity;
        final int dimension;
        final double x, y, z;

        Marker(
                String kind,
                String session,
                WorldServer world,
                EntityPlayerMP player,
                Entity entity,
                double x,
                double y,
                double z) {
            this.kind = kind;
            this.session = session;
            this.world = world;
            this.player = player;
            this.entity = entity;
            this.dimension = player.dimension;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        double x() {
            return entity == null ? x : entity.posX;
        }

        double y() {
            return entity == null ? y : entity.posY;
        }

        double z() {
            return entity == null ? z : entity.posZ;
        }
    }
}
