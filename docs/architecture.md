# Architecture

Minecraft Agent Tools hosts MCP inside the Forge JVM through a Java 8 HTTP runtime.
Codex connects to the game instead of owning an MCP subprocess. Its core has no
built-in tools: provider JARs own integration contracts and routing. A Java 21
stdio launcher remains optional. See the [embedded setup](embedded-mcp.md).

![Block-by-block process diagram](assets/architecture.svg)

## Module boundaries

| Directory / module | Java | Responsibility |
| --- | --- | --- |
| `core/bridge-protocol` | 8 | Framing, detached data, sessions, scans, snapshots and markers; no external runtime dependencies. |
| `core/mcp-api` | 8 | `ToolProvider` and `ToolContext` SPI; no Minecraft, Gson or MCP SDK. |
| `core/mcp-runtime` | 8 | JSON-RPC/MCP sessions, HTTP endpoint, plugin discovery and bridge connections; uses the host's Gson. |
| `core/mcp-server` | 21 | Optional external stdio launcher and self-contained distribution. |
| `core/game-bridge` | 8 | Shared bounded loopback transport for game adapters; Minecraft supplies Gson at runtime. |
| `tools/*-tools` | 8 | Integration contracts and provider implementations, in separate packages. |
| `adapters/forge-*` | 8 | Game/mod API access on the appropriate server or client thread. |
| `adapters/forge-mcp-1.7.10` | 8 | Owns the MCP HTTP endpoint inside Minecraft and loads providers from config/. |
| `integrations/harness/schematica` | 8 | Optional schematic chat widgets, compiled only with an explicit Harness API JAR. |
| `integrations/harness/betterquesting` | 8 | Optional live quest cards and explicit quest-book navigation. |

Java 8 contracts can be shared with Forge without importing the external process's
implementation. The external launcher uses Gson 2.11.0; the embedded runtime and
game adapters use Minecraft's Gson 2.2.4. Game adapters include selected contracts;
the MCP host includes the runtime and SPI. Provider JARs remain separate files.
The build's `verifyBundle` task checks the resulting archives, including class
versions and forbidden classes.

## A request across the boundary

1. Minecraft initializes its host mod; the MCP client connects by HTTP and discovers tools.
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
Export writes `.schematic` and source JSON from the provider's worker thread.

Harness widgets live in `integrations/harness/`, in separate Gradle projects/JARs.
The projects are always discoverable; their Java/Forge tasks and bundle artifacts
are enabled by `harnessApiJar`. Without it they expose only base lifecycle tasks.
The standalone core and game adapters never acquire a Harness dependency.
Their buttons invoke explicit
Java callbacks scoped to the current plan and session. Old callbacks do not
become valid again after reconnecting; saved plans require explicit restoration.
The [Schematica guide](schematica.md) documents persistence and renderer limits.

Quest cards use the BetterQuesting adapter's `BqClient` UI seam on the client thread,
without a socket round trip or whole-quest NBT serialization during rendering.
Snapshots contain bounded task/reward/relation pages and the current scope's progress.
Opening the native quest book revalidates the scope at click time; saved chat cards
never automatically reconnect to a new world. See the [quest guide](betterquesting.md).

## MCP profile and verification

The implemented profile is MCP `2025-11-25` over Streamable HTTP or optional stdio:
`initialize`, `notifications/initialized`, `ping`, `tools/list` and `tools/call`.
Resources, prompts, SSE and other optional MCP capabilities are not
implemented. The core implements this profile directly rather than depending on
an MCP SDK.

Tests cover protocol validation, provider discovery and removal, scope expiry,
bounded transport, contracts and isolated adapter behavior. CI also inspects
distribution JARs. Game rendering, real mod compatibility and complete user
workflows still require [runtime validation](startup-check.md).

The embedded runtime targets the legacy JVM and Gson; its lifecycle belongs to
Minecraft. Per-integration adapters avoid mandatory mod dependencies; bounded
queries protect responsiveness but cannot make arbitrary third-party APIs fast.
