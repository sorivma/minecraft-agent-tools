package dev.minecraftagenttools.mcp.plugins;

import static org.junit.Assert.*;

import com.google.gson.*;
import dev.minecraftagenttools.api.ToolProvider;
import dev.minecraftagenttools.mcp.bridge.BridgeConnections;
import dev.minecraftagenttools.mcp.rpc.RpcServer;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;

public class PluginLoaderTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void jarPresenceControlsDiscoveryAndDispatchAcrossRestart() throws Exception {
        Path plugins = temp.newFolder("plugins").toPath();
        // The Minecraft provider is on the test classpath: it must still remain absent here.
        try (PluginLoader loader = new PluginLoader(plugins)) {
            assertTrue(loader.registry().tools().isEmpty());
            assertFalse(loader.registry().instructions().contains("list_sessions"));
            RpcServer server = endpoint(loader);
            assertEquals(
                    0,
                    server.handle(request("tools/list", "{}"))
                            .getAsJsonObject("result")
                            .getAsJsonArray("tools")
                            .size());
            assertTrue(
                    server.handle(
                                    request(
                                            "tools/call",
                                            "{\"name\":\"fixture_read\",\"arguments\":{}}"))
                            .has("error"));
        }
        Path jar = fixture(plugins, "One", "fixture_one", "fixture_read");
        try (PluginLoader loader = new PluginLoader(plugins)) {
            assertEquals(1, loader.registry().tools().size());
            RpcServer server = endpoint(loader);
            JsonObject result =
                    server.handle(
                                    request(
                                            "tools/call",
                                            "{\"name\":\"fixture_read\",\"arguments\":{}}"))
                            .getAsJsonObject("result");
            assertFalse(result.get("isError").getAsBoolean());
            assertEquals(
                    "fixture_one",
                    result.getAsJsonObject("structuredContent").get("data").getAsString());
        }
        Files.delete(jar); // Also verifies that closing the loader releases the JAR on Windows.
        try (PluginLoader loader = new PluginLoader(plugins)) {
            assertTrue(loader.registry().tools().isEmpty());
        }
    }

    @Test
    public void independentProvidersCombineAndDuplicateNamesFailClearly() throws Exception {
        Path plugins = temp.newFolder("combined").toPath();
        fixture(plugins, "One", "one", "one_read");
        fixture(plugins, "Two", "two", "two_read");
        try (PluginLoader loader = new PluginLoader(plugins)) {
            assertEquals(2, loader.registry().tools().size());
        }
        Path duplicate = fixture(plugins, "Duplicate", "three", "one_read");
        try {
            new PluginLoader(plugins).close();
            fail();
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("Duplicate tool name: one_read"));
        }
        Files.delete(duplicate);
        fixture(plugins, "DuplicateId", "one", "different_read");
        try {
            new PluginLoader(plugins).close();
            fail();
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("Duplicate provider ID: one"));
        }
    }

    @Test
    public void absentDirectoryIsEmptyButBrokenProviderFailsStartup() throws Exception {
        Path plugins = temp.getRoot().toPath().resolve("absent");
        try (PluginLoader loader = new PluginLoader(plugins)) {
            assertTrue(loader.registry().tools().isEmpty());
        }
        Files.createDirectory(plugins);
        try (JarOutputStream jar =
                new JarOutputStream(Files.newOutputStream(plugins.resolve("broken.jar")))) {
            entry(
                    jar,
                    "META-INF/services/" + ToolProvider.class.getName(),
                    "missing.Provider".getBytes(StandardCharsets.UTF_8));
        }
        try {
            new PluginLoader(plugins).close();
            fail();
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("missing.Provider"));
        }
    }

    @Test
    public void unconfiguredBridgeDoesNotPreventCoreStartupOrDiscovery() throws Exception {
        BridgeConnections context = new BridgeConnections(Collections.emptyMap());
        try {
            context.callBridge("minecraft", "list_sessions", Collections.emptyMap());
            fail();
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("MAT_TOKEN"));
        }
        Map<String, String> environment = new HashMap<>();
        environment.put("MAT_NEI_TOKEN", String.join("", Collections.nCopies(32, "x")));
        try {
            new BridgeConnections(environment).callBridge("nei", "recipes", Collections.emptyMap());
            fail();
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("MAT_NEI_PORT"));
        }
    }

    @Test
    public void shippedCoreHasNoProvidersAndMinecraftJarRegistersAllTools() throws Exception {
        try (JarFile core = new JarFile(System.getProperty("core.jar"))) {
            assertEquals(
                    "dev.minecraftagenttools.mcp.Main",
                    core.getManifest().getMainAttributes().getValue("Main-Class"));
            assertNull(core.getEntry("dev/minecraftagenttools/minecraft/contract/Tools.class"));
            assertNull(core.getEntry("dev/minecraftagenttools/nei/contract/NeiTools.class"));
            assertNull(core.getEntry("dev/minecraftagenttools/gregtech/contract/GtTools.class"));
            assertNull(
                    core.getEntry(
                            "dev/minecraftagenttools/schematica/contract/SchematicaTools.class"));
            assertNull(core.getEntry("META-INF/services/" + ToolProvider.class.getName()));
            assertNotNull(core.getEntry("dev/minecraftagenttools/api/ToolProvider.class"));
            assertNotNull(core.getEntry("com/google/gson/Gson.class"));
        }
        Path plugins = temp.newFolder("shipped").toPath();
        Files.copy(
                Paths.get(System.getProperty("minecraft.provider.jar")),
                plugins.resolve("minecraft-tools.jar"));
        try (PluginLoader loader = new PluginLoader(plugins)) {
            assertEquals(18, loader.registry().tools().size());
            assertTrue(loader.registry().instructions().contains("read_terrain"));
            RpcServer server =
                    new RpcServer(loader.registry(), new BridgeConnections(Collections.emptyMap()));
            server.handle(
                    request(
                            "initialize",
                            "{\"protocolVersion\":\"2025-11-25\",\"capabilities\":{},\"clientInfo\":{}}"));
            server.handle("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}");
            JsonObject result =
                    server.handle(
                                    request(
                                            "tools/call",
                                            "{\"name\":\"list_sessions\",\"arguments\":{}}"))
                            .getAsJsonObject("result");
            assertTrue(result.get("isError").getAsBoolean());
            assertTrue(
                    result.getAsJsonObject("structuredContent")
                            .get("error")
                            .getAsString()
                            .contains("MAT_TOKEN"));
        }
    }

    @Test
    public void neiJarIndependentlyRegistersAndRoutesToolsWithoutMinecraftProvider()
            throws Exception {
        Path plugins = temp.newFolder("nei-only").toPath();
        Path provider = plugins.resolve("nei-tools.jar");
        Files.copy(Paths.get(System.getProperty("nei.provider.jar")), provider);
        try (PluginLoader loader = new PluginLoader(plugins)) {
            assertEquals(5, loader.registry().tools().size());
            RpcServer server =
                    new RpcServer(
                            loader.registry(),
                            (connection, tool, args) -> {
                                assertEquals("nei", connection);
                                assertEquals("nei_status", tool);
                                assertTrue(args.isEmpty());
                                return Collections.singletonMap("ok", true);
                            });
            server.handle(
                    request(
                            "initialize",
                            "{\"protocolVersion\":\"2025-11-25\",\"capabilities\":{},\"clientInfo\":{}}"));
            server.handle("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}");
            assertFalse(
                    server.handle(
                                    request(
                                            "tools/call",
                                            "{\"name\":\"nei_status\",\"arguments\":{}}"))
                            .getAsJsonObject("result")
                            .get("isError")
                            .getAsBoolean());
        }
        Files.delete(provider);
        try (PluginLoader loader = new PluginLoader(plugins)) {
            assertTrue(loader.registry().tools().isEmpty());
        }
    }

    @Test
    public void gregtechJarIsIndependentAndRoutesThroughItsOwnBridge() throws Exception {
        Path plugins = temp.newFolder("gregtech-only").toPath();
        Path jar = plugins.resolve("gregtech-tools.jar");
        Files.copy(Paths.get(System.getProperty("gregtech.provider.jar")), jar);
        try (PluginLoader loader = new PluginLoader(plugins)) {
            assertEquals(12, loader.registry().tools().size());
            RpcServer server =
                    new RpcServer(
                            loader.registry(),
                            (connection, tool, args) -> {
                                assertEquals("gregtech", connection);
                                assertEquals("gt_status", tool);
                                return Collections.singletonMap("ok", true);
                            });
            server.handle(
                    request(
                            "initialize",
                            "{\"protocolVersion\":\"2025-11-25\",\"capabilities\":{},\"clientInfo\":{}}"));
            server.handle("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}");
            assertFalse(
                    server.handle(
                                    request(
                                            "tools/call",
                                            "{\"name\":\"gt_status\",\"arguments\":{}}"))
                            .getAsJsonObject("result")
                            .get("isError")
                            .getAsBoolean());
        }
        Files.delete(jar);
        try (PluginLoader loader = new PluginLoader(plugins)) {
            assertTrue(loader.registry().tools().isEmpty());
        }
    }

    @Test
    public void schematicaJarRegistersOnlyWhenInstalledAndUsesClientBridge() throws Exception {
        Path plugins = temp.newFolder("schematica-only").toPath();
        Path jar = plugins.resolve("schematica-tools.jar");
        Files.copy(Paths.get(System.getProperty("schematica.provider.jar")), jar);
        try (PluginLoader loader = new PluginLoader(plugins)) {
            assertEquals(7, loader.registry().tools().size());
            RpcServer server =
                    new RpcServer(
                            loader.registry(),
                            (connection, tool, args) -> {
                                assertEquals("schematica", connection);
                                assertEquals("schematic_status", tool);
                                return Collections.singletonMap("ok", true);
                            });
            server.handle(
                    request(
                            "initialize",
                            "{\"protocolVersion\":\"2025-11-25\",\"capabilities\":{},\"clientInfo\":{}}"));
            server.handle("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}");
            assertFalse(
                    server.handle(
                                    request(
                                            "tools/call",
                                            "{\"name\":\"schematic_status\",\"arguments\":{}}"))
                            .getAsJsonObject("result")
                            .get("isError")
                            .getAsBoolean());
        }
        Files.delete(jar);
        try (PluginLoader loader = new PluginLoader(plugins)) {
            assertTrue(loader.registry().tools().isEmpty());
        }
    }

    @Test
    public void betterQuestingJarRegistersOnlyWhenInstalledAndUsesItsOwnBridge() throws Exception {
        Path plugins = temp.newFolder("betterquesting-only").toPath();
        Path jar = plugins.resolve("betterquesting-tools.jar");
        Files.copy(Paths.get(System.getProperty("betterquesting.provider.jar")), jar);
        try (PluginLoader loader = new PluginLoader(plugins)) {
            assertEquals(8, loader.registry().tools().size());
            RpcServer server =
                    new RpcServer(
                            loader.registry(),
                            (connection, tool, args) -> {
                                assertEquals("betterquesting", connection);
                                assertEquals("bq_status", tool);
                                return Collections.singletonMap("ok", true);
                            });
            server.handle(
                    request(
                            "initialize",
                            "{\"protocolVersion\":\"2025-11-25\",\"capabilities\":{},\"clientInfo\":{}}"));
            server.handle("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}");
            assertFalse(
                    server.handle(
                                    request(
                                            "tools/call",
                                            "{\"name\":\"bq_status\",\"arguments\":{}}"))
                            .getAsJsonObject("result")
                            .get("isError")
                            .getAsBoolean());
        }
        Files.delete(jar);
        try (PluginLoader loader = new PluginLoader(plugins)) {
            assertTrue(loader.registry().tools().isEmpty());
        }
        try (JarFile core = new JarFile(System.getProperty("core.jar"))) {
            assertNull(
                    core.getEntry("dev/minecraftagenttools/betterquesting/contract/BqTools.class"));
        }
    }

    private RpcServer endpoint(PluginLoader loader) {
        RpcServer server =
                new RpcServer(
                        loader.registry(),
                        (connection, name, args) -> {
                            throw new AssertionError("Fixture must not contact a game");
                        });
        server.handle(
                request(
                        "initialize",
                        "{\"protocolVersion\":\"2025-11-25\",\"capabilities\":{},\"clientInfo\":{}}"));
        server.handle("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}");
        return server;
    }

    private String request(String method, String params) {
        return "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\""
                + method
                + "\",\"params\":"
                + params
                + "}";
    }

    /** Compile an actual external provider, absent from the test/core classpath. */
    private Path fixture(Path plugins, String name, String id, String tool) throws Exception {
        Path classes = temp.newFolder("classes" + name).toPath();
        Path source = classes.resolve(name + ".java");
        Files.writeString(
                source,
                "package fixture; import java.util.*; import dev.minecraftagenttools.api.*; "
                        + "public class "
                        + name
                        + " implements ToolProvider { "
                        + "public String id() { return \""
                        + id
                        + "\"; } "
                        + "public List<Map<String,Object>> tools() { Map<String,Object> t = new HashMap<>(); "
                        + "t.put(\"name\",\""
                        + tool
                        + "\"); t.put(\"inputSchema\",Collections.singletonMap(\"type\",\"object\")); return Collections.singletonList(t); } "
                        + "public void validate(String t, Map<String,Object> a) {} "
                        + "public Map<String,Object> call(String t, Map<String,Object> a, ToolContext c) { "
                        + "Map<String,Object> r = new HashMap<>(); r.put(\"ok\",true); r.put(\"data\",\""
                        + id
                        + "\"); return r; } }");
        String api =
                Paths.get(
                                ToolProvider.class
                                        .getProtectionDomain()
                                        .getCodeSource()
                                        .getLocation()
                                        .toURI())
                        .toString();
        assertEquals(
                0,
                javax.tools.ToolProvider.getSystemJavaCompiler()
                        .run(
                                null,
                                null,
                                null,
                                "--release",
                                "8",
                                "-classpath",
                                api,
                                "-d",
                                classes.toString(),
                                source.toString()));
        Path output = plugins.resolve(name + ".jar");
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(output))) {
            entry(
                    jar,
                    "fixture/" + name + ".class",
                    Files.readAllBytes(classes.resolve("fixture/" + name + ".class")));
            entry(
                    jar,
                    "META-INF/services/" + ToolProvider.class.getName(),
                    ("fixture." + name).getBytes(StandardCharsets.UTF_8));
        }
        return output;
    }

    private void entry(JarOutputStream jar, String name, byte[] bytes) throws IOException {
        jar.putNextEntry(new JarEntry(name));
        jar.write(bytes);
        jar.closeEntry();
    }
}
