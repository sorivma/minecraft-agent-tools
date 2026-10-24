package dev.minecraftagenttools.mcp.http;

import static dev.minecraftagenttools.protocol.data.Data.map;
import static org.junit.Assert.*;

import com.google.gson.*;
import dev.minecraftagenttools.api.*;
import dev.minecraftagenttools.gamebridge.GameBridge;
import dev.minecraftagenttools.mcp.bridge.BridgeConnections;
import dev.minecraftagenttools.mcp.plugins.ToolRegistry;
import dev.minecraftagenttools.mcp.rpc.RpcServer;
import dev.minecraftagenttools.protocol.transport.Frames;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import org.junit.Test;

public class HttpEndpointTest {
    private static final String TOKEN = "0123456789abcdef0123456789abcdef";
    private static final String INIT =
            request(
                    "initialize",
                    "{\"protocolVersion\":\"2025-11-25\","
                            + "\"capabilities\":{},\"clientInfo\":{\"name\":\"fixture\",\"version\":\"1\"}}");
    private static final String READY =
            "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}";

    private static String request(String method, String params) {
        return "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\""
                + method
                + "\",\"params\":"
                + params
                + "}";
    }

    private static HttpEndpoint empty() throws Exception {
        return new HttpEndpoint(
                0,
                TOKEN,
                () ->
                        new RpcServer(
                                new ToolRegistry(Collections.emptyList()),
                                (name, tool, args) -> {
                                    throw new AssertionError("Empty registry must not dispatch");
                                }));
    }

    @Test
    public void initializationIsIsolatedPerClientAndDeletionInvalidatesOnlyThatSession()
            throws Exception {
        try (HttpEndpoint endpoint = empty()) {
            Reply first = post(endpoint.port(), null, INIT);
            Reply second = post(endpoint.port(), null, INIT);
            assertEquals(200, first.status);
            assertNotNull(first.session);
            assertNotEquals(first.session, second.session);
            assertEquals(202, post(endpoint.port(), first.session, READY).status);
            assertEquals(
                    0,
                    post(endpoint.port(), first.session, request("tools/list", "{}"))
                            .json()
                            .getAsJsonObject("result")
                            .getAsJsonArray("tools")
                            .size());
            assertTrue(
                    post(endpoint.port(), second.session, request("tools/list", "{}"))
                            .json()
                            .has("error"));
            assertEquals(400, post(endpoint.port(), null, request("tools/list", "{}")).status);
            assertEquals(404, post(endpoint.port(), "unknown", READY).status);
            assertEquals(
                    204,
                    send(endpoint.port(), "DELETE", first.session, null, TOKEN, null, null).status);
            assertEquals(404, post(endpoint.port(), first.session, READY).status);
            assertEquals(202, post(endpoint.port(), second.session, READY).status);
            assertEquals(
                    202,
                    post(
                                    endpoint.port(),
                                    second.session,
                                    "{\"jsonrpc\":\"2.0\",\"id\":8,\"result\":{}}")
                            .status);
            assertEquals(
                    405,
                    send(endpoint.port(), "GET", second.session, null, TOKEN, null, null).status);
        }
    }

    @Test
    public void authenticationOriginVersionAndPayloadBoundsAreChecked() throws Exception {
        try (HttpEndpoint endpoint = empty()) {
            assertEquals(401, send(endpoint.port(), "POST", null, INIT, null, null, null).status);
            assertEquals(
                    401, send(endpoint.port(), "POST", null, INIT, "wrong", null, null).status);
            assertEquals(
                    403,
                    send(
                                    endpoint.port(),
                                    "POST",
                                    null,
                                    INIT,
                                    TOKEN,
                                    "https://untrusted.example",
                                    null)
                            .status);
            assertEquals(
                    200,
                    send(
                                    endpoint.port(),
                                    "POST",
                                    null,
                                    INIT,
                                    TOKEN,
                                    "http://127.0.0.1:" + endpoint.port(),
                                    null)
                            .status);
            assertEquals(
                    400, send(endpoint.port(), "POST", null, INIT, TOKEN, null, "wrong").status);
            assertEquals(400, post(endpoint.port(), null, "[]").status);
            assertEquals(400, post(endpoint.port(), null, "{").status);
            char[] large = new char[Frames.MAX_CHARS + 1];
            Arrays.fill(large, ' ');
            assertEquals(413, post(endpoint.port(), null, new String(large)).status);
        }
    }

    @Test
    public void sessionsAreBoundedAndDeletingOneReleasesCapacity() throws Exception {
        try (HttpEndpoint endpoint = empty()) {
            String first = null;
            for (int i = 0; i < 32; i++) {
                Reply reply = post(endpoint.port(), null, INIT);
                assertEquals(200, reply.status);
                if (i == 0) first = reply.session;
            }
            assertEquals(503, post(endpoint.port(), null, INIT).status);
            assertEquals(
                    204, send(endpoint.port(), "DELETE", first, null, TOKEN, null, null).status);
            assertEquals(200, post(endpoint.port(), null, INIT).status);
        }
    }

    @Test(timeout = 15000)
    public void httpProvidersStayOffTheGameThreadAndAdaptersRunOnlyWhenTicked() throws Exception {
        Thread game = Thread.currentThread();
        try (GameBridge bridge =
                new GameBridge(
                        0,
                        TOKEN,
                        (tool, args) -> {},
                        (tool, args) -> {
                            assertSame(game, Thread.currentThread());
                            return map("fromGame", true);
                        })) {
            ToolProvider provider =
                    new ToolProvider() {
                        public String id() {
                            return "fixture";
                        }

                        public List<Map<String, Object>> tools() {
                            return Collections.singletonList(
                                    map(
                                            "name",
                                            "fixture_read",
                                            "inputSchema",
                                            map("type", "object")));
                        }

                        public void validate(String tool, Map<String, Object> args) {}

                        public Map<String, Object> call(
                                String tool, Map<String, Object> args, ToolContext context)
                                throws Exception {
                            assertNotSame(game, Thread.currentThread());
                            return context.callBridge("minecraft", tool, args);
                        }
                    };
            Map<String, String> env = new HashMap<String, String>();
            env.put("MAT_PORT", Integer.toString(bridge.port()));
            env.put("MAT_TOKEN", TOKEN);
            ToolRegistry tools = new ToolRegistry(Collections.singletonList(provider));
            try (HttpEndpoint endpoint =
                    new HttpEndpoint(
                            0, TOKEN, () -> new RpcServer(tools, new BridgeConnections(env)))) {
                String session = post(endpoint.port(), null, INIT).session;
                post(endpoint.port(), session, READY);
                ExecutorService client = Executors.newSingleThreadExecutor();
                try {
                    Future<Reply> result =
                            client.submit(
                                    () ->
                                            post(
                                                    endpoint.port(),
                                                    session,
                                                    request(
                                                            "tools/call",
                                                            "{\"name\":\"fixture_read\",\"arguments\":{}}")));
                    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
                    while (bridge.pendingCount() == 0 && System.nanoTime() < deadline)
                        Thread.sleep(2);
                    assertEquals(1, bridge.pendingCount());
                    assertFalse(result.isDone());
                    bridge.tick();
                    assertTrue(
                            result.get(3, TimeUnit.SECONDS)
                                    .json()
                                    .getAsJsonObject("result")
                                    .getAsJsonObject("structuredContent")
                                    .getAsJsonObject("data")
                                    .get("fromGame")
                                    .getAsBoolean());
                } finally {
                    client.shutdownNow();
                }
            }
        }
    }

    /** One fixture JVM stands for Minecraft, and owns the endpoint in its own address space. */
    public static class GameFixture {
        public static void main(String[] args) throws Exception {
            try (HttpEndpoint endpoint = empty()) {
                System.out.println(endpoint.port());
                System.out.flush();
                while (System.in.read() != -1) {}
            }
        }
    }

    @Test(timeout = 20000)
    public void closingOrForciblyKillingTheGameJvmLeavesNoMcpListener() throws Exception {
        StringBuilder path = new StringBuilder();
        for (Class<?> type :
                new Class<?>[] {
                    GameFixture.class,
                    HttpEndpoint.class,
                    ToolContext.class,
                    Frames.class,
                    JsonObject.class
                }) {
            if (path.length() > 0) path.append(File.pathSeparator);
            path.append(new File(type.getProtectionDomain().getCodeSource().getLocation().toURI()));
        }
        for (boolean crash : new boolean[] {false, true}) {
            Process game =
                    new ProcessBuilder(
                                    new File(System.getProperty("java.home"), "bin/java").getPath(),
                                    "-cp",
                                    path.toString(),
                                    GameFixture.class.getName())
                            .start();
            ExecutorService read = Executors.newSingleThreadExecutor();
            try {
                int port =
                        Integer.parseInt(
                                read.submit(
                                                () ->
                                                        new BufferedReader(
                                                                        new InputStreamReader(
                                                                                game
                                                                                        .getInputStream(),
                                                                                "UTF-8"))
                                                                .readLine())
                                        .get(3, TimeUnit.SECONDS));
                assertEquals(200, post(port, null, INIT).status);
                if (crash) game.destroyForcibly();
                else game.getOutputStream().close();
                assertTrue("Embedding JVM did not exit", game.waitFor(4, TimeUnit.SECONDS));
                try {
                    post(port, null, INIT);
                    fail("MCP listener survived its embedding JVM");
                } catch (ConnectException | SocketTimeoutException expected) {
                }
            } finally {
                game.destroyForcibly();
                game.waitFor(3, TimeUnit.SECONDS);
                read.shutdownNow();
            }
        }
    }

    private static Reply post(int port, String session, String body) throws Exception {
        return send(port, "POST", session, body, TOKEN, null, null);
    }

    private static Reply send(
            int port,
            String method,
            String session,
            String body,
            String token,
            String origin,
            String version)
            throws Exception {
        HttpURLConnection http =
                (HttpURLConnection) new URL("http://127.0.0.1:" + port + "/mcp").openConnection();
        http.setConnectTimeout(1000);
        http.setReadTimeout(5000);
        http.setRequestMethod(method);
        http.setRequestProperty("Accept", "application/json, text/event-stream");
        http.setRequestProperty("Content-Type", "application/json");
        if (token != null) http.setRequestProperty("Authorization", "Bearer " + token);
        if (origin != null) http.setRequestProperty("Origin", origin);
        if (version != null) http.setRequestProperty("MCP-Protocol-Version", version);
        if (session != null) http.setRequestProperty("Mcp-Session-Id", session);
        if (body != null) {
            http.setDoOutput(true);
            http.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
            http.getOutputStream().close();
        }
        try {
            int status = http.getResponseCode();
            String id = http.getHeaderField("Mcp-Session-Id");
            InputStream input = status < 400 ? http.getInputStream() : http.getErrorStream();
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            if (input != null) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) bytes.write(buffer, 0, count);
                input.close();
            }
            return new Reply(status, id, new String(bytes.toByteArray(), StandardCharsets.UTF_8));
        } finally {
            http.disconnect();
        }
    }

    private static final class Reply {
        final int status;
        final String session, body;

        Reply(int status, String session, String body) {
            this.status = status;
            this.session = session;
            this.body = body;
        }

        JsonObject json() {
            return new JsonParser().parse(body).getAsJsonObject();
        }
    }
}
