package dev.minecraftagenttools.gregtech.contract;

import static dev.minecraftagenttools.protocol.data.Data.map;
import static org.junit.Assert.*;

import dev.minecraftagenttools.gregtech.provider.GregTechToolProvider;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Test;

public class GregTechContractTest {
    @Test
    public void invalidAndExecutableArgumentsNeverReachBridge() throws Exception {
        GregTechToolProvider provider = new GregTechToolProvider();
        for (Map<String, Object> args :
                Arrays.asList(map("class", "java.lang.Runtime"), map("method", "checkMachine"))) {
            try {
                provider.call(
                        "gt_status",
                        args,
                        (c, t, a) -> {
                            fail("Invalid input dispatched");
                            return null;
                        });
                fail();
            } catch (IllegalArgumentException expected) {
            }
        }
        invalid(
                "gt_inspect_machine",
                map("session", "s", "dimension", 0, "x", 0, "y", 256, "z", 0));
        invalid("gt_search_recipes", map("session", "s", "dimension", 0, "map", "m", "meta", 3));
        invalid(
                "gt_search_recipes",
                map("session", "s", "dimension", 0, "map", "m", "direction", "execute"));
        invalid("gt_get_machine", map("session", "s", "dimension", 0, "machineId", 0.5));
        invalid("gt_list_machines", map("session", "s", "dimension", 0, "limit", 65));
    }

    @Test
    public void independentProviderRoutesWithoutLoadingGameClasses() throws Exception {
        Map<String, Object> args = map("session", "s", "dimension", -1, "machineId", 1200);
        Map<String, Object> response = map("ok", true);
        assertSame(
                response,
                new GregTechToolProvider()
                        .call(
                                "gt_get_machine",
                                args,
                                (connection, tool, actual) -> {
                                    assertEquals("gregtech", connection);
                                    assertEquals("gt_get_machine", tool);
                                    assertSame(args, actual);
                                    return response;
                                }));
        assertEquals(12, GtTools.list().size());
    }

    @Test
    public void catalogIndicesSurviveSourceListChangesButNotScopeExpiryOrEviction() {
        AtomicLong clock = new AtomicLong();
        GtCatalogs catalogs = new GtCatalogs(clock::get);
        List<String> source = new ArrayList<String>(Arrays.asList("first", "second"));
        String id = catalogs.create("map", 0, "world1", source);
        source.clear();
        assertEquals("second", catalogs.require(id, 0, "world1").recipes.get(1));
        rejected(() -> catalogs.require(id, -1, "world1"));
        rejected(() -> catalogs.require(id, 0, "world2"));
        catalogs.refresh(dimension -> "world2");
        rejected(() -> catalogs.require(id, 0, "world1"));
        String next = catalogs.create("map", 0, "world2", source);
        clock.set(TimeUnit.SECONDS.toNanos(60));
        rejected(() -> catalogs.require(next, 0, "world2"));
        String oldest = catalogs.create("map", 0, "world2", source);
        for (int i = 0; i < 4; i++) catalogs.create("map", 0, "world2", source);
        rejected(() -> catalogs.require(oldest, 0, "world2"));
    }

    @Test
    public void publicInterfaceWorksForAnonymousStructureElementsWithoutPrivateAccess() {
        Getter hiddenImplementation =
                new Getter() {
                    public long read() {
                        return 9007199254740993L;
                    }
                };
        assertEquals("9007199254740993", GtRead.scalar(GtRead.call(hiddenImplementation, "read")));
        try {
            GtRead.field(hiddenImplementation, "privateState");
            fail();
        } catch (IllegalStateException expected) {
        }
    }

    public interface Getter {
        long read();
    }

    private void invalid(String tool, Map<String, Object> args) {
        rejected(() -> GtTools.validate(tool, args));
    }

    private void rejected(Runnable call) {
        try {
            call.run();
            fail("Invalid/stale input accepted");
        } catch (IllegalArgumentException expected) {
        }
    }
}
