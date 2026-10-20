package dev.minecraftagenttools.mcp.rpc;

import static org.junit.Assert.*;

import com.google.gson.*;
import dev.minecraftagenttools.mcp.plugins.ToolRegistry;
import dev.minecraftagenttools.minecraft.provider.MinecraftToolProvider;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

public class RpcServerTest {
    private interface Backend {
        JsonObject call(String name, Map<String, Object> args) throws Exception;
    }

    @SuppressWarnings("unchecked")
    private RpcServer server(Backend backend) {
        return new RpcServer(
                new ToolRegistry(Collections.singletonList(new MinecraftToolProvider())),
                (connection, name, args) -> {
                    assertEquals("minecraft", connection);
                    return new Gson().fromJson(backend.call(name, args), Map.class);
                });
    }

    private RpcServer server() {
        return server(
                (name, args) ->
                        JsonParser.parseString("{\"ok\":true,\"data\":{}}").getAsJsonObject());
    }

    private String request(String method, String params) {
        return "{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\""
                + method
                + "\",\"params\":"
                + params
                + "}";
    }

    private void ready(RpcServer server) {
        JsonObject response =
                server.handle(
                        request(
                                "initialize",
                                "{\"protocolVersion\":\"2025-11-25\",\"capabilities\":{},\"clientInfo\":{\"name\":\"test\",\"version\":\"1\"}}"));
        assertEquals(
                "2025-11-25",
                response.getAsJsonObject("result").get("protocolVersion").getAsString());
        assertNull(server.handle("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}"));
    }

    @Test
    public void lifecycleAndToolDiscovery() {
        RpcServer server = server();
        assertTrue(server.handle(request("tools/list", "{}")).has("error"));
        ready(server);
        JsonArray tools =
                server.handle(request("tools/list", "{}"))
                        .getAsJsonObject("result")
                        .getAsJsonArray("tools");
        assertEquals(18, tools.size());
        boolean terrain = false;
        for (JsonElement tool : tools)
            if (tool.getAsJsonObject().get("name").getAsString().equals("read_terrain"))
                terrain = true;
        assertTrue(terrain);
    }

    @Test
    public void terrainDefaultsReachBackendWithWorldScope() {
        AtomicInteger calls = new AtomicInteger();
        RpcServer server =
                server(
                        (name, args) -> {
                            assertEquals("read_terrain", name);
                            assertEquals("s", args.get("session"));
                            assertEquals("p", args.get("player"));
                            assertEquals(0, ((Number) args.get("dimension")).intValue());
                            calls.incrementAndGet();
                            return JsonParser.parseString("{\"ok\":true}").getAsJsonObject();
                        });
        ready(server);
        JsonObject response =
                server.handle(
                        request(
                                "tools/call",
                                "{\"name\":\"read_terrain\",\"arguments\":{\"session\":\"s\",\"player\":\"p\",\"dimension\":0}}"));
        assertFalse(response.getAsJsonObject("result").get("isError").getAsBoolean());
        assertEquals(1, calls.get());
    }

    @Test
    public void toolResultHasTextAndStructuredContent() {
        RpcServer server = server();
        ready(server);
        JsonObject result =
                server.handle(
                                request(
                                        "tools/call",
                                        "{\"name\":\"list_sessions\",\"arguments\":{}}"))
                        .getAsJsonObject("result");
        assertFalse(result.get("isError").getAsBoolean());
        assertTrue(result.has("structuredContent"));
        assertEquals(
                "text",
                result.getAsJsonArray("content")
                        .get(0)
                        .getAsJsonObject()
                        .get("type")
                        .getAsString());
    }

    @Test
    public void malformedRequestsAreErrorsAndDoNotCrash() {
        RpcServer server = server();
        for (String line :
                new String[] {
                    "{",
                    "[]",
                    "null",
                    "{\"jsonrpc\":\"1.0\",\"id\":1,\"method\":\"ping\"}",
                    "{\"jsonrpc\":\"2.0\",\"id\":{},\"method\":\"ping\"}"
                }) assertTrue(server.handle(line).has("error"));
        ready(server);
        assertEquals(
                -32601,
                server.handle(request("unknown", "{}"))
                        .getAsJsonObject("error")
                        .get("code")
                        .getAsInt());
    }

    @Test
    public void invalidArgumentsNeverReachBackend() {
        AtomicInteger calls = new AtomicInteger();
        RpcServer server =
                server(
                        (name, args) -> {
                            calls.incrementAndGet();
                            return new JsonObject();
                        });
        ready(server);
        assertTrue(
                server.handle(
                                request(
                                        "tools/call",
                                        "{\"name\":\"inspect_block\",\"arguments\":{}}"))
                        .has("error"));
        assertTrue(server.handle(request("tools/call", "{\"name\":\"not_a_tool\"}")).has("error"));
        assertEquals(0, calls.get());
    }

    @Test
    public void backendFailureIsToolError() {
        RpcServer server =
                server(
                        (name, args) -> {
                            throw new java.io.IOException("offline");
                        });
        ready(server);
        JsonObject result =
                server.handle(request("tools/call", "{\"name\":\"list_sessions\"}"))
                        .getAsJsonObject("result");
        assertTrue(result.get("isError").getAsBoolean());
    }

    @Test
    public void notificationsNeverInvokeToolsOrProduceResponses() {
        AtomicInteger calls = new AtomicInteger();
        RpcServer server =
                server(
                        (name, args) -> {
                            calls.incrementAndGet();
                            return new JsonObject();
                        });
        ready(server);
        assertNull(
                server.handle(
                        "{\"jsonrpc\":\"2.0\",\"method\":\"tools/call\",\"params\":{\"name\":\"list_sessions\"}}"));
        assertEquals(0, calls.get());
    }
}
