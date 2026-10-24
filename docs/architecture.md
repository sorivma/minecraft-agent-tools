# Architecture

Minecraft Agent Tools connects a modern MCP client to a legacy Forge runtime
without putting the MCP stack inside the game. Its core has no built-in tools:
provider JARs own the integration contracts and routing.

![Block-by-block process diagram](assets/architecture.svg)

## Module boundaries

| Directory / module | Java | Responsibility |
| --- | --- | --- |
| `core/bridge-protocol` | 8 | Framing, detached data, sessions, scans, snapshots and markers; no external runtime dependencies. |
| `core/mcp-api` | 8 | `ToolProvider` and `ToolContext` SPI; no Minecraft, Gson or MCP SDK. |
| `core/mcp-server` | 21 | JSON-RPC/MCP lifecycle, plugin discovery, routing and lazy bridge connections. |
| `core/game-bridge` | 8 | Shared bounded loopback transport for game adapters; Minecraft supplies Gson at runtime. |
| `tools/*-tools` | 8 | Integration contracts and provider implementations, in separate packages. |
| `adapters/forge-*` | 8 | Game/mod API access on the appropriate server or client thread. |
| `adapters/harness-schematica-1.7.10` | 8 | Optional chat widgets, included only when a Harness API JAR is explicitly supplied. |

Java 8 contracts can be shared with Forge without importing the external process's
implementation. The core uses Gson 2.11.0; game adapters use Minecraft's Gson.
Forge artifacts include selected contract classes, not entire provider JARs.
The build's `verifyBundle` task checks the resulting archives, including class
versions and forbidden classes.

## A request across the boundary

1. The MCP client initializes the endpoint and discovers tools.
2. The core looks up the owning provider. That provider validates the arguments.
3. `ToolContext.callBridge` resolves the named bridge's environment settings on
   demand and opens an authenticated loopback request.
4. A socket worker parses and validates the request, then queues detached arguments.
5. On an END server/client tick, the adapter checks the current world scope and
   executes the operation. Render callbacks own preview drawing.
6. A detached result returns to the socket worker for serialization and then to the
   MCP client as text plus `structuredContent`.

The game thread does not wait for socket I/O. Queues hold up to 32 pending jobs;
adapters process up to four per tick with a 5 ms inter-job time budget.
Jobs expire after five seconds. These are bounds on
queued work, **not hard deadlines on a single mod API call**. Slow third-party
getters can still delay the game thread.

## Provider discovery

The core scans one level of `plugins/*.jar` at startup. Each provider declares
`META-INF/services/dev.minecraftagenttools.api.ToolProvider`. Its class must
implement the [SPI](../core/mcp-api/src/main/java/dev/minecraftagenttools/api/ToolProvider.java)
and have a public no-argument constructor.

- No plugin JAR means no tools from that plugin, even if provider classes happen
  to exist on another classpath.
- An empty or missing directory is valid and needs no bridge token.
- Duplicate provider IDs or tool names, broken providers and incompatible class
  versions fail startup rather than leaving a partially registered endpoint.
- Constructors, `tools()` and `instructions()` must not connect to the game or
  write to stdout. Stdout belongs exclusively to MCP.
- Plugins share a classloader. This is an extension mechanism for trusted local
  code, not a sandbox or a solution for conflicting dependency versions.

New integrations implement the SPI and ship their own adapter/contract. The
[Minecraft provider](../tools/minecraft-tools/src/main/java/dev/minecraftagenttools/minecraft/provider/MinecraftToolProvider.java)
is a small example. No core registration table needs editing.

## Scope and data quality

Each adapter owns its world/session identifiers. Server observations also identify
the player. A queued request is checked when it executes, so changing worlds
cannot make old coordinates or entity IDs silently refer to a new world.
Markers are scoped and revoked on relevant lifecycle changes.

Queries inspect loaded chunks and do not force loading or generation. Missing
cells are unknown, not air. Pagination describes separately captured pages;
it is not an atomic world snapshot. Results report truncation and unavailable
mod data instead of silently replacing them with zero or empty values.

World names, item descriptions, chat-card text and NBT are untrusted data.
They are never interpreted as commands to mutate the world.

## Schematica and optional UI

Authoring changes ghost plans, not real blocks. Plans have independent IDs and
revisions, materials and optional groups. Client persistence uses a separate ordered
I/O worker; game objects and preview renderers remain on their owning threads.
Export writes `.schematic` and source JSON from the external MCP process.

Harness widgets are a separate opt-in extension. Their buttons invoke explicit
Java callbacks scoped to the current plan and session. Old callbacks do not
become valid again after reconnecting; saved plans require explicit restoration.
The [Schematica guide](schematica.md) documents persistence and renderer limits.

## MCP profile and verification

The implemented profile is MCP `2025-11-25` over stdio:
`initialize`, `notifications/initialized`, `ping`, `tools/list` and `tools/call`.
Resources, prompts, HTTP transport and other optional MCP capabilities are not
implemented. The core implements this profile directly rather than depending on
an MCP SDK.

Tests cover protocol validation, provider discovery and removal, scope expiry,
bounded transport, contracts and isolated adapter behavior. CI also inspects
distribution JARs. Game rendering, real mod compatibility and complete user
workflows still require [runtime validation](startup-check.md).

The main tradeoffs are explicit: separate processes avoid legacy dependency
conflicts; per-integration adapters avoid mandatory mod dependencies; bounded
queries protect responsiveness but cannot make arbitrary third-party APIs fast.
