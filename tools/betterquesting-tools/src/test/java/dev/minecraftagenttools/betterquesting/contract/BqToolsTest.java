package dev.minecraftagenttools.betterquesting.contract;

import static dev.minecraftagenttools.protocol.data.Data.*;
import static org.junit.Assert.*;

import dev.minecraftagenttools.betterquesting.provider.BetterQuestingToolProvider;
import java.util.*;
import org.junit.Test;

public class BqToolsTest {
    private Map<String, Object> scope() {
        return map("session", "s", "dimension", -1);
    }

    @Test
    public void rejectsMalformedArgumentsBeforeBridgeAccess() {
        for (Object value : Arrays.asList(null, Double.NaN, 1.5, -1, 65, "32")) {
            Map<String, Object> args = scope();
            args.put("limit", value);
            assertThrows(
                    IllegalArgumentException.class,
                    () -> BqTools.validate("bq_search_quests", args));
        }
        assertThrows(
                IllegalArgumentException.class, () -> BqTools.validate("bq_claim_reward", scope()));
        assertThrows(
                IllegalArgumentException.class,
                () -> BqTools.validate("bq_status", map("session", "s")));
        Map<String, Object> args = scope();
        args.put("questId", "1-1-1-1-1");
        assertThrows(IllegalArgumentException.class, () -> BqTools.validate("bq_get_quest", args));
        args.put("questId", UUID.randomUUID().toString());
        args.put("path", "/foo~x");
        assertThrows(IllegalArgumentException.class, () -> BqTools.validate("bq_get_quest", args));
        args.put("path", "/config/a~1b~0c");
        BqTools.validate("bq_get_quest", args);
    }

    @Test
    public void filtersAreOptionalAndScopeIsRequired() {
        BqTools.validate("bq_search_quests", scope());
        Map<String, Object> args = scope();
        args.put("state", "unclaimed");
        args.put("revision", 5);
        BqTools.validate("bq_search_quests", args);
        args.remove("dimension");
        assertThrows(
                IllegalArgumentException.class, () -> BqTools.validate("bq_search_quests", args));
    }

    @Test
    public void providerRoutesToIndependentBridgeAndDoesNotRegisterMutations() throws Exception {
        BetterQuestingToolProvider provider = new BetterQuestingToolProvider();
        assertEquals(8, provider.tools().size());
        for (Map<String, Object> tool : provider.tools())
            assertEquals(true, ((Map<?, ?>) tool.get("annotations")).get("readOnlyHint"));
        provider.call(
                "bq_status",
                map(),
                (connection, tool, args) -> {
                    assertEquals("betterquesting", connection);
                    assertEquals("bq_status", tool);
                    return map("ok", true);
                });
    }
}
