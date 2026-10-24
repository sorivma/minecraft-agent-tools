package dev.minecraftagenttools.mcp.http;

import com.google.gson.JsonObject;

/** Versioned, client-neutral advertisement shared through Forge IMC (no Harness dependency). */
public final class McpDiscovery {
    public static final String DISCOVER = "agent-mcp:discover/v1";
    public static final String SERVER = "agent-mcp:server/v1";

    private McpDiscovery() {}

    public static String advertisement(int port, String token) {
        if (port < 1
                || port > 65535
                || token == null
                || token.length() < 32
                || token.contains("\r")
                || token.contains("\n"))
            throw new IllegalArgumentException("Invalid MCP endpoint configuration");
        JsonObject server = new JsonObject();
        server.addProperty("id", "minecraft");
        server.addProperty("transport", "streamable-http");
        server.addProperty("url", "http://127.0.0.1:" + port + "/mcp");
        server.addProperty("startup_timeout_sec", 20);
        server.addProperty("tool_timeout_sec", 30);
        JsonObject headers = new JsonObject();
        headers.addProperty("Authorization", "Bearer " + token);
        server.add("http_headers", headers);
        return server.toString();
    }
}
