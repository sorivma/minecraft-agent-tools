# Getting started

## Build from source

Use a **JDK 21** to run Gradle. The wrapper is pinned to Gradle 8.8 with a distribution
checksum. Adapters, contracts and providers compile with a Java 8 toolchain;
Gradle can download it through the configured Foojay resolver.

```sh
git clone https://github.com/sorivma/minecraft-agent-tools.git
cd minecraft-agent-tools
./gradlew build :mcp-server:installDist
```

Windows PowerShell:

```powershell
$env:JAVA_HOME = 'C:/path/to/jdk-21'
.\gradlew.bat build :mcp-server:installDist
```

Linux/macOS, if JDK 21 is not your default:

```sh
export JAVA_HOME=/absolute/path/to/jdk-21
./gradlew build :mcp-server:installDist
```

The first build downloads dependencies from Maven Central, the Gradle Plugin
Portal and GTNH repositories, plus the Forge/Minecraft development toolchain.
It may take several minutes. A Java 8 JVM cannot run this build, even though most
output JARs target Java 8.

These tasks build and test artifacts; they do not start the game or a live MCP
endpoint. No neighboring project is required. Linux and Windows are configured
in CI; macOS is not part of the CI matrix.

## Choose integrations

The complete build produces this layout (version suffixes omitted for readability):

```text
build/bundle/
├── core.jar
├── plugins/
│   ├── minecraft-tools.jar
│   ├── nei-tools.jar
│   ├── gregtech-tools.jar
│   └── schematica-tools.jar
├── forge/
│   └── minecraft-agent-tools-forge-1.7.10-*.jar
├── client-mods/
│   ├── minecraft-agent-tools-nei-forge-1.7.10-*.jar
│   └── minecraft-agent-tools-schematica-forge-1.7.10-*.jar
├── gregtech-mods/
│   └── minecraft-agent-tools-gregtech-forge-1.7.10-*.jar
├── LICENSE
├── THIRD_PARTY_NOTICES.md
├── GETTING_STARTED.md
└── licenses/
```

Copy the bundle to your chosen installation directory before customizing it:
running `bundle` again refreshes the build output and restores all providers.
Keep only the provider JARs you want in that installation's `plugins/`.
Removing a provider requires an MCP restart. Do not copy provider JARs or
`core.jar` into Minecraft's `mods/`.

| Integration | Adapter destination | Required mod profile | Configuration file | Default port |
| --- | --- | --- | --- | ---: |
| Minecraft | Server `mods/` | Forge 1.7.10 | `minecraftagenttools.cfg` | 25576 |
| NEI | Client `mods/` | NEI GTNH 2.6.44 compile baseline | `minecraftagenttoolsnei.cfg` | 25577 |
| GregTech | Server, or client with integrated server | GT5-Unofficial 5.09.54.183 / StructureLib 1.4.45 API profile | `minecraftagenttoolsgregtech.cfg` | 25578 |
| Schematica | Client `mods/` | Schematica 1.12.6-GTNH API profile | `minecraftagenttoolsschematica.cfg` | 25579 |

These are implementation baselines, not a claim that every listed combination
has been verified in a live game. Install third-party mods and their dependencies
separately. The vanilla observation adapter does not require a client mod.
Do not put the NEI or Schematica adapters on a dedicated server.

## Connect an MCP client

1. Start the game/server with the chosen adapter. It generates its configuration
   under the game's `config/` directory.
2. Read `bridge.token` and `bridge.port` from the corresponding file. Each adapter
   has its own token; do not interchange them.
3. Configure your MCP client to launch **Java 21 directly**, with the absolute path
   to `core.jar`. Do not launch via Gradle: Gradle's stdout is not MCP traffic.
4. Restart/reconnect the client after changing provider JARs or environment settings.

For clients using an `mcpServers` JSON configuration, a minimal Minecraft setup is:

```json
{
  "mcpServers": {
    "minecraft": {
      "command": "/absolute/path/to/jdk-21/bin/java",
      "args": ["-jar", "/absolute/path/to/bundle/core.jar"],
      "env": {
        "MAT_PORT": "25576",
        "MAT_TOKEN": "copy-the-generated-server-token-here"
      }
    }
  }
}
```

On Windows, use paths such as `C:/path/to/jdk-21/bin/java.exe` and
`C:/path/to/bundle/core.jar`. Client configuration formats differ; this example
is not a universal configuration file. Keep real credentials in the client's
private configuration, outside this repository.

| Integration | Environment variables |
| --- | --- |
| Minecraft | `MAT_TOKEN`, optional `MAT_PORT` (defaults to 25576) |
| NEI | `MAT_NEI_TOKEN`, `MAT_NEI_PORT` |
| GregTech | `MAT_GREGTECH_TOKEN`, `MAT_GREGTECH_PORT` |
| Schematica | `MAT_SCHEMATICA_TOKEN`, `MAT_SCHEMATICA_PORT` |

Both variables are required for optional bridges. The MCP process must run on
the same machine as every adapter it contacts; all bridges use loopback.
A remote dedicated server and your local client therefore need separate MCP
processes on their respective machines for server and client integrations.

The default provider directory is `plugins/` next to `core.jar`. For the Gradle
application distribution, it is next to `lib/`. Override it with
`--plugins /absolute/path/to/plugins` or `MAT_PLUGINS`; the argument takes priority.
An empty directory starts a core with no tools and requires no game credentials.

## First calls

- Minecraft: `list_sessions`, then `observe` with returned `session`, `dimension`
  and player UUID.
- NEI: `nei_status`; wait for `ready: true`, then discover handlers.
- GregTech: `gt_status`; select a dimension/session and discover recipe maps.
- Schematica: `schematic_status`, then `schematic_list` or create a small plan.

Scopes are independent. Never reuse a Minecraft session for NEI, GregTech or
Schematica. Rediscover after a world transition or restart.

## Optional Harness widgets

The core, providers and Forge adapters are standalone. Only the optional chat
widget extension needs a separately built Harness dev JAR with
`WidgetCanvas.custom(...)` support:

```sh
./gradlew build "-PharnessApiJar=/absolute/path/to/harness-dev.jar"
```

This explicitly includes `harness-schematica-1.7.10` and adds its output to
`build/bundle/harness-mods/`. Install it on the client alongside Harness and the
Schematica adapter. The build never looks up or builds a sibling checkout.
In PowerShell, use `.\gradlew.bat` and keep the entire `-P...` argument quoted.
Saved-plan restoration currently uses an explicit Harness widget callback;
without widgets, exported JSON can be resubmitted as a new plan with a fresh scope.

## Troubleshooting

| Symptom | Check |
| --- | --- |
| Gradle says the JVM is too old | Set `JAVA_HOME` to JDK 21, then check `gradlew --version`. |
| Dependency or toolchain download fails | Check access to the repositories; retry after resolving connectivity. Do not disable TLS verification. |
| No tools appear | Check the selected `plugins/` directory, service JARs and startup stderr. Restart after changes. |
| Tools appear but calls fail | Discovery does not connect to the game. Check the game is running and each token/port matches. |
| Wrong or expired session | Call the integration's status/discovery tool again and use its new scope. |
| Bridge busy / game thread timeout | Narrow the query and retry after the game resumes ticking. |
| Terrain is incomplete | Inspect pagination and unloaded-chunk indicators. The adapter does not load chunks. |
| Mod API or renderer is unavailable | Check the exact installed versions and the adapter's reported limitations. |

Tokens grant administrative observation access, including hidden inventories
and world seed. Install only trusted provider JARs; they execute as local code.
See the repository's
[security policy](https://github.com/sorivma/minecraft-agent-tools/blob/main/SECURITY.md),
[tool reference](https://github.com/sorivma/minecraft-agent-tools/blob/main/docs/tools.md)
and [Schematica guide (RU)](https://github.com/sorivma/minecraft-agent-tools/blob/main/docs/schematica.md).
