# Third-party notices

The MIT license applies to original Minecraft Agent Tools code. Dependencies
retain their own licenses; installing a mod does not relicense it under MIT.

| Component | How it is used | License / upstream |
| --- | --- | --- |
| Gradle Wrapper 8.8 | Wrapper JAR and launcher scripts in this repository | [Apache-2.0](https://github.com/gradle/gradle/blob/v8.8.0/LICENSE) |
| Gson 2.11.0 | Included in the standalone MCP core | [Apache-2.0](https://github.com/google/gson/blob/gson-parent-2.11.0/LICENSE) |
| Error Prone annotations 2.27.0 | Transitive Gson dependency included in the MCP core | [Apache-2.0](https://github.com/google/error-prone/blob/v2.27.0/COPYING) |

The Apache license text is included in `licenses/Apache-2.0.txt` in this
repository and in the generated bundle. The core JAR carries it in `META-INF/licenses/`.
Gson copyright: The Gson Authors. Error Prone copyright: The Error Prone Authors.
Gradle copyright: the original author or authors, as recorded in the wrapper sources.

Minecraft, Forge, NEI, GregTech, StructureLib, Schematica and Minecraft Harness
are not included in the distributed project JARs. Install the required game and
mods separately under their respective terms. The Forge adapters use the Gson
already provided by Minecraft; the modern Gson dependency belongs to the external core only.

Build and test dependencies (including RetroFuturaGradle, Spotless, JUnit and
Mockito) are resolved by Gradle and are not bundled as game mods.

The README cover is an AI-generated concept illustration, not an in-game
screenshot. Its source prompt is recorded in `docs/assets/README.md`. It uses no
copied game textures. Minecraft Agent Tools is an independent project and is
not affiliated with or endorsed by Mojang or Microsoft.
