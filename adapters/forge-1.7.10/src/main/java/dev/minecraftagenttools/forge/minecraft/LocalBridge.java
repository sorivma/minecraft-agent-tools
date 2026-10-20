package dev.minecraftagenttools.forge.minecraft;

import static dev.minecraftagenttools.protocol.data.Data.map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.minecraftagenttools.minecraft.contract.Tools;
import dev.minecraftagenttools.protocol.data.Data;
import dev.minecraftagenttools.protocol.transport.Frames;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;

/** Socket workers handle detached JSON; the tick handler exclusively owns Minecraft reads. */
final class LocalBridge implements Closeable {
    private final ServerSocket listener;
    private final String token;
    private final Observations observations;
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
                        Thread t = new Thread(r, "mat-io");
                        t.setDaemon(true);
                        return t;
                    });
    private volatile boolean closed;

    LocalBridge(int port, String token, Observations observations) throws IOException {
        this.token = token;
        this.observations = observations;
        listener = new ServerSocket();
        listener.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 8);
        Thread accept = new Thread(this::accept, "mat-accept");
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
                Tools.validate(tool, args);
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
            // A marker request may already have executed; callers can inspect list_markers.
        } finally {
            if (job != null) {
                job.result.cancel(false);
                pending.remove(job);
            }
            sockets.remove(socket);
        }
    }

    void tick() {
        observations.tick();
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(5);
        for (int i = 0; i < 4 && System.nanoTime() < deadline; i++) {
            Job job = pending.poll();
            if (job == null) return;
            if (job.result.isDone() || System.nanoTime() > job.expires) continue;
            try {
                job.result.complete(map("ok", true, "data", observations.call(job.tool, job.args)));
            } catch (Exception e) {
                job.result.complete(map("ok", false, "error", String.valueOf(e.getMessage())));
            }
        }
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
