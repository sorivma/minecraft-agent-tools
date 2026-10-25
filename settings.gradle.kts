pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://nexus.gtnewhorizons.com/repository/public/")
    }
}

plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0" }

rootProject.name = "minecraft-agent-tools"

// Keep Gradle project paths stable while grouping their directories by responsibility.
val moduleGroups =
    mapOf(
        "core" to listOf("bridge-protocol", "mcp-api", "mcp-runtime", "mcp-server", "game-bridge"),
        "tools" to
            listOf(
                "minecraft-tools",
                "nei-tools",
                "gregtech-tools",
                "schematica-tools",
                "betterquesting-tools"
            ),
        "adapters" to
            listOf(
                "forge-1.7.10",
                "forge-mcp-1.7.10",
                "forge-nei-1.7.10",
                "forge-gregtech-1.7.10",
                "forge-schematica-1.7.10",
                "forge-betterquesting-1.7.10"
            )
    )

moduleGroups.forEach { (directory, modules) ->
    modules.forEach { module ->
        include(module)
        project(":$module").projectDir = file("$directory/$module")
    }
}

// Always expose integration projects to Gradle/IDEs; compiling their JARs remains opt-in.
providers.gradleProperty("harnessApiJar").orNull?.let { path ->
    require(path.isNotBlank() && file(path).isFile) {
        "harnessApiJar must point to an existing Harness dev JAR. " +
            "Omit -PharnessApiJar to build the standalone project."
    }
}

listOf("schematica", "betterquesting").forEach { integration ->
    val module = "harness-$integration-1.7.10"
    include(module)
    project(":$module").projectDir = file("integrations/harness/$integration")
}
