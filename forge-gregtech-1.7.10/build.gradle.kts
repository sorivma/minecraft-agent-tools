plugins {
    java
    id("com.gtnewhorizons.retrofuturagradle") version "1.4.9"
}

java { toolchain { languageVersion.set(JavaLanguageVersion.of(8)) } }

minecraft { mcVersion.set("1.7.10") }

dependencies {
    implementation(project(":gregtech-tools"))
    implementation(project(":game-bridge"))
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-inline:4.11.0")
}

tasks.jar {
    from(project(":bridge-protocol").sourceSets.main.get().output)
    from(project(":game-bridge").sourceSets.main.get().output)
    from(project(":gregtech-tools").sourceSets.main.get().output) {
        include("dev/minecraftagenttools/gregtech/contract/GtTools*.class")
        include("dev/minecraftagenttools/gregtech/contract/GtRead*.class")
        include("dev/minecraftagenttools/gregtech/contract/GtCatalogs*.class")
    }
    archiveBaseName.set("minecraft-agent-tools-gregtech-forge-1.7.10")
}
