package dev.minecraftagenttools.nei.contract;

import static dev.minecraftagenttools.protocol.data.Data.map;
import static org.junit.Assert.*;

import dev.minecraftagenttools.nei.provider.NeiToolProvider;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Test;

public class NeiContractTest {
    @Test
    public void rejectsMalformedQueriesBeforeContactingBridge() {
        invalid(
                "nei_find_recipes",
                map(
                        "session",
                        "s",
                        "dimension",
                        0,
                        "handler",
                        "h",
                        "itemId",
                        "i",
                        "mode",
                        "craft"));
        invalid(
                "nei_get_recipe",
                map("session", "s", "dimension", 0, "queryId", "q", "recipeIndex", -1));
        invalid("nei_list_handlers", map("session", "s", "dimension", 0.5));
        invalid(
                "nei_search_items",
                map("session", "s", "dimension", 0, "query", "iron", "limit", 65));
        invalid(
                "nei_search_items",
                map("session", "s", "dimension", 0, "query", "iron", "offset", null));
        invalid("nei_status", map("command", "give"));
        invalid("nei_status", null);
    }

    @Test
    public void providerRoutesToIndependentNeiBridge() throws Exception {
        NeiToolProvider provider = new NeiToolProvider();
        Map<String, Object> args =
                map(
                        "session",
                        "s",
                        "dimension",
                        -1,
                        "handler",
                        "any.plugin",
                        "itemId",
                        "i",
                        "mode",
                        "usages");
        Map<String, Object> expected = map("ok", true, "data", Collections.emptyMap());
        assertSame(
                expected,
                provider.call(
                        "nei_find_recipes",
                        args,
                        (connection, tool, actual) -> {
                            assertEquals("nei", connection);
                            assertEquals("nei_find_recipes", tool);
                            assertSame(args, actual);
                            return expected;
                        }));
        try {
            provider.call(
                    "nei_status",
                    map("execute", "anything"),
                    (c, t, a) -> {
                        fail("Invalid input reached bridge");
                        return null;
                    });
            fail();
        } catch (IllegalArgumentException expectedError) {
            /* rejected before dispatch */
        }
    }

    @Test
    public void worldIdentityDimensionAndCatalogReloadRevokeScope() {
        NeiScope scope = new NeiScope();
        Object world = new Object(), catalog = new Object();
        assertTrue(scope.update(world, 0, catalog, true));
        String first = scope.session();
        scope.require(first, 0);
        assertFalse(scope.update(world, 0, catalog, true));
        assertTrue(scope.update(world, -1, catalog, true));
        stale(scope, first, 0);
        String second = scope.session();
        assertTrue(scope.update(new Object(), -1, catalog, true));
        stale(scope, second, -1);
        String third = scope.session();
        assertTrue(scope.update(world, -1, new Object(), true));
        stale(scope, third, -1);
        scope.update(world, -1, catalog, false);
        try {
            scope.require(scope.session(), -1);
            fail();
        } catch (IllegalStateException expected) {
        }
        scope.update(null, 0, catalog, false);
        assertNull(scope.session());
        stale(scope, first, 0);
    }

    @Test
    public void handlesExpireAreBoundedAndCanBeRevokedTogether() {
        AtomicLong clock = new AtomicLong();
        ScopedCache<String> cache = new ScopedCache<String>(2, 100, clock::get);
        String first = cache.put("first"), second = cache.put("second");
        assertEquals("first", cache.get(first));
        cache.put("third");
        missing(cache, first);
        assertEquals("second", cache.get(second));
        clock.set(100);
        missing(cache, second);
        String next = cache.put("next");
        cache.clear();
        missing(cache, next);
    }

    private void invalid(String tool, Map<String, Object> args) {
        try {
            NeiTools.validate(tool, args);
            fail("Accepted invalid query for " + tool);
        } catch (IllegalArgumentException expected) {
        }
    }

    private void stale(NeiScope scope, String session, int dimension) {
        try {
            scope.require(session, dimension);
            fail("Stale world accepted");
        } catch (IllegalArgumentException expected) {
        }
    }

    private void missing(ScopedCache<String> cache, String id) {
        try {
            cache.get(id);
            fail("Stale handle accepted");
        } catch (IllegalArgumentException expected) {
        }
    }
}
