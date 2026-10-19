package dev.minecraftagenttools.mcp.bridge;

import static dev.minecraftagenttools.protocol.data.Data.map;

import com.google.gson.*;
import dev.minecraftagenttools.protocol.transport.Frames;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public final class BridgeClient {
    private final int port;
    private final String token;
    private final Gson gson = new Gson();

    public BridgeClient(int port, String token) {
        this.port = port;
        this.token = token;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> call(String tool, Map<String, Object> args) throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 2000);
            socket.setSoTimeout(10000);
            Frames.write(
                    new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8),
                    gson.toJson(
                            map("version", 1, "token", token, "tool", tool, "arguments", args)));
            String line =
                    Frames.read(
                            new BufferedReader(
                                    new InputStreamReader(
                                            socket.getInputStream(), StandardCharsets.UTF_8)));
            if (line == null) throw new IOException("Bridge closed connection");
            return gson.fromJson(JsonParser.parseString(line).getAsJsonObject(), Map.class);
        }
    }
}
