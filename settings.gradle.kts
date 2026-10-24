pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://nexus.gtnewhorizons.com/repository/public/")
    }
}

plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0" }

rootProject.name = "minecraft-agent-tools"

// Keep Gradle project paths stable while grouping their directories by responsibility.
val moduleGroups =
    mapOf(
        "core" to listOf("bridge-protocol", "mcp-api", "mcp-server", "game-bridge"),
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

// An external UI extension is opt-in: a fresh clone never needs a sibling checkout.
providers.gradleProperty("harnessApiJar").orNull?.let { path ->
    require(path.isNotBlank() && file(path).isFile) {
        "harnessApiJar must point to an existing Harness dev JAR. " +
            "Omit -PharnessApiJar to build the standalone project."
    }
    include("harness-schematica-1.7.10")
    project(":harness-schematica-1.7.10").projectDir = file("adapters/harness-schematica-1.7.10")
}
