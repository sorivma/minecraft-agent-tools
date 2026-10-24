package dev.minecraftagenttools.mcp.plugins;

import dev.minecraftagenttools.api.ToolProvider;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Loads provider services exclusively from JARs in a startup directory, not the core classpath. */
public final class PluginLoader implements Closeable {
    private static final String SERVICE = "META-INF/services/" + ToolProvider.class.getName();
    private final URLClassLoader loader;
    private final ToolRegistry registry;

    public PluginLoader(Path directory) throws IOException {
        List<URL> jars = new ArrayList<>();
        if (Files.exists(directory)) {
            if (!Files.isDirectory(directory))
                throw new IOException("Plugin path is not a directory: " + directory);
            try (Stream<Path> files = Files.list(directory)) {
                for (Path path :
                        files.filter(
                                        p ->
                                                Files.isRegularFile(p)
                                                        && p.getFileName()
                                                                .toString()
                                                                .endsWith(".jar"))
                                .sorted()
                                .collect(Collectors.toList())) jars.add(path.toUri().toURL());
            }
        }
        loader =
                new URLClassLoader(jars.toArray(new URL[0]), ToolProvider.class.getClassLoader()) {
                    @Override
                    public Enumeration<URL> getResources(String name) throws IOException {
                        return name.equals(SERVICE)
                                ? findResources(name)
                                : super.getResources(name);
                    }
                };
        try {
            registry = new ToolRegistry(ServiceLoader.load(ToolProvider.class, loader));
        } catch (RuntimeException | LinkageError | ServiceConfigurationError e) {
            loader.close();
            throw new IOException(
                    "Cannot load MCP providers from " + directory + ": " + e.getMessage(), e);
        }
    }

    public ToolRegistry registry() {
        return registry;
    }

    @Override
    public void close() throws IOException {
        loader.close();
    }

    /** For a packaged JAR, use plugins beside the core, or beside lib in installDist. */
    public static Path defaultDirectory() throws URISyntaxException {
        Path location =
                Paths.get(
                        PluginLoader.class
                                .getProtectionDomain()
                                .getCodeSource()
                                .getLocation()
                                .toURI());
        if (Files.isRegularFile(location)) {
            Path parent = location.getParent();
            if (parent.getFileName().toString().equals("lib")) parent = parent.getParent();
            return parent.resolve("plugins");
        }
        return Paths.get("plugins").toAbsolutePath();
    }
}
