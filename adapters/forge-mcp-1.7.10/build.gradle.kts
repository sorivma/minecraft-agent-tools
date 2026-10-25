plugins {
    java
    id("com.gtnewhorizons.retrofuturagradle") version "1.4.9"
}

java { toolchain { languageVersion.set(JavaLanguageVersion.of(8)) } }

minecraft { mcVersion.set("1.7.10") }

dependencies {
    implementation(project(":mcp-runtime"))
    compileOnly(project(":bridge-protocol"))
}

tasks.jar {
    from(project(":mcp-runtime").sourceSets.main.get().output)
    from(project(":mcp-api").sourceSets.main.get().output)
    from(project(":bridge-protocol").sourceSets.main.get().output)
    archiveBaseName.set("minecraft-agent-tools-mcp-forge-1.7.10")
}
