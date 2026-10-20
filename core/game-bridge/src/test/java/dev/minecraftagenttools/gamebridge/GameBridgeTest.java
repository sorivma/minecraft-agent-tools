package dev.minecraftagenttools.gamebridge;

import static dev.minecraftagenttools.protocol.data.Data.map;
import static org.junit.Assert.*;

import com.google.gson.Gson;
import dev.minecraftagenttools.protocol.transport.Frames;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

/** Fake reader only: no Minecraft, NEI, MCP process or real game backend is launched. */
public class GameBridgeTest {
    private static final String TOKEN = "test-only-nei-credential-32-characters";

    @Test
    public void authenticatedWorkRunsOnlyOnTickAndPluginFailureDoesNotKillBridge()
            throws Exception {
        Thread tickThread = Thread.currentThread();
        AtomicInteger calls = new AtomicInteger();
        try (GameBridge bridge =
                new GameBridge(
                        0,
                        TOKEN,
                        (tool, args) -> {
                            if (!tool.equals("nei_status") || !args.isEmpty())
                                throw new IllegalArgumentException("Invalid fixture request");
                        },
                        (tool, args) -> {
                            assertSame(tickThread, Thread.currentThread());
                            if (calls.incrementAndGet() == 1)
                                throw new NoClassDefFoundError("missing plugin dependency");
                            return map("ready", true);
                        })) {
            for (int i = 0; i < 2; i++) {
                CompletableFuture<Map<?, ?>> response =
                        request(bridge.port(), TOKEN, "nei_status", map());
                awaitPending(bridge);
                assertEquals(i, calls.get());
                bridge.tick();
                Map<?, ?> result = response.get(2, TimeUnit.SECONDS);
                assertEquals(i == 1, result.get("ok"));
                if (i == 0) assertTrue(result.get("error").toString().contains("missing plugin"));
            }
        }
    }

    @Test
    public void rejectsWrongCredentialsAndInvalidArgumentsWithoutTick() throws Exception {
        try (GameBridge bridge =
                new GameBridge(
                        0,
                        TOKEN,
                        (tool, args) -> {
                            if (!tool.equals("nei_status") || !args.isEmpty())
                                throw new IllegalArgumentException("Invalid fixture request");
                        },
                        (tool, args) -> {
                            fail("Invalid request reached game thread");
                            return null;
                        })) {
            assertEquals(
                    false,
                    request(bridge.port(), "wrong", "nei_status", map())
                            .get(2, TimeUnit.SECONDS)
                            .get("ok"));
            assertEquals(
                    false,
                    request(bridge.port(), TOKEN, "nei_status", map("unknown", "x"))
                            .get(2, TimeUnit.SECONDS)
                            .get("ok"));
            assertEquals(0, bridge.pendingCount());
        }
    }

    private void awaitPending(GameBridge bridge) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (bridge.pendingCount() == 0 && System.nanoTime() < deadline) Thread.sleep(5);
        assertEquals(1, bridge.pendingCount());
    }

    private CompletableFuture<Map<?, ?>> request(
            int port, String token, String tool, Map<String, Object> args) {
        return CompletableFuture.supplyAsync(
                () -> {
                    try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), port)) {
                        socket.setSoTimeout(3000);
                        Frames.write(
                                new OutputStreamWriter(
                                        socket.getOutputStream(), StandardCharsets.UTF_8),
                                new Gson()
                                        .toJson(
                                                map(
                                                        "version",
                                                        1,
                                                        "token",
                                                        token,
                                                        "tool",
                                                        tool,
                                                        "arguments",
                                                        args)));
                        return new Gson()
                                .fromJson(
                                        Frames.read(
                                                new InputStreamReader(
                                                        socket.getInputStream(),
                                                        StandardCharsets.UTF_8)),
                                        Map.class);
                    } catch (IOException e) {
                        throw new CompletionException(e);
                    }
                });
    }
}
