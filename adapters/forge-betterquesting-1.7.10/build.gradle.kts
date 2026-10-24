plugins {
    java
    id("com.gtnewhorizons.retrofuturagradle") version "1.4.9"
}

java { toolchain { languageVersion.set(JavaLanguageVersion.of(8)) } }

minecraft { mcVersion.set("1.7.10") }

repositories { maven("https://nexus.gtnewhorizons.com/repository/public/") }

dependencies {
    implementation(project(":betterquesting-tools"))
    implementation(project(":game-bridge"))
    compileOnly("com.github.GTNewHorizons:BetterQuesting:3.8.89-GTNH:dev") { isTransitive = false }
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-inline:4.11.0")
    testImplementation("com.github.GTNewHorizons:BetterQuesting:3.8.89-GTNH:dev") {
        isTransitive = false
    }
}

tasks.test { classpath += sourceSets.main.get().compileClasspath }

tasks.jar {
    from(project(":bridge-protocol").sourceSets.main.get().output)
    from(project(":game-bridge").sourceSets.main.get().output)
    from(project(":betterquesting-tools").sourceSets.main.get().output) {
        include("dev/minecraftagenttools/betterquesting/contract/**")
    }
    archiveBaseName.set("minecraft-agent-tools-betterquesting-forge-1.7.10")
}
