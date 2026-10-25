import java.security.MessageDigest

plugins {
    java
    id("com.gtnewhorizons.retrofuturagradle") version "1.4.9"
}

java { toolchain { languageVersion.set(JavaLanguageVersion.of(8)) } }

minecraft { mcVersion.set("1.7.10") }

repositories {
    // This historical Java 8 baseline is no longer present in the GTNH Maven repository.
    // Resolve only this module from its official, versioned upstream release.
    exclusiveContent {
        forRepository {
            ivy {
                name = "NeiOfficialRelease"
                url = uri("https://github.com/GTNewHorizons/NotEnoughItems/releases/download")
                patternLayout { artifact("[revision]/[artifact]-[revision](-[classifier]).[ext]") }
                metadataSources { artifact() }
            }
        }
        filter { includeModule("com.github.GTNewHorizons", "NotEnoughItems") }
    }
}

val neiBaseline by configurations.creating

dependencies {
    implementation(project(":nei-tools"))
    // Java 8 API baseline. The installed NEI supplies the implementation; never shade it.
    add(neiBaseline.name, "com.github.GTNewHorizons:NotEnoughItems:2.6.44-GTNH:dev") {
        isTransitive = false
    }
    compileOnly(files(neiBaseline))
    testImplementation("junit:junit:4.13.2")
    testImplementation(files(neiBaseline))
}

val verifyNeiBaseline by
    tasks.registering {
        group = "verification"
        description = "Verify the pinned upstream NEI development API before compilation."
        inputs.files(neiBaseline)
        doLast {
            val digest =
                MessageDigest.getInstance("SHA-256").digest(neiBaseline.singleFile.readBytes())
            val actual = digest.joinToString("") { "%02x".format(it) }
            check(actual == "1159e813bc16128d4156703c794ac83a9f893f04dce74674b37159e3fa85665c") {
                "The official NEI 2.6.44-GTNH development artifact has changed: $actual"
            }
        }
    }

tasks.compileJava { dependsOn(verifyNeiBaseline) }

tasks.named("check") { dependsOn(verifyNeiBaseline) }

tasks.jar {
    from(project(":bridge-protocol").sourceSets.main.get().output)
    from(project(":nei-tools").sourceSets.main.get().output) {
        include("dev/minecraftagenttools/nei/contract/NeiTools*.class")
        include("dev/minecraftagenttools/nei/contract/ScopedCache*.class")
        include("dev/minecraftagenttools/nei/contract/NeiScope*.class")
    }
    archiveBaseName.set("minecraft-agent-tools-nei-forge-1.7.10")
}
