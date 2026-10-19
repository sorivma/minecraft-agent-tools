package dev.minecraftagenttools.mcp;

import com.google.gson.*;
import dev.minecraftagenttools.mcp.bridge.BridgeConnections;
import dev.minecraftagenttools.mcp.plugins.PluginLoader;
import dev.minecraftagenttools.mcp.rpc.RpcServer;
import dev.minecraftagenttools.protocol.transport.Frames;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

public final class Main {
    public static void main(String[] args) throws Exception {
        String configured = System.getenv("MAT_PLUGINS");
        Path directory =
                configured == null ? PluginLoader.defaultDirectory() : Paths.get(configured);
        if (args.length == 2 && args[0].equals("--plugins")) directory = Paths.get(args[1]);
        else if (args.length != 0)
            throw new IllegalArgumentException("Usage: java -jar core.jar [--plugins directory]");
        try (PluginLoader plugins = new PluginLoader(directory)) {
            run(new RpcServer(plugins.registry(), new BridgeConnections(System.getenv())));
        }
    }

    private static void run(RpcServer server) throws IOException {
        Reader input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        Writer output = new OutputStreamWriter(System.out, StandardCharsets.UTF_8);
        Gson gson = new GsonBuilder().serializeNulls().create();
        String line;
        while ((line = Frames.read(input)) != null) {
            JsonObject response = server.handle(line);
            if (response != null) Frames.write(output, gson.toJson(response));
        }
    }
}
