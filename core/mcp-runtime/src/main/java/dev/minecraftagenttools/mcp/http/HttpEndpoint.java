package dev.minecraftagenttools.mcp.http;

import com.google.gson.*;
import com.sun.net.httpserver.*;
import dev.minecraftagenttools.mcp.rpc.RpcServer;
import dev.minecraftagenttools.protocol.transport.Frames;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

/** Java 8 Streamable HTTP, JSON responses, no SSE. Owned by the embedding JVM. */
public final class HttpEndpoint implements Closeable {
    private static final String VERSION = "2025-11-25";
    private static final long IDLE_NANOS = TimeUnit.MINUTES.toNanos(30);
    private final HttpServer server;
    private final Supplier<RpcServer> factory;
    private final byte[] authorization;
    private final Map<String, Session> sessions = new ConcurrentHashMap<String, Session>();
    private final ExecutorService workers;
    private volatile boolean closed;

    public HttpEndpoint(int port, String token, Supplier<RpcServer> factory) throws IOException {
        if (token == null || token.length() < 32)
            throw new IllegalArgumentException("MCP HTTP token must have at least 32 characters");
        this.factory = Objects.requireNonNull(factory);
        authorization = ("Bearer " + token).getBytes(StandardCharsets.UTF_8);
        workers =
                new ThreadPoolExecutor(
                        2,
                        4,
                        30,
                        TimeUnit.SECONDS,
                        new ArrayBlockingQueue<Runnable>(16),
                        r -> {
                            Thread thread = new Thread(r, "mat-mcp-http");
                            thread.setDaemon(true);
                            return thread;
                        });
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 16);
        server.setExecutor(workers);
        server.createContext("/mcp", this::serve);
        // HttpServer's dispatcher inherits its creator's daemon flag. Do not keep Minecraft alive.
        Thread start = new Thread(server::start, "mat-mcp-http-start");
        start.setDaemon(true);
        start.start();
        try {
            start.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            close();
            throw new IOException("Interrupted starting MCP HTTP endpoint", e);
        }
    }

    public int port() {
        return server.getAddress().getPort();
    }

    private void serve(HttpExchange exchange) throws IOException {
        try {
            if (closed) {
                empty(exchange, 503);
                return;
            }
            if (!exchange.getRequestURI().getPath().equals("/mcp")
                    || exchange.getRequestURI().getRawQuery() != null) {
                empty(exchange, 404);
                return;
            }
            Headers headers = exchange.getRequestHeaders();
            String host = headers.getFirst("Host");
            if (!localHost(host)) {
                empty(exchange, 403);
                return;
            }
            String origin = headers.getFirst("Origin");
            if (origin != null
                    && !origin.equals("http://127.0.0.1:" + port())
                    && !origin.equals("http://localhost:" + port())) {
                empty(exchange, 403);
                return;
            }
            String supplied = headers.getFirst("Authorization");
            if (supplied == null
                    || !MessageDigest.isEqual(
                            authorization, supplied.getBytes(StandardCharsets.UTF_8))) {
                exchange.getResponseHeaders().set("WWW-Authenticate", "Bearer");
                empty(exchange, 401);
                return;
            }
            String version = headers.getFirst("MCP-Protocol-Version");
            if (version != null && !version.equals(VERSION)) {
                empty(exchange, 400);
                return;
            }
            String method = exchange.getRequestMethod();
            if (!method.equals("POST") && !method.equals("DELETE")) {
                exchange.getResponseHeaders().set("Allow", "POST, DELETE");
                empty(exchange, 405);
                return;
            }
            if (method.equals("DELETE")) {
                String id = headers.getFirst("Mcp-Session-Id");
                if (id == null) empty(exchange, 400);
                else empty(exchange, sessions.remove(id) == null ? 404 : 204);
                return;
            }
            String accept = headers.getFirst("Accept");
            if (accept == null
                    || !accept.contains("application/json")
                    || !accept.contains("text/event-stream")) {
                empty(exchange, 406);
                return;
            }
            String contentType = headers.getFirst("Content-Type");
            if (contentType == null
                    || !contentType.split(";", 2)[0].trim().equalsIgnoreCase("application/json")) {
                empty(exchange, 415);
                return;
            }
            String body = read(exchange.getRequestBody());
            JsonElement parsed;
            try {
                parsed = new JsonParser().parse(body);
            } catch (JsonParseException e) {
                empty(exchange, 400);
                return;
            }
            if (!parsed.isJsonObject()) {
                empty(exchange, 400);
                return;
            }
            JsonObject request = parsed.getAsJsonObject();
            String id = headers.getFirst("Mcp-Session-Id");
            boolean initialize =
                    request.has("method")
                            && request.get("method").isJsonPrimitive()
                            && request.get("method").getAsString().equals("initialize");
            if (initialize && id == null) {
                synchronized (sessions) {
                    long now = System.nanoTime();
                    sessions.values().removeIf(s -> now - s.touched > IDLE_NANOS);
                    if (sessions.size() >= 32) {
                        empty(exchange, 503);
                        return;
                    }
                    Session session = new Session(factory.get());
                    JsonObject response = session.rpc.handle(body);
                    if (response != null && response.has("result")) {
                        String created = UUID.randomUUID().toString();
                        sessions.put(created, session);
                        exchange.getResponseHeaders().set("Mcp-Session-Id", created);
                    }
                    reply(exchange, response);
                }
                return;
            }
            if (id == null) {
                empty(exchange, 400);
                return;
            }
            Session session = sessions.get(id);
            if (session == null || System.nanoTime() - session.touched > IDLE_NANOS) {
                sessions.remove(id);
                empty(exchange, 404);
                return;
            }
            synchronized (session) {
                session.touched = System.nanoTime();
                if (!request.has("method")
                        && request.has("id")
                        && request.has("jsonrpc")
                        && request.get("jsonrpc").equals(new JsonPrimitive("2.0"))
                        && (request.has("result") ^ request.has("error"))) {
                    empty(exchange, 202);
                } else reply(exchange, session.rpc.handle(body));
            }
        } catch (TooLarge e) {
            empty(exchange, 413);
        } catch (RuntimeException e) {
            empty(exchange, 400);
        } finally {
            exchange.close();
        }
    }

    private boolean localHost(String host) {
        return ("127.0.0.1:" + port()).equals(host) || ("localhost:" + port()).equals(host);
    }

    private static String read(InputStream input) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) != -1) {
            if (bytes.size() + read > Frames.MAX_CHARS) throw new TooLarge();
            bytes.write(buffer, 0, read);
        }
        return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
    }

    private static void reply(HttpExchange exchange, JsonObject response) throws IOException {
        if (response == null) {
            empty(exchange, 202);
            return;
        }
        byte[] body = response.toString().getBytes(StandardCharsets.UTF_8);
        if (body.length > Frames.MAX_CHARS) {
            empty(exchange, 413);
            return;
        }
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
    }

    private static void empty(HttpExchange exchange, int status) throws IOException {
        exchange.sendResponseHeaders(status, -1);
    }

    @Override
    public void close() {
        closed = true;
        server.stop(0);
        workers.shutdownNow();
        sessions.clear();
    }

    private static final class Session {
        final RpcServer rpc;
        volatile long touched = System.nanoTime();

        Session(RpcServer rpc) {
            this.rpc = rpc;
        }
    }

    private static final class TooLarge extends IOException {}
}
