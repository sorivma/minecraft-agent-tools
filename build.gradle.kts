plugins {
    base
    id("com.diffplug.spotless") version "6.25.0"
}

spotless {
    java {
        target("*/src/**/*.java")
        googleJavaFormat("1.22.0").aosp()
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        target("*.gradle.kts", "*/build.gradle.kts")
        ktfmt("0.50").kotlinlangStyle()
        trimTrailingWhitespace()
        endWithNewline()
    }
}

allprojects {
    group = "dev.minecraftagenttools"
    version = "0.1.0-SNAPSHOT"
    repositories { mavenCentral() }
}

subprojects { tasks.withType<JavaCompile>().configureEach { options.encoding = "UTF-8" } }

tasks.named("build") { dependsOn(subprojects.map { "${it.path}:build" }) }

tasks.named("check") { dependsOn(subprojects.map { "${it.path}:check" }) }

tasks.named("clean") { dependsOn(subprojects.map { "${it.path}:clean" }) }

val bundle by
    tasks.registering(Sync::class) {
        group = "distribution"
        description = "Assemble a standalone core, optional provider JARs and the Forge adapter."
        dependsOn(
            ":mcp-server:jar",
            ":minecraft-tools:jar",
            ":forge-1.7.10:assemble",
            ":nei-tools:jar",
            ":forge-nei-1.7.10:assemble"
        )
        dependsOn(":gregtech-tools:jar", ":forge-gregtech-1.7.10:assemble")
        into(layout.buildDirectory.dir("bundle"))
        from(
            project(":mcp-server")
                .layout
                .buildDirectory
                .file("libs/minecraft-agent-tools-core-${project.version}.jar")
        ) {
            rename { "core.jar" }
        }
        from(
            project(":minecraft-tools")
                .layout
                .buildDirectory
                .file("libs/minecraft-tools-${project.version}.jar")
        ) {
            into("plugins")
            rename { "minecraft-tools.jar" }
        }
        from(
            project(":forge-1.7.10")
                .layout
                .buildDirectory
                .file("libs/minecraft-agent-tools-forge-1.7.10-${project.version}.jar")
        ) {
            into("forge")
        }
        from(
            project(":nei-tools")
                .layout
                .buildDirectory
                .file("libs/nei-tools-${project.version}.jar")
        ) {
            into("plugins")
            rename { "nei-tools.jar" }
        }
        from(
            project(":forge-nei-1.7.10")
                .layout
                .buildDirectory
                .file("libs/minecraft-agent-tools-nei-forge-1.7.10-${project.version}.jar")
        ) {
            into("client-mods")
        }
        from(
            project(":gregtech-tools")
                .layout
                .buildDirectory
                .file("libs/gregtech-tools-${project.version}.jar")
        ) {
            into("plugins")
            rename { "gregtech-tools.jar" }
        }
        from(
            project(":forge-gregtech-1.7.10")
                .layout
                .buildDirectory
                .file("libs/minecraft-agent-tools-gregtech-forge-1.7.10-${project.version}.jar")
        ) {
            into("gregtech-mods")
        }
    }

tasks.named("build") { dependsOn(bundle) }
