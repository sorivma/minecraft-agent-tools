# Release review · 0.1.0

Reviewed on 2026-10-25 before the first publication. This records a focused
release-readiness review, not an exhaustive audit or certification of in-game
compatibility.

## Findings and fixes

| Finding | Change | Evidence |
| --- | --- | --- |
| NEI `2.6.44-GTNH` no longer resolves from GTNH Nexus. Local caches masked the failure seen in GitHub Actions. | Resolve that exact module from the official GitHub release through an exclusive Ivy repository. Verify the development JAR against a pinned SHA-256 before compilation. | Nexus returned 404 for both POM and development JAR. The official release download matched the cached baseline, and the updated build passed. |
| Forge mods advertised `0.1.0-SNAPSHOT`, while MCP advertised a separately hardcoded `0.1.0`. | Use `gradle.properties` as the shared version source, generate the compile-time `BuildVersion` constant, and stamp JAR manifests. | All 44 release JAR manifests report `0.1.0`; Forge annotations and MCP server information compile from the generated constant. |
| The external MCP fat JAR resolved its runtime classpath eagerly during task configuration. A sibling project's clean CI failure exposed the risk of trusting existing project JARs. | Defer the classpath expansion while preserving explicit build dependencies. | A full clean build passed. A further Gradle invocation started after all 44 generated module JARs were removed and rebuilt the complete release successfully. |
| Optional Harness widgets were outside the original CI coverage, and release assets had no automated publication contract. | Add a full integration job against the pinned Harness release, explicit artifact completeness checks, SHA-256 checksums, and a version-checked release workflow. | Both widget test suites passed; packaging produced 50 release files, including 44 JARs and `SHA256SUMS`. |
| CodeQL flagged three pagination loops for comparing an `int` index with a `long` bound. The bound was already capped by an `int` collection size, so index overflow was not possible. | Express the capped endpoint as an `int` before each loop, resolving the static-analysis ambiguity without changing behavior. | A regression test covers near-maximum and maximum integer offsets for item, fluid and block requirements; the focused BetterQuesting suite passes all 22 tests. |

The NEI API remains a compile/test dependency and is not embedded in the adapter.
Release staging selects declared task outputs so stale files in `build/libs/`
cannot accidentally become published assets.

## Verification performed

- **131 isolated tests:** zero failures, errors or skipped tests, including both
  optional Harness integrations.
- A subsequent pagination regression brings the repository total to **132 tests**;
  all 22 tests in the affected BetterQuesting adapter passed after that change.
- Standalone build and external launcher installation distribution.
- Full clean build with all integrations: 262 tasks, 254 executed.
- Fresh Gradle configuration with all 44 module JARs absent: full release rebuilt
  successfully, with 50 tasks executed.
- Spotless formatting checks and inspection of 14 runtime bundle JARs for Java
  bytecode compatibility, provider registration and module boundaries.
- All 44 release JAR manifests and project-license entries; all release checksums.
- GitHub Actions syntax validation with actionlint and `git diff --check`.

These are local verification results. The authoritative hosted results and
publication records are available in [GitHub Actions](https://github.com/sorivma/minecraft-agent-tools/actions)
and [Releases](https://github.com/sorivma/minecraft-agent-tools/releases).

## Scope and limits

Source inspection covered the HTTP endpoint's authentication, Host/Origin checks,
session and payload bounds, provider loading, bridge queues and cancellation, and
Schematica persistence/export paths. It also covered build dependencies, artifact
contents and release permissions. This was not a penetration test, exhaustive
concurrency analysis or audit of upstream mods.

Minecraft and a live user backend were not launched. Existing isolated tests use
local fixtures; their success does not establish end-to-end behavior in arbitrary
Forge/GTNH installations. The existing [runtime validation record](startup-check.md)
remains the source for observations made in the game.

Tokens still grant broad observation access, provider JARs remain trusted code,
and `0.x` APIs remain experimental. See [Security](../SECURITY.md) and
[Versioning](../VERSIONING.md) for those explicit boundaries.
