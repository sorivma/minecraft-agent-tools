import java.security.MessageDigest

val releaseBundle by
    tasks.registering(Zip::class) {
        group = "distribution"
        description = "Package the installable bundle, including opted-in Harness widgets."
        dependsOn("verifyBundle")
        from(layout.buildDirectory.dir("bundle"))
        archiveFileName.set("minecraft-agent-tools-${project.version}-bundle.zip")
        destinationDirectory.set(layout.buildDirectory.dir("distributions"))
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
    }

val releaseAssets by
    tasks.registering(Sync::class) {
        group = "distribution"
        description = "Stage all runtime, API and source JARs plus the installation bundle."
        dependsOn("build", releaseBundle)
        into(layout.buildDirectory.dir("release"))
        from(releaseBundle)
        from("LICENSE", "THIRD_PARTY_NOTICES.md", "CHANGELOG.md")
        from("docs/releases/${project.version}.md") { rename { "RELEASE_NOTES.md" } }
    }

// Configure after optional Java/Forge plugins have been applied to every project.
gradle.projectsEvaluated {
    subprojects
        .filter { it.plugins.hasPlugin("java") }
        .forEach { module ->
            releaseAssets.configure {
                val jar = module.tasks.named<Jar>("jar")
                from(jar)
                from(module.tasks.named<Jar>("sourcesJar"))
                if (module.plugins.hasPlugin("com.gtnewhorizons.retrofuturagradle")) {
                    dependsOn("${module.path}:reobfJar")
                    from(
                        module.layout.buildDirectory.file(
                            jar.map {
                                "libs/" +
                                    it.archiveFileName.get().replace(Regex("-dev\\.jar$"), ".jar")
                            }
                        )
                    )
                }
            }
        }
}

tasks.register("releaseChecksums") {
    group = "distribution"
    description = "Generate SHA-256 checksums for every staged release artifact."
    dependsOn(releaseAssets)
    doLast {
        val directory = layout.buildDirectory.dir("release").get().asFile
        val files =
            directory
                .listFiles()!!
                .filter { it.isFile && it.name != "SHA256SUMS" }
                .sortedBy { it.name }
        val names = files.map { it.name }.toSet()
        subprojects
            .filter { it.plugins.hasPlugin("java") }
            .forEach { module ->
                listOf("jar", "sourcesJar").forEach { taskName ->
                    val name = module.tasks.named<Jar>(taskName).get().archiveFileName.get()
                    check(name in names) {
                        "Missing ${module.path} $taskName release artifact: $name"
                    }
                    if (
                        taskName == "jar" &&
                            module.plugins.hasPlugin("com.gtnewhorizons.retrofuturagradle")
                    ) {
                        val runtime = name.replace(Regex("-dev\\.jar$"), ".jar")
                        check(runtime in names) {
                            "Missing reobfuscated runtime artifact: $runtime"
                        }
                    }
                }
            }
        check(files.any { it.name == "mcp-api-${project.version}.jar" }) {
            "MCP API artifact is missing"
        }
        check(files.any { it.name == "bridge-protocol-${project.version}.jar" }) {
            "Bridge protocol artifact is missing"
        }
        check(files.any { it.name == "game-bridge-${project.version}.jar" }) {
            "Game bridge artifact is missing"
        }
        if (providers.gradleProperty("harnessApiJar").isPresent) {
            listOf("schematica", "betterquesting").forEach { integration ->
                check(
                    files.any {
                        it.name ==
                            "minecraft-agent-tools-$integration-harness-1.7.10-${project.version}.jar"
                    }
                ) {
                    "Harness $integration artifact is missing"
                }
            }
        }
        directory
            .resolve("SHA256SUMS")
            .writeText(
                files.joinToString("") { file ->
                    val digest = MessageDigest.getInstance("SHA-256").digest(file.readBytes())
                    digest.joinToString("") { "%02x".format(it) } + "  " + file.name + "\n"
                }
            )
        logger.lifecycle("Prepared ${files.size} release assets with SHA-256 checksums")
    }
}
