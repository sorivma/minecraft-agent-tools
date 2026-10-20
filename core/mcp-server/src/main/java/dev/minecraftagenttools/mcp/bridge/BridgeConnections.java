package dev.minecraftagenttools.mcp.bridge;

import dev.minecraftagenttools.api.ToolContext;
import java.util.*;

/** Lazy configuration: a bare endpoint needs no game credentials or running backend. */
public final class BridgeConnections implements ToolContext {
    private final Map<String, String> environment;

    public BridgeConnections(Map<String, String> environment) {
        this.environment = new HashMap<>(environment);
    }

    public Map<String, Object> callBridge(
            String connection, String tool, Map<String, Object> arguments) throws Exception {
        if (connection == null || !connection.matches("[a-z][a-z0-9_]*"))
            throw new IllegalArgumentException("Invalid bridge connection name");
        String prefix =
                connection.equals("minecraft")
                        ? "MAT_"
                        : "MAT_" + connection.toUpperCase(Locale.ROOT) + "_";
        String token = environment.get(prefix + "TOKEN");
        if (token == null || token.length() < 32)
            throw new IllegalArgumentException(
                    "Set " + prefix + "TOKEN (at least 32 characters), matching the adapter");
        String configuredPort = environment.get(prefix + "PORT");
        if (configuredPort == null && !connection.equals("minecraft"))
            throw new IllegalArgumentException("Set " + prefix + "PORT");
        int port = Integer.parseInt(configuredPort == null ? "25576" : configuredPort);
        if (port < 1024 || port > 65535)
            throw new IllegalArgumentException("Invalid " + prefix + "PORT");
        return new BridgeClient(port, token).call(tool, arguments);
    }
}
