# Forge MCP client discovery v1

This is a data-only Forge 1.7.10 IMC contract for endpoint owners and clients.
It requires no shared Java library or dependency on Minecraft Harness. Version 1
supports Streamable HTTP; non-participating mods simply do not advertise servers.

1. During `FMLInitializationEvent`, clients send an empty string message with key
   `agent-mcp:discover/v1` to loaded mods using `FMLInterModComms.sendMessage`.
2. In `FMLInterModComms.IMCEvent`, endpoint owners remember each string request's
   `message.getSender()`. This event precedes post-init in Forge 1.7.10.
3. After successfully starting their endpoint, no later than the end of their
   `FMLPostInitializationEvent`, owners call
   `FMLInterModComms.sendRuntimeMessage(this, sender, "agent-mcp:server/v1", json)`.
   `this` is the registered `@Mod` instance. One reply advertises one endpoint;
   multiple endpoints require distinct local IDs. Failed endpoints are not advertised.
4. In `FMLLoadCompleteEvent`, clients call `fetchRuntimeMessages(this)` to collect
   advertisements after every mod's post-init has finished.

The response string is a JSON object:

```json
{
  "id": "minecraft",
  "transport": "streamable-http",
  "url": "http://127.0.0.1:25581/mcp",
  "http_headers": { "Authorization": "Bearer <private HTTP token>" },
  "startup_timeout_sec": 20,
  "tool_timeout_sec": 30
}
```

Required fields `id`, `transport`, `url` are strings. The local ID contains ASCII
letters, digits and hyphens and is unique within its owning mod. The URL is an
absolute HTTP(S) address without user info or fragment. Optional `http_headers`
maps HTTP header names to string values without CR/LF. Optional timeouts are
positive integer seconds, defaulting to 20 and 30. Unknown fields are ignored.
Advertise actual runtime addresses and credentials, never hardcoded defaults.
Endpoint configuration changes require restarting the game.

Clients namespace IDs by the actual IMC sender to avoid collisions. Harness uses
`harness_<sender-modid>__<id>` and accepts sender IDs containing ASCII letters,
digits, hyphens and underscores. Duplicate or invalid advertisements are errors.
Credentials must not appear in diagnostics. The advertisement contains no client
TOML, shell commands or approval policies; each client renders its own settings.

This host advertises one endpoint with all installed provider JARs. Its reply
uses the bound port from `HttpEndpoint.port()` and only the host's HTTP token.
Provider JARs do not advertise separate connections or game-bridge credentials.
