# Runtime validation record

This is a record of the local checks documented on **2026-10-20**, not a claim
that the current checkout has passed every game scenario. The game was not
launched during repository publication preparation.

## Recorded environment and outcome

| Check | Recorded result | What it establishes |
| --- | --- | --- |
| GTNH 2.9.0-RC-2 client startup, Forge 10.13.4.1614 | Reached the main menu; 328 mods loaded. | The installed adapters and widget did not prevent this client from starting. |
| Schematica bridge status | `schematic_status` returned `ok: true` and `unavailable: null`. | The installed bridge could dispatch a status request on the client tick. |
| Schematica mod dependency | Corrected `required-after:schematica` to `required-after:Schematica`; added `ModDependencyTest`. | The dependency matches the case-sensitive ID declared by the inspected mod. |

The latest recorded launch used the neighboring Harness project's existing
`:forge-1.7.10:runClient` task. Its local LWJGL3ify setup relaunched the game on
Java 21. An earlier check used Prism. Those launcher/configuration details belong
to that local installation; they are not prerequisites or portable launch
instructions for this standalone repository.

No current source commit or reproducible full mod manifest was captured with that
record. Treat it as limited historical evidence, not a release certification.

## Still to verify in-game

- Dedicated-server loading without client-only classes or a mandatory client mod.
- Complete MCP connection and real observation/recipe calls in the intended client.
- Agreement between observations and actual player, inventory, block and machine state.
- Rejection of old scopes and callbacks after changing worlds, dimensions or reconnecting.
- Particle visibility, expiry and cleanup for the correct player.
- Multiple Schematica plans, mod-specific textures, layers and per-plan widget controls.
- Explicit saved-plan restoration and `.schematic` export/import in the target modpack.
- GregTech recipe, machine and StructureLib coverage with the exact installed versions.
- BetterQuesting chapter ordering, quest/task/reward data, party/repeat progress and world transitions;
  see the [quest validation steps](betterquesting.md#проверка-пользователем-в-gtnh). This integration
  has isolated tests and a compile baseline, with no recorded in-game verification.
- Harness BetterQuesting card layout, page navigation and explicit native quest-book opening;
  isolated widget tests do not establish native UI behavior in the target modpack.
- Embedded Java 8 HTTP MCP host in GTNH. Transport and forced fixture-JVM termination
  are tested in isolation; no native game startup for this host has been recorded.

The [technical guide](reference.ru.md#проверка-пользователем-на-выделенном-сервере)
and [Schematica guide](schematica.md#ограничения-и-проверка) contain detailed scenarios.
Record the source commit, exact mod versions, steps and observed results when
adding evidence. Successful unit tests or compilation do not establish these results.
