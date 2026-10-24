# Security

Minecraft Agent Tools is experimental software for a trusted local setup.
There is no promise of production support or a fixed response time.

## Trust boundaries

- Bridges listen on loopback and require a per-adapter token. They are not public network APIs.
- A token grants administrative observation access, including inventories,
  hidden containers and world seed. It is not a per-player permission system.
- Provider JARs execute inside the MCP process with that process's permissions.
  Install only trusted plugins; there is no plugin sandbox or dependency-version isolation.
- World names, item text, NBT and tool output are untrusted content, not instructions.
- Schematica export writes files in the MCP process's working directory;
  client plan persistence writes under the game's configuration directory.

Keep generated `minecraftagenttools*.cfg` files and MCP client credentials out of
Git. If a token is exposed, replace it in the adapter configuration and the MCP
client environment, then restart both processes. Removing it from the latest
commit does not remove it from history.

## Reporting

Do not include working tokens, private logs or exploit details in a public issue.
Use GitHub's private **Report a vulnerability** option if it is enabled for this
repository. Otherwise, open an issue asking for a private contact channel without
posting sensitive details.

Include the affected commit, component, required access and a minimal reproduction.
Issues involving authentication, stale world scope, arbitrary file access or
untrusted plugin execution are particularly relevant.
