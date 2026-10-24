import com.gtnewhorizons.retrofuturagradle.MinecraftExtension

plugins {
    base
    id("com.gtnewhorizons.retrofuturagradle") version "1.4.9" apply false
}

val harnessApi = providers.gradleProperty("harnessApiJar").orNull

if (harnessApi == null) {
    tasks.named("build") {
        doLast {
            logger.lifecycle(
                "$path: widget JAR not built; supply -PharnessApiJar=/path/to/harness-dev.jar"
            )
        }
    }
} else {
    apply(plugin = "java")
    apply(plugin = "com.gtnewhorizons.retrofuturagradle")
    configure<JavaPluginExtension> { toolchain { languageVersion.set(JavaLanguageVersion.of(8)) } }
    configure<MinecraftExtension> { mcVersion.set("1.7.10") }
    dependencies {
        add("compileOnly", files(rootProject.file(harnessApi)))
        add("compileOnly", project(":forge-betterquesting-1.7.10"))
        add("compileOnly", project(":bridge-protocol"))
        add("testImplementation", files(rootProject.file(harnessApi)))
        add("testImplementation", project(":forge-betterquesting-1.7.10"))
        add("testImplementation", project(":bridge-protocol"))
        add("testImplementation", "junit:junit:4.13.2")
    }
    tasks.named<Jar>("jar") {
        archiveBaseName.set("minecraft-agent-tools-betterquesting-harness-1.7.10")
    }
}
