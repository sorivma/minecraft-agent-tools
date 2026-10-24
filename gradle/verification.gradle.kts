import java.io.DataInputStream
import java.util.zip.ZipFile

// Inspect the actual distribution: source compatibility alone cannot catch accidental shading.
val verifyBundle by
    tasks.registering {
        group = "verification"
        description = "Verify Java 8 adapter bytecode and the core/provider packaging boundaries."
        dependsOn("bundle")
        val bundleDir = layout.buildDirectory.dir("bundle")
        inputs.dir(bundleDir)
        doLast {
            val directory = bundleDir.get().asFile
            val jars = directory.walkTopDown().filter { it.extension == "jar" }.toList()
            val expected =
                mutableSetOf(
                    "core.jar",
                    "plugins/minecraft-tools.jar",
                    "plugins/nei-tools.jar",
                    "plugins/gregtech-tools.jar",
                    "plugins/schematica-tools.jar",
                    "plugins/betterquesting-tools.jar",
                    "client-mods/minecraft-agent-tools-betterquesting-forge-1.7.10-${project.version}.jar",
                    "forge/minecraft-agent-tools-forge-1.7.10-${project.version}.jar",
                    "forge/minecraft-agent-tools-mcp-forge-1.7.10-${project.version}.jar",
                    "client-mods/minecraft-agent-tools-nei-forge-1.7.10-${project.version}.jar",
                    "client-mods/minecraft-agent-tools-schematica-forge-1.7.10-${project.version}.jar",
                    "gregtech-mods/minecraft-agent-tools-gregtech-forge-1.7.10-${project.version}.jar"
                )
            if (providers.gradleProperty("harnessApiJar").isPresent) {
                listOf("schematica", "betterquesting").forEach { integration ->
                    expected.add(
                        "harness-mods/minecraft-agent-tools-$integration-harness-1.7.10-${project.version}.jar"
                    )
                }
            } else {
                check(
                    jars.none {
                        it.relativeTo(directory).invariantSeparatorsPath.startsWith("harness-mods/")
                    }
                ) {
                    "Standalone bundle must not contain Harness extensions"
                }
            }
            check(
                jars.map { it.relativeTo(directory).invariantSeparatorsPath }.containsAll(expected)
            ) {
                "Bundle is missing a core, provider or adapter JAR"
            }
            val service = "META-INF/services/dev.minecraftagenttools.api.ToolProvider"
            jars.forEach { file ->
                val relative = file.relativeTo(directory).invariantSeparatorsPath
                val core = relative == "core.jar"
                val provider = relative.startsWith("plugins/")
                val embeddedHost = relative.startsWith("forge/minecraft-agent-tools-mcp-forge-")
                ZipFile(file).use { jar ->
                    val entries = jar.entries().asSequence().toList()
                    val classes = entries.filter { it.name.endsWith(".class") }
                    if (embeddedHost) {
                        check(jar.getEntry("dev/minecraftagenttools/mcp/Main.class") == null) {
                            "$relative embeds the external stdio launcher"
                        }
                    }
                    check(classes.isNotEmpty()) { "$relative has no classes" }
                    check(jar.getEntry("META-INF/LICENSE-minecraft-agent-tools") != null) {
                        "$relative is missing the project license"
                    }
                    check((jar.getEntry(service) != null) == provider) {
                        "$relative has an incorrect provider service registration"
                    }
                    classes.forEach { entry ->
                        val name = entry.name
                        val major =
                            jar.getInputStream(entry).use { stream ->
                                DataInputStream(stream).use { data ->
                                    check(data.readInt() == 0xCAFEBABE.toInt()) {
                                        "$relative: invalid class $name"
                                    }
                                    data.readUnsignedShort()
                                    data.readUnsignedShort()
                                }
                            }
                        val java8 =
                            !core ||
                                name.startsWith("dev/minecraftagenttools/protocol/") ||
                                name.startsWith("dev/minecraftagenttools/api/")
                        check(major <= if (java8) 52 else 65) {
                            "$relative: $name uses class version $major"
                        }
                        check(!name.startsWith("net/minecraft/") && !name.startsWith("cpw/mods/")) {
                            "$relative bundles game/loader classes: $name"
                        }
                        if (core) {
                            check(!name.contains("/provider/") && !name.contains("/contract/")) {
                                "The core embeds tool implementation $name"
                            }
                        } else {
                            check(name.startsWith("dev/minecraftagenttools/")) {
                                "$relative bundles a third-party class: $name"
                            }
                            check(
                                embeddedHost || !name.startsWith("dev/minecraftagenttools/mcp/")
                            ) {
                                "$relative embeds MCP core code: $name"
                            }
                            if (provider) {
                                check(!name.startsWith("dev/minecraftagenttools/api/")) {
                                    "$relative embeds the core-owned provider SPI: $name"
                                }
                            } else {
                                check(
                                    !name.contains("/provider/") &&
                                        (embeddedHost ||
                                            !name.startsWith("dev/minecraftagenttools/api/"))
                                ) {
                                    "$relative embeds MCP provider code: $name"
                                }
                            }
                        }
                        if (!relative.startsWith("harness-mods/")) {
                            check(!name.startsWith("dev/minecraftagenttools/harness/")) {
                                "$relative embeds the optional Harness extension"
                            }
                        }
                    }
                }
            }
            logger.lifecycle("Verified ${jars.size} bundle JARs: bytecode and module boundaries")
        }
    }

tasks.named("check") { dependsOn(verifyBundle) }
