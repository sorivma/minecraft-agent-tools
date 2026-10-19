package dev.minecraftagenttools.api;

import java.util.Map;

/** Authenticated loopback bridge transport. No connection is opened until a tool calls it. */
public interface ToolContext {
    /** Named connection: minecraft uses MAT_PORT/MAT_TOKEN; others use MAT_<NAME>_PORT/TOKEN. */
    Map<String, Object> callBridge(String connection, String tool, Map<String, Object> arguments)
            throws Exception;
}
