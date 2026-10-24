package dev.minecraftagenttools.forge.mcp;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.*;
import dev.minecraftagenttools.mcp.bridge.BridgeConnections;
import dev.minecraftagenttools.mcp.http.HttpEndpoint;
import dev.minecraftagenttools.mcp.http.McpDiscovery;
import dev.minecraftagenttools.mcp.plugins.PluginLoader;
import dev.minecraftagenttools.mcp.rpc.RpcServer;
import java.io.*;
import java.util.*;
import java.util.regex.*;
import net.minecraftforge.common.config.Configuration;

/** Owns the MCP endpoint in Minecraft's JVM; never starts a server process. */
@Mod(
        modid = "minecraftagenttoolsmcp",
        name = "Minecraft Agent Tools MCP Host",
        version = "0.1.0-SNAPSHOT",
        acceptableRemoteVersions = "*")
public final class McpHostMod {
    private File configDirectory, pluginDirectory;
    private int port;
    private String token;
    private PluginLoader plugins;
    private HttpEndpoint endpoint;
    private final Set<String> discoveryClients = new LinkedHashSet<String>();

    @Mod.EventHandler
    public void discover(FMLInterModComms.IMCEvent event) {
        for (FMLInterModComms.IMCMessage message : event.getMessages()) {
            if (McpDiscovery.DISCOVER.equals(message.key) && message.isStringMessage())
                discoveryClients.add(message.getSender());
        }
    }

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) throws IOException {
        configDirectory = event.getModConfigurationDirectory().getCanonicalFile();
        Configuration config = new Configuration(event.getSuggestedConfigurationFile());
        config.load();
        port = config.getInt("port", "http", 25581, 1024, 65535, "Loopback-only MCP HTTP port");
        token =
                config.getString(
                        "token",
                        "http",
                        UUID.randomUUID().toString() + UUID.randomUUID().toString(),
                        "Private HTTP Bearer token for the MCP client");
        String directory =
                config.getString(
                        "plugins",
                        "http",
                        "minecraftagenttools/plugins",
                        "Provider JAR directory, relative to config/ unless absolute");
        config.save();
        pluginDirectory = new File(directory);
        if (!pluginDirectory.isAbsolute()) pluginDirectory = new File(configDirectory, directory);
        pluginDirectory = pluginDirectory.getCanonicalFile();
        if (!pluginDirectory.isDirectory() && !pluginDirectory.mkdirs())
            throw new IOException("Cannot create MCP provider directory " + pluginDirectory);
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) throws IOException {
        plugins = new PluginLoader(pluginDirectory.toPath());
        try {
            BridgeConnections bridges = new BridgeConnections(bridgeEnvironment());
            endpoint =
                    new HttpEndpoint(port, token, () -> new RpcServer(plugins.registry(), bridges));
        } catch (IOException | RuntimeException e) {
            plugins.close();
            throw e;
        }
        Runtime.getRuntime().addShutdownHook(new Thread(this::close, "mat-mcp-shutdown"));
        // IMC requests arrive before post-init. Advertise only after the endpoint has bound.
        for (String client : discoveryClients)
            FMLInterModComms.sendRuntimeMessage(
                    this,
                    client,
                    McpDiscovery.SERVER,
                    McpDiscovery.advertisement(endpoint.port(), token));
        discoveryClients.clear();
    }

    private Map<String, String> bridgeEnvironment() {
        Map<String, String> result = new HashMap<String, String>();
        File[] files = configDirectory.listFiles();
        if (files == null) return result;
        Pattern name = Pattern.compile("minecraftagenttools([a-z0-9_]*).cfg");
        for (File file : files) {
            Matcher match = name.matcher(file.getName());
            if (!file.isFile() || !match.matches() || match.group(1).equals("mcp")) continue;
            Configuration config = new Configuration(file);
            config.load();
            if (!config.hasKey("bridge", "port") || !config.hasKey("bridge", "token")) continue;
            String prefix =
                    match.group(1).isEmpty()
                            ? "MAT_"
                            : "MAT_" + match.group(1).toUpperCase(Locale.ROOT) + "_";
            result.put(prefix + "PORT", config.get("bridge", "port", 0).getString());
            result.put(prefix + "TOKEN", config.get("bridge", "token", "").getString());
        }
        return result;
    }

    private void close() {
        if (endpoint != null) endpoint.close();
        if (plugins != null)
            try {
                plugins.close();
            } catch (IOException ignored) {
            }
    }
}
