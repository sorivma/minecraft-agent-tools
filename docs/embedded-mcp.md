# MCP inside Minecraft

`forge-mcp-1.7.10` hosts MCP in Minecraft's own JVM. Codex connects over Streamable
HTTP and launches no MCP process. Closing or forcibly killing Minecraft removes
the endpoint even if shutdown hooks do not run. The host and shared runtime are
Java 8 compatible and use Minecraft's Gson 2.2.4. The independent Java 21 stdio
launcher remains an optional alternative for external installations.

## Install

1. Build with `./gradlew build` (JDK 21 runs Gradle).
2. Copy `build/bundle/forge/minecraft-agent-tools-mcp-forge-1.7.10-*.jar` to `mods/`.
3. Install the desired game adapters and their required mods as usual.
4. Copy desired `build/bundle/plugins/*.jar` to `config/minecraftagenttools/plugins/`.
   These are provider files, not Forge mods; do not put them in `mods/`.
5. Start Minecraft. The host creates `config/minecraftagenttoolsmcp.cfg` with
   `http.port` (default `25581`), a private `http.token`, and `http.plugins`.
6. With a discovery-capable Minecraft Harness installed, open a local world:
   Harness creates its `.codex/config.toml` automatically. Update both mods when
   upgrading from versions without discovery. For other clients, configure the
   URL and token below, then reconnect.

Codex `config.toml`:

```toml
[mcp_servers.minecraft]
url = "http://127.0.0.1:25581/mcp"
startup_timeout_sec = 20
tool_timeout_sec = 30

[mcp_servers.minecraft.http_headers]
Authorization = "Bearer <http.token from minecraftagenttoolsmcp.cfg>"
```

Replace the old `minecraft` stdio entry, including `command`, `args` and `env`.
Do not combine two transports in one entry. The HTTP host reads each installed
adapter's port/token from `config/minecraftagenttools*.cfg` during post-init.
Bridge credentials stay in Minecraft; Codex needs only the HTTP credential.
Restart Minecraft after changing provider JARs or adapter credentials.

An absent provider JAR registers no tools. An empty directory is valid. The host
does not depend on Harness; widgets remain separate optional integration JARs.
The endpoint is available while Minecraft runs. Game reads still require an active
world and each adapter's fresh session/dimension. Closing a world invalidates game
scopes without rebinding old commands or cards to the next world.

## Client configuration discovery

The host implements the data-only [Forge IMC discovery v1 contract](mcp-discovery.md).
Any mod can request its connection settings; no Harness dependency is required.
Requests use `agent-mcp:discover/v1` during initialization. The host remembers
requesters during `IMCEvent`, then replies with `agent-mcp:server/v1` runtime
messages after the HTTP endpoint has successfully started in post-init. Clients
collect replies at load-complete, independent of mod ordering.

The advertisement contains the actual bound port, HTTP Bearer authorization and
timeouts for the single host endpoint. Game-bridge credentials and provider
implementations are not exported. Provider JARs continue to use `mcp-api`; they
need no separate client-configuration entry.

## Runtime boundaries

The mod includes the shared MCP runtime, `mcp-api` and Java 8 bridge protocol,
with no provider implementation, Minecraft classes or Gson implementation.
Provider discovery uses the SPI and isolated service-file lookup. HTTP workers
invoke providers off the game thread. Authenticated internal loopback bridges
queue game access on the client/server tick thread. These sockets create no process.
Schematica exports are written under the hosting JVM's `schematics/` directory.

The endpoint implements MCP `2025-11-25` with JSON HTTP responses, initialization,
session IDs, notifications, `ping`, `tools/list`, `tools/call` and session deletion.
GET returns `405` because SSE is not offered. It binds only `127.0.0.1`, validates
Host/Origin and requires a Bearer token. Sessions are independent, capped at 32,
and expire after 30 minutes idle. Payloads are bounded to 4 MiB and worker queues
are bounded. The endpoint is intended for local clients.

References: [MCP Streamable HTTP](https://modelcontextprotocol.io/specification/2025-11-25/basic/transports)
and [OpenAI Docs for Codex MCP](https://developers.openai.com/codex/mcp/).

Isolated Java 8/Gson 2.2.4 tests exercise HTTP-to-game-tick dispatch and verify that
normal exit and forced termination of a fixture JVM leave no MCP listener.
Native GTNH compatibility still requires a user-run game check.
