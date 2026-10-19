package dev.minecraftagenttools.protocol.world;

import static dev.minecraftagenttools.protocol.data.Data.*;
import static org.junit.Assert.*;

import dev.minecraftagenttools.minecraft.contract.Tools;
import java.util.*;
import org.junit.Test;

public class MarkerStoreTest {
    @Test
    public void expiresAtDeadlineAndInvalidTargetsAreRemoved() {
        MarkerStore<String> store = new MarkerStore<String>();
        store.add("world:dimension:player", "alive", 100, 1);
        store.add("world:dimension:player", "disconnected", 100, 120);
        store.prune(1099, v -> v.equals("alive"));
        assertEquals(1, store.list(null).size());
        store.prune(1100, v -> true);
        assertTrue(store.list(null).isEmpty());
    }

    @Test
    public void cannotRemoveAnotherPlayerOrWorldMarker() {
        MarkerStore<String> store = new MarkerStore<String>();
        String id = store.add("world1:0:player1", "target", 0, 10).id;
        for (String other :
                Arrays.asList("world2:0:player1", "world1:1:player1", "world1:0:player2")) {
            assertFalse(store.remove(other, id));
            assertEquals(0, store.clear(other));
            assertTrue(store.list(other).isEmpty());
        }
        assertTrue(store.remove("world1:0:player1", id));
        assertFalse(store.remove("world1:0:player1", id));
    }

    @Test
    public void enforcesQuotaAndReclaimsExpiredSlots() {
        MarkerStore<String> store = new MarkerStore<String>();
        for (int i = 0; i < 8; i++) store.add("scope", "target", 0, 1);
        try {
            store.add("scope", "target", 999, 1);
            fail();
        } catch (IllegalStateException expected) {
        }
        store.add("scope", "new", 1000, 1);
        assertEquals(1, store.list("scope").size());
        assertEquals(1, store.clear("scope"));
    }

    @Test
    public void markerContractRejectsInvalidLifetimeAndMissingEntityIdentity() {
        Map<String, Object> args =
                map("session", "s", "dimension", 0, "player", "p", "entityId", 1);
        try {
            Tools.validate("highlight_entity", args);
            fail();
        } catch (IllegalArgumentException expected) {
        }
        args.put("entityUuid", UUID.randomUUID().toString());
        Tools.validate("highlight_entity", args);
        for (Object value : Arrays.asList(0, 121, 1.5, Double.NaN, "10", null)) {
            args.put("durationSeconds", value);
            try {
                Tools.validate("highlight_entity", args);
                fail();
            } catch (IllegalArgumentException expected) {
            }
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    public void markerMutationsAreNotAdvertisedAsReadOnly() {
        for (Map<String, Object> spec : Tools.list()) {
            String name = (String) spec.get("name");
            boolean mutation =
                    Arrays.asList(
                                    "highlight_entity",
                                    "highlight_block",
                                    "set_waypoint",
                                    "remove_marker",
                                    "clear_markers")
                            .contains(name);
            assertEquals(
                    !mutation, ((Map<String, Object>) spec.get("annotations")).get("readOnlyHint"));
        }
    }
}
