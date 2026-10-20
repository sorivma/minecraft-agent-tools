plugins {
    java
    id("com.gtnewhorizons.retrofuturagradle") version "1.4.9"
}

java { toolchain { languageVersion.set(JavaLanguageVersion.of(8)) } }

minecraft { mcVersion.set("1.7.10") }

dependencies {
    implementation(project(":schematica-tools"))
    implementation(project(":game-bridge"))
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-inline:4.11.0")
}

tasks.test { classpath += sourceSets.main.get().compileClasspath }

tasks.jar {
    from(project(":bridge-protocol").sourceSets.main.get().output)
    from(project(":game-bridge").sourceSets.main.get().output)
    from(project(":schematica-tools").sourceSets.main.get().output) {
        include("dev/minecraftagenttools/schematica/contract/**")
    }
    archiveBaseName.set("minecraft-agent-tools-schematica-forge-1.7.10")
}
