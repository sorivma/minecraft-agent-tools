plugins {
    java
    id("com.gtnewhorizons.retrofuturagradle") version "1.4.9"
}

java { toolchain { languageVersion.set(JavaLanguageVersion.of(8)) } }

minecraft { mcVersion.set("1.7.10") }

dependencies { implementation(project(":minecraft-tools")) }

tasks.jar {
    from(project(":bridge-protocol").sourceSets.main.get().output)
    // Embed only the Java 8 tool contract, not endpoint provider discovery or its SPI.
    from(project(":minecraft-tools").sourceSets.main.get().output) {
        include("dev/minecraftagenttools/minecraft/contract/Tools.class")
    }
    archiveBaseName.set("minecraft-agent-tools-forge-1.7.10")
}
