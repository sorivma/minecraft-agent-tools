plugins {
    java
    id("com.gtnewhorizons.retrofuturagradle") version "1.4.9"
}

java { toolchain { languageVersion.set(JavaLanguageVersion.of(8)) } }

minecraft { mcVersion.set("1.7.10") }

repositories { maven("https://nexus.gtnewhorizons.com/repository/public/") }

dependencies {
    implementation(project(":nei-tools"))
    // Java 8 API baseline. The installed NEI supplies the implementation; never shade it.
    compileOnly("com.github.GTNewHorizons:NotEnoughItems:2.6.44-GTNH:dev") { isTransitive = false }
    testImplementation("junit:junit:4.13.2")
    testImplementation("com.github.GTNewHorizons:NotEnoughItems:2.6.44-GTNH:dev") {
        isTransitive = false
    }
}

tasks.jar {
    from(project(":bridge-protocol").sourceSets.main.get().output)
    from(project(":nei-tools").sourceSets.main.get().output) {
        include("dev/minecraftagenttools/nei/contract/NeiTools*.class")
        include("dev/minecraftagenttools/nei/contract/ScopedCache*.class")
        include("dev/minecraftagenttools/nei/contract/NeiScope*.class")
    }
    archiveBaseName.set("minecraft-agent-tools-nei-forge-1.7.10")
}
