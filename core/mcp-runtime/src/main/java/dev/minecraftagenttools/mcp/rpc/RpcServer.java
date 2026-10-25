package dev.minecraftagenttools.mcp.rpc;

import static dev.minecraftagenttools.protocol.data.Data.map;

import com.google.gson.*;
import dev.minecraftagenttools.api.ToolContext;
import dev.minecraftagenttools.mcp.plugins.ToolRegistry;
import java.util.*;

/** Transport-independent MCP 2025-11-25 session. No game access or stdout logging. */
public final class RpcServer {
    private final ToolRegistry providers;
    private final ToolContext context;
    private final Gson gson = new GsonBuilder().serializeNulls().create();
    private boolean initialized;
    private boolean ready;

    public RpcServer(ToolRegistry providers, ToolContext context) {
        this.providers = providers;
        this.context = context;
    }

    @SuppressWarnings("unchecked")
    public JsonObject handle(String line) {
        JsonObject req;
        try {
            JsonElement parsed = new JsonParser().parse(line);
            if (!parsed.isJsonObject())
                return error(JsonNull.INSTANCE, -32600, "Expected request object");
            req = parsed.getAsJsonObject();
        } catch (JsonParseException e) {
            return error(JsonNull.INSTANCE, -32700, "Parse error");
        }
        JsonElement id = req.has("id") ? req.get("id") : JsonNull.INSTANCE;
        if (!req.has("jsonrpc")
                || !req.get("jsonrpc").equals(new JsonPrimitive("2.0"))
                || !req.has("method")
                || !req.get("method").isJsonPrimitive()
                || !req.getAsJsonPrimitive("method").isString()
                || (req.has("id")
                        && !(id.isJsonPrimitive()
                                && (id.getAsJsonPrimitive().isString()
                                        || id.getAsJsonPrimitive().isNumber()))))
            return error(JsonNull.INSTANCE, -32600, "Invalid request");
        String method = req.get("method").getAsString();
        if (!req.has("id")) {
            if (method.equals("notifications/initialized") && initialized) ready = true;
            return null;
        }
        try {
            JsonObject params =
                    req.has("params") ? req.getAsJsonObject("params") : new JsonObject();
            Object result;
            if (method.equals("initialize")) {
                if (initialized) return error(id, -32600, "Already initialized");
                if (!params.has("protocolVersion")
                        || !params.has("capabilities")
                        || !params.has("clientInfo"))
                    return error(id, -32602, "Missing initialize fields");
                initialized = true;
                result =
                        map(
                                "protocolVersion",
                                "2025-11-25",
                                "capabilities",
                                map("tools", map("listChanged", false)),
                                "serverInfo",
                                map(
                                        "name",
                                        "minecraft-agent-tools",
                                        "version",
                                        dev.minecraftagenttools.protocol.BuildVersion.VERSION),
                                "instructions",
                                providers.instructions());
            } else if (method.equals("ping")) result = map();
            else if (!ready) return error(id, -32000, "Initialize first");
            else if (method.equals("tools/list")) result = map("tools", providers.tools());
            else if (method.equals("tools/call")) {
                String name = params.get("name").getAsString();
                Map<String, Object> args =
                        params.has("arguments")
                                ? gson.fromJson(params.get("arguments"), Map.class)
                                : map();
                if (args == null) throw new IllegalArgumentException("arguments must be an object");
                providers.validate(name, args);
                Map<String, Object> response;
                try {
                    response = providers.call(name, args, context);
                } catch (Exception e) {
                    response = map("ok", false, "error", "Provider failed: " + e.getMessage());
                }
                if (response == null)
                    response = map("ok", false, "error", "Provider returned no result");
                boolean failed = !Boolean.TRUE.equals(response.get("ok"));
                result =
                        map(
                                "content",
                                Collections.singletonList(
                                        map("type", "text", "text", gson.toJson(response))),
                                "structuredContent",
                                response,
                                "isError",
                                failed);
            } else return error(id, -32601, "Method not found");
            return gson.toJsonTree(map("jsonrpc", "2.0", "id", id, "result", result))
                    .getAsJsonObject();
        } catch (RuntimeException e) {
            return error(id, -32602, "Invalid params: " + e.getMessage());
        }
    }

    private JsonObject error(JsonElement id, int code, String message) {
        JsonObject result =
                gson.toJsonTree(
                                map(
                                        "jsonrpc",
                                        "2.0",
                                        "error",
                                        map("code", code, "message", message)))
                        .getAsJsonObject();
        result.add("id", id);
        return result;
    }
}
