# Versioning and releases

Minecraft Agent Tools uses [Semantic Versioning 2.0.0](https://semver.org/).
All modules in this repository share one version from `gradle.properties` and
one Git tag, `vMAJOR.MINOR.PATCH`. Minecraft's version remains in adapter names;
`1.7.10` is a platform identifier, not this project's release version.

## Compatibility promise

- **0.x:** experimental API. A minor version may break Java APIs, tool schemas,
  configuration or installation layout. Patch versions preserve those contracts.
- **1.x and later:** breaking public contract changes require a major release;
  backward-compatible capabilities increment minor; fixes increment patch.
- The public contract includes `mcp-api`, `bridge-protocol`, game-bridge entry
  points, provider discovery, MCP tool names/schemas and Forge IMC discovery.
- Internal classes are not a compatibility guarantee. World/game runtime support
  is documented independently; a passing CI build does not establish modpack compatibility.
- Preview tags use `vX.Y.Z-alpha.N`, `-beta.N` or `-rc.N`. Never move published
  tags or replace release files. Correct a bad release with a new patch version.

Harness and Mira version independently. This initial compatibility set is
Agent Tools **0.1.0**, Harness **0.1.0** and Mira **0.1.0**. Optional widget builds
consume the pinned Harness release declared in `scripts/fetch-harness.py`.
Update that pin only after verifying the integration tests.

## Release procedure

1. Update `version` in `gradle.properties`, `CHANGELOG.md`, and
   `docs/releases/<version>.md` in a pull request. Describe breaking changes and
   migration steps explicitly. Keep the development version ahead of the last release.
2. Run the standalone build and the full integration build. The CI workflow
   performs both on every pull request; standalone builds cover Linux and Windows.
3. Merge after required checks pass, then create and push an annotated tag:
   `git tag -a vX.Y.Z -m "Release X.Y.Z"` and `git push origin vX.Y.Z`.
4. The release workflow rejects mismatched tags, re-runs both CI variants, checks
   SHA-256 digests, attests assets for public repositories, and publishes only
   after all jobs pass. Inspect the published workflow and asset list.

The release includes an installation bundle, each runtime/API/development/source
JAR, licensing files, release notes and `SHA256SUMS`. Source archives are also
provided by GitHub. Library JARs are developer artifacts; they do not belong in
Minecraft's `mods/` directory.

To reproduce packaging locally:

```sh
python3 scripts/fetch-harness.py
./gradlew -PharnessApiJar=.dependencies/minecraft-harness-forge-1.7.10-0.1.0-dev.jar releaseChecksums
```

The download helper uses GitHub CLI. Authenticate `gh` locally, or supply
`GH_TOKEN` in automation. Public release assets require no special cross-repository
credentials. For a private fork, set the `ECOSYSTEM_TOKEN` Actions secret to a token
with read access to the pinned upstream repository.

Verify a downloaded release with `sha256sum --check SHA256SUMS` from a directory
containing all assets, and verify provenance with
`gh attestation verify <artifact> --repo sorivma/minecraft-agent-tools`.
Checksums detect file corruption; provenance ties a file to the workflow identity.
