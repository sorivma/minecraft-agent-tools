# Changelog

All modules share the version in `gradle.properties`. See [Versioning](VERSIONING.md)
for compatibility guarantees and the release process.

## [0.1.0] - 2026-10-25

First published experimental release.

### Added

- Embedded Java 8 MCP HTTP host with bearer authentication, bounded sessions,
  loopback binding and Minecraft-owned lifetime.
- Removable Minecraft, NEI, GregTech, Schematica and BetterQuesting tool providers.
- Java 8 Forge 1.7.10 adapters, session-scoped bridge requests and Forge IMC discovery.
- Optional Java 21 stdio launcher and optional Harness schematic/quest widgets.
- Standalone Linux/Windows CI, full widget integration checks, release packaging,
  artifact checksums and build provenance for public releases.
- Versioning, contribution, security and issue-reporting policies.

### Fixed

- Resolve the legacy NEI 2.6.44 development API from its official GitHub release,
  with a pinned SHA-256 check, after the original Maven coordinates disappeared.
- Generate Forge metadata and MCP server versions from the same Gradle version.

### Release boundaries

- Experimental `0.x` APIs; no compatibility promise for arbitrary modpack versions.
- Automated tests and bundle inspections do not replace in-game validation.
- Ghost plans and markers guide players; autonomous movement/building is not implemented.

[0.1.0]: https://github.com/sorivma/minecraft-agent-tools/releases/tag/v0.1.0
