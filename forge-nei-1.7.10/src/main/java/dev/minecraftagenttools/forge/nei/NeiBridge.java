package dev.minecraftagenttools.forge.nei;

import static dev.minecraftagenttools.protocol.data.Data.map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.minecraftagenttools.nei.contract.NeiTools;
import dev.minecraftagenttools.protocol.data.Data;
import dev.minecraftagenttools.protocol.transport.Frames;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BiFunction;

/**
 * Socket workers handle detached JSON; the client tick thread exclusively owns NEI/Minecraft reads.
 */
final class NeiBridge implements Closeable {
    private final ServerSocket listener;
    private final String token;
    private final BiFunction<String, Map<String, Object>, Map<String, Object>> reader;
    private final Gson gson = new GsonBuilder().serializeNulls().create();
    private final BlockingQueue<Job> pending = new ArrayBlockingQueue<Job>(32);
    private final Set<Socket> sockets = Collections.synchronizedSet(new HashSet<Socket>());
    private final ThreadPoolExecutor workers =
            new ThreadPoolExecutor(
                    2,
                    4,
                    30,
                    TimeUnit.SECONDS,
                    new ArrayBlockingQueue<Runnable>(8),
                    r -> {
                        Thread t = new Thread(r, "mat-nei-io");
                        t.setDaemon(true);
                        return t;
                    });
    private volatile boolean closed;

    NeiBridge(
            int port,
            String token,
            BiFunction<String, Map<String, Object>, Map<String, Object>> reader)
            throws IOException {
        this.token = token;
        this.reader = reader;
        listener = new ServerSocket();
        listener.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 8);
        Thread accept = new Thread(this::accept, "mat-nei-accept");
        accept.setDaemon(true);
        accept.start();
    }

    private void accept() {
        while (!closed)
            try {
                final Socket socket = listener.accept();
                socket.setSoTimeout(5000);
                sockets.add(socket);
                try {
                    workers.execute(() -> serve(socket));
                } catch (RejectedExecutionException e) {
                    sockets.remove(socket);
                    socket.close();
                }
            } catch (IOException e) {
                if (!closed) System.err.println("MAT bridge accept failed: " + e.getMessage());
            }
    }

    @SuppressWarnings("unchecked")
    private void serve(Socket socket) {
        Job job = null;
        try (Socket connection = socket) {
            Writer out =
                    new OutputStreamWriter(connection.getOutputStream(), StandardCharsets.UTF_8);
            Map<String, Object> response;
            try {
                String line =
                        Frames.read(
                                new BufferedReader(
                                        new InputStreamReader(
                                                connection.getInputStream(),
                                                StandardCharsets.UTF_8)));
                Map<String, Object> request = gson.fromJson(line, Map.class);
                if (request == null
                        || !(request.get("token") instanceof String)
                        || !MessageDigest.isEqual(
                                token.getBytes(StandardCharsets.UTF_8),
                                ((String) request.get("token")).getBytes(StandardCharsets.UTF_8)))
                    throw new IllegalArgumentException("Unauthorized");
                if (Data.integer(request, "version", -1, 1, 1) != 1)
                    throw new IllegalArgumentException("Protocol version mismatch");
                String tool = Data.string(request, "tool");
                Map<String, Object> args = (Map<String, Object>) request.get("arguments");
                if (args == null) throw new IllegalArgumentException("Missing arguments");
                NeiTools.validate(tool, args);
                job = new Job(tool, args);
                if (closed || !pending.offer(job))
                    throw new IllegalStateException("Bridge busy or stopping");
                response = job.result.get(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                response =
                        map(
                                "ok",
                                false,
                                "error",
                                e instanceof TimeoutException
                                        ? "Game thread timeout"
                                        : String.valueOf(e.getMessage()));
            }
            String json = gson.toJson(response);
            if (json.length() > Frames.MAX_CHARS)
                json = gson.toJson(map("ok", false, "error", "Response too large; narrow query"));
            Frames.write(out, json);
        } catch (IOException ignored) {
            // Client disconnected; cancel pending work below.
        } finally {
            if (job != null) {
                job.result.cancel(false);
                pending.remove(job);
            }
            sockets.remove(socket);
        }
    }

    void tick() {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(5);
        for (int i = 0; i < 4 && System.nanoTime() < deadline; i++) {
            Job job = pending.poll();
            if (job == null) return;
            if (job.result.isDone() || System.nanoTime() > job.expires) continue;
            try {
                job.result.complete(map("ok", true, "data", reader.apply(job.tool, job.args)));
            } catch (Exception | LinkageError e) {
                job.result.complete(map("ok", false, "error", String.valueOf(e.getMessage())));
            }
        }
    }

    int port() {
        return listener.getLocalPort();
    }

    int pendingCount() {
        return pending.size();
    }

    @Override
    public void close() {
        closed = true;
        try {
            listener.close();
        } catch (IOException ignored) {
        }
        synchronized (sockets) {
            for (Socket s : sockets)
                try {
                    s.close();
                } catch (IOException ignored) {
                }
        }
        workers.shutdownNow();
        Job job;
        while ((job = pending.poll()) != null) job.result.cancel(false);
    }

    private static final class Job {
        final String tool;
        final Map<String, Object> args;
        final long expires = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        final CompletableFuture<Map<String, Object>> result =
                new CompletableFuture<Map<String, Object>>();

        Job(String tool, Map<String, Object> args) {
            this.tool = tool;
            this.args = args;
        }
    }
}
