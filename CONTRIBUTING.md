# Contributing

Bug reports, small fixes and documented compatibility checks are welcome.
Use English or Russian. Include the project commit and exact mod versions when
reporting an integration problem; a modpack name alone is not enough.

## Build locally

Install a JDK 21 and set `JAVA_HOME` to it. Gradle resolves a Java 8 toolchain
for adapters and contracts; installing JDK 8 locally also works. The first
build downloads the legacy Forge toolchain and needs network access.

The historical NEI 2.6.44-GTNH development API is fetched from its official GitHub
release because those old Maven coordinates are unavailable. `verifyNeiBaseline`
checks its pinned SHA-256 before compiling. It remains a compile/test dependency
and is never embedded in the distributed adapter.

```sh
./gradlew build :mcp-server:installDist
```

On Windows, use `.\gradlew.bat` with the same tasks. This runs isolated tests,
format checks and distribution checks. It does not start Minecraft or a live MCP server.
The Java 8 `mcp-runtime`/Forge host implements the embedded HTTP endpoint. Its isolated
tests use fixture JVMs, including forced termination; see [embedded setup](docs/embedded-mcp.md).
The optional projects in `integrations/harness/` are always listed by Gradle.
Their Java/Forge tasks and widget JARs are enabled only when
`-PharnessApiJar=/path/to/harness-dev.jar` is supplied. Do not introduce an implicit
dependency on a neighboring checkout.

```sh
./gradlew spotlessApply       # Google Java Format (AOSP) and ktfmt
./gradlew spotlessCheck       # Check formatting without edits
./gradlew verifyBundle        # Check actual JARs and Java bytecode versions
./gradlew :mcp-server:test    # One module's isolated tests
```

Tests live next to the corresponding module under `src/test/java`. Reports are
written to `<module>/build/reports/tests/test/index.html`. CI covers the standalone
build on Linux and Windows and a separate full build with both Harness widgets.
Use `python3 scripts/fetch-harness.py` to obtain the pinned, checksummed release API,
then supply it with `-PharnessApiJar=.dependencies/minecraft-harness-forge-1.7.10-0.1.0-dev.jar`.
See [Versioning](VERSIONING.md) for packaging and release instructions.

## Design boundaries

- Keep `bridge-protocol` and `mcp-api` compatible with Java 8 and free of game/JSON/MCP SDK dependencies.
- The Java 21 core discovers providers from `plugins/*.jar`; it must work with an empty plugin directory.
- Forge JARs contain Java 8 contracts, never MCP providers, modern Gson or game classes.
- Access game objects only on the appropriate tick/render thread. Keep socket and file I/O off that thread.
- Validate world/session/dimension scope when queued work executes, not just when it arrives.
- Treat names, NBT and chat-card text as data. UI mutations use explicit callbacks.
- Return unavailable/truncated data explicitly. Never describe unloaded terrain as air.

Read the [architecture](docs/architecture.md) and [tool reference](docs/tools.md)
before changing a boundary. Add regression tests for behavior changes at the
relevant public seam; documentation-only edits do not need new tests.

## Pull requests

Explain the problem, the resulting behavior and how you verified it. Keep fixes
focused. Include configuration or documentation changes when installation changes.
Never commit tokens, game directories, private logs or exported personal plans.

Report in-game checks separately from unit tests, with versions and observed
results. Screenshots should come from the actual application and omit private
chat, player details and credentials. See the [validation record](docs/startup-check.md)
for the current boundary of runtime evidence.
