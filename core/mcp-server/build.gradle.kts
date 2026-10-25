plugins { application }

evaluationDependsOn(":minecraft-tools")

evaluationDependsOn(":nei-tools")

evaluationDependsOn(":gregtech-tools")

evaluationDependsOn(":schematica-tools")

evaluationDependsOn(":betterquesting-tools")

java { toolchain { languageVersion.set(JavaLanguageVersion.of(21)) } }

dependencies {
    implementation(project(":bridge-protocol"))
    implementation(project(":mcp-api"))
    implementation(project(":mcp-runtime"))
    implementation("com.google.code.gson:gson:2.11.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation(project(":minecraft-tools"))
    testImplementation(project(":nei-tools"))
    testImplementation(project(":gregtech-tools"))
    testImplementation(project(":schematica-tools"))
    testImplementation(project(":betterquesting-tools"))
}

application { mainClass.set("dev.minecraftagenttools.mcp.Main") }

// Standalone core: provider JARs are deliberately excluded from its runtime classpath.
tasks.jar {
    dependsOn(configurations.runtimeClasspath)
    archiveBaseName.set("minecraft-agent-tools-core")
    manifest { attributes["Main-Class"] = application.mainClass.get() }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from({ configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) } })
    from(rootProject.file("THIRD_PARTY_NOTICES.md")) { into("META-INF") }
    from(rootProject.file("licenses")) { into("META-INF/licenses") }
}

distributions.main {
    contents {
        from(rootProject.file("LICENSE"))
        from(rootProject.file("THIRD_PARTY_NOTICES.md"))
        from(rootProject.file("licenses")) { into("licenses") }
        from(project(":minecraft-tools").tasks.named("jar")) { into("plugins") }
        from(project(":nei-tools").tasks.named("jar")) { into("plugins") }
        from(project(":gregtech-tools").tasks.named("jar")) { into("plugins") }
        from(project(":schematica-tools").tasks.named("jar")) { into("plugins") }
        from(project(":betterquesting-tools").tasks.named("jar")) { into("plugins") }
    }
}

tasks.test {
    dependsOn(
        tasks.jar,
        ":minecraft-tools:jar",
        ":nei-tools:jar",
        ":gregtech-tools:jar",
        ":schematica-tools:jar",
        ":betterquesting-tools:jar"
    )
    systemProperty(
        "schematica.provider.jar",
        project(":schematica-tools")
            .tasks
            .named<Jar>("jar")
            .get()
            .archiveFile
            .get()
            .asFile
            .absolutePath
    )
    systemProperty("core.jar", tasks.jar.get().archiveFile.get().asFile.absolutePath)
    systemProperty(
        "betterquesting.provider.jar",
        project(":betterquesting-tools")
            .tasks
            .named<Jar>("jar")
            .get()
            .archiveFile
            .get()
            .asFile
            .absolutePath
    )
    systemProperty(
        "minecraft.provider.jar",
        project(":minecraft-tools")
            .tasks
            .named<Jar>("jar")
            .get()
            .archiveFile
            .get()
            .asFile
            .absolutePath
    )
    systemProperty(
        "nei.provider.jar",
        project(":nei-tools").tasks.named<Jar>("jar").get().archiveFile.get().asFile.absolutePath
    )
    systemProperty(
        "gregtech.provider.jar",
        project(":gregtech-tools")
            .tasks
            .named<Jar>("jar")
            .get()
            .archiveFile
            .get()
            .asFile
            .absolutePath
    )
}
