package dev.minecraftagenttools.mcp.http;

import static org.junit.Assert.*;

import com.google.gson.*;
import org.junit.Test;

public class McpDiscoveryTest {
    @Test
    public void advertisesActualPortAndCredentialWithoutGameBridgeSecrets() {
        String token = "private-token-with-a-quote-\"-and-a-backslash-\\";
        JsonObject server =
                new JsonParser().parse(McpDiscovery.advertisement(32123, token)).getAsJsonObject();
        assertEquals("minecraft", server.get("id").getAsString());
        assertEquals("streamable-http", server.get("transport").getAsString());
        assertEquals("http://127.0.0.1:32123/mcp", server.get("url").getAsString());
        assertEquals(
                "Bearer " + token,
                server.getAsJsonObject("http_headers").get("Authorization").getAsString());
        assertEquals(6, server.entrySet().size());
        assertEquals(1, server.getAsJsonObject("http_headers").entrySet().size());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsHeaderInjection() {
        McpDiscovery.advertisement(25581, "long-enough-private-token-but-with\r\na-second-header");
    }
}
