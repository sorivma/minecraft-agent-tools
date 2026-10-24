plugins {
    java
    id("com.gtnewhorizons.retrofuturagradle") version "1.4.9"
}

java { toolchain { languageVersion.set(JavaLanguageVersion.of(8)) } }

minecraft { mcVersion.set("1.7.10") }

// Optional extension only: the independent bridge/provider never depends on Harness.
val harnessApi = rootProject.file(providers.gradleProperty("harnessApiJar").get())

dependencies {
    compileOnly(files(harnessApi))
    compileOnly(project(":forge-schematica-1.7.10"))
    compileOnly(project(":schematica-tools"))
    compileOnly(project(":bridge-protocol"))
    testImplementation(files(harnessApi))
    testImplementation(project(":forge-schematica-1.7.10"))
    testImplementation(project(":schematica-tools"))
    testImplementation(project(":bridge-protocol"))
    testImplementation("junit:junit:4.13.2")
}

tasks.jar { archiveBaseName.set("minecraft-agent-tools-schematica-harness-1.7.10") }
