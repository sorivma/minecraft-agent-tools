plugins { `java-library` }

java { toolchain { languageVersion.set(JavaLanguageVersion.of(8)) } }

dependencies {
    api(project(":bridge-protocol"))
    implementation(project(":mcp-api"))
    testImplementation("junit:junit:4.13.2")
}
