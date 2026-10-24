package dev.minecraftagenttools.forge.schematica;

import static dev.minecraftagenttools.protocol.data.Data.*;

import com.google.gson.Gson;
import dev.minecraftagenttools.schematica.contract.*;
import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/** Detached JSON only. All disk operations run on one ordered worker, never on the game thread. */
public final class PlanStore implements AutoCloseable {
    private final Path directory;
    private final ConcurrentMap<String, AtomicLong> versions =
            new ConcurrentHashMap<String, AtomicLong>();
    private final ExecutorService worker =
            Executors.newSingleThreadExecutor(
                    r -> {
                        Thread t = new Thread(r, "mat-schematica-storage");
                        t.setDaemon(true);
                        return t;
                    });

    public PlanStore(Path directory) {
        this.directory = directory;
    }

    public static String key(String identity, int dimension) {
        if (identity == null) return null;
        try {
            byte[] bytes =
                    MessageDigest.getInstance("SHA-256")
                            .digest((identity + "\n" + dimension).getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (byte b : bytes) out.append(String.format(Locale.ROOT, "%02x", b & 255));
            return out.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }

    private Path file(String key) {
        if (key == null || !key.matches("[a-f0-9]{64}"))
            throw new IllegalArgumentException("Invalid world key");
        return directory.resolve(key + ".json");
    }

    public Future<Map<String, Object>> load(String key, int dimension) {
        return worker.submit(() -> read(key, dimension));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> read(String key, int dimension) throws IOException {
        Path path = file(key);
        if (!Files.exists(path))
            return map(
                    "sessions",
                    new ArrayList<String>(),
                    "plans",
                    new ArrayList<Map<String, Object>>());
        if (Files.size(path) > 256L * 1024 * 1024)
            throw new IOException("Saved plans file too large");
        Map<String, Object> data;
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            data = new Gson().fromJson(reader, Map.class);
        }
        try {
            if (data == null
                    || integer(data, "version", 0, 1, 1) != 1
                    || !key.equals(data.get("world"))
                    || Layout.requiredInt(data, "dimension", Integer.MIN_VALUE, Integer.MAX_VALUE)
                            != dimension) throw new IllegalArgumentException("Invalid saved scope");
            List<Object> sessions = (List<Object>) data.get("sessions");
            if (sessions == null || sessions.size() > 4096)
                throw new IllegalArgumentException("Invalid saved sessions");
            for (Object session : sessions)
                if (!(session instanceof String) || ((String) session).length() > 128)
                    throw new IllegalArgumentException("Invalid saved session");
            List<Map<String, Object>> rows = (List<Map<String, Object>>) data.get("plans");
            if (rows == null || rows.size() > Plans.MAX_PLANS)
                throw new IllegalArgumentException("Invalid saved plans");
            Plans limits = new Plans();
            limits.update(new Object(), dimension);
            for (Map<String, Object> row : rows) {
                String persistentId =
                        row.containsKey("persistentId")
                                ? Plans.requirePersistentId(Layout.text(row, "persistentId", 36))
                                : UUID.randomUUID().toString();
                row.put("persistentId", persistentId);
                Map<String, Object> args = new LinkedHashMap<String, Object>(row);
                args.remove("persistentId");
                args.remove("visible");
                args.remove("layer");
                args.put("session", "saved");
                args.put("dimension", dimension);
                SchematicaTools.validate("schematic_put", args);
                Layout layout = new Layout(args);
                Layout.requiredInt(row, "layer", -1, layout.height - 1);
                if (!(row.get("visible") instanceof Boolean))
                    throw new IllegalArgumentException("Invalid visibility");
                limits.restore(
                        persistentId,
                        string(args, "name"),
                        (String) args.get("group"),
                        layout,
                        Layout.requiredInt(args, "x", -29999872, 29999872),
                        Layout.requiredInt(args, "y", 0, 255),
                        Layout.requiredInt(args, "z", -29999872, 29999872));
            }
            return data;
        } catch (RuntimeException e) {
            throw new IOException("Invalid saved plans", e);
        }
    }

    public Future<?> save(
            String key,
            int dimension,
            Collection<String> sessions,
            List<Map<String, Object>> plans) {
        // Callers transfer detached snapshots; no Minecraft object crosses this boundary.
        Map<String, Object> data =
                map(
                        "version",
                        1,
                        "world",
                        key,
                        "dimension",
                        dimension,
                        "sessions",
                        new ArrayList<String>(sessions),
                        "plans",
                        plans);
        AtomicLong version = versions.computeIfAbsent(key, ignored -> new AtomicLong());
        long submitted = version.incrementAndGet();
        return worker.submit(
                () -> {
                    if (submitted != version.get()) return null;
                    Path destination = file(key);
                    Files.createDirectories(directory);
                    Path temp = Files.createTempFile(directory, key + "-", ".tmp");
                    try {
                        try (Writer writer =
                                Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                            new Gson().toJson(data, writer);
                        }
                        try (FileChannel channel =
                                FileChannel.open(temp, StandardOpenOption.WRITE)) {
                            channel.force(true);
                        }
                        try {
                            Files.move(
                                    temp,
                                    destination,
                                    StandardCopyOption.ATOMIC_MOVE,
                                    StandardCopyOption.REPLACE_EXISTING);
                        } catch (AtomicMoveNotSupportedException e) {
                            Files.move(temp, destination, StandardCopyOption.REPLACE_EXISTING);
                        }
                    } finally {
                        Files.deleteIfExists(temp);
                    }
                    return null;
                });
    }

    public void close() {
        worker.shutdown();
        try {
            worker.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
