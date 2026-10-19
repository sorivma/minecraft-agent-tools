pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://nexus.gtnewhorizons.com/repository/public/")
    }
}

plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0" }

rootProject.name = "minecraft-agent-tools"

include("bridge-protocol", "mcp-api", "mcp-server", "minecraft-tools", "forge-1.7.10")

include("nei-tools", "forge-nei-1.7.10")

include("game-bridge", "gregtech-tools", "forge-gregtech-1.7.10")
