plugins { `java-library` }

java { toolchain { languageVersion.set(JavaLanguageVersion.of(8)) } }

dependencies { testImplementation("junit:junit:4.13.2") }

val generateBuildVersion by
    tasks.registering {
        val releaseVersion = project.version.toString()
        val output = layout.buildDirectory.dir("generated/sources/version")
        inputs.property("version", releaseVersion)
        outputs.dir(output)
        doLast {
            require(releaseVersion.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+(?:-[A-Za-z0-9.]+)?"))) {
                "Invalid project version: $releaseVersion"
            }
            val file =
                output.get().file("dev/minecraftagenttools/protocol/BuildVersion.java").asFile
            file.parentFile.mkdirs()
            file.writeText(
                "package dev.minecraftagenttools.protocol;\n" +
                    "public final class BuildVersion {\n" +
                    "    public static final String VERSION = \"$releaseVersion\";\n" +
                    "    private BuildVersion() {}\n" +
                    "}\n"
            )
        }
    }

sourceSets.main { java.srcDir(generateBuildVersion) }
