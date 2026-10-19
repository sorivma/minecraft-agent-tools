package dev.minecraftagenttools.api;

import java.util.List;
import java.util.Map;

/**
 * Startup-discovered provider. Public no-argument constructor required by ServiceLoader.
 * Descriptors and results contain only JSON-compatible maps, lists and scalar values. Calls run
 * outside Minecraft; adapters must marshal game access onto the game thread.
 */
public interface ToolProvider {
    String id();

    List<Map<String, Object>> tools();

    void validate(String tool, Map<String, Object> arguments);

    /** Return an envelope with boolean ok and data or error. */
    Map<String, Object> call(String tool, Map<String, Object> arguments, ToolContext context)
            throws Exception;

    default String instructions() {
        return "";
    }
}
