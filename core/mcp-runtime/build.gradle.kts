plugins { `java-library` }

java { toolchain { languageVersion.set(JavaLanguageVersion.of(8)) } }

dependencies {
    api(project(":mcp-api"))
    implementation(project(":bridge-protocol"))
    // Minecraft supplies this version. The runtime never packages a Gson implementation.
    compileOnly("com.google.code.gson:gson:2.2.4")
    testImplementation("com.google.code.gson:gson:2.2.4")
    testImplementation("junit:junit:4.13.2")
    testImplementation(project(":game-bridge"))
}

tasks.test { systemProperty("sun.net.http.allowRestrictedHeaders", "true") }
