import org.apache.tools.ant.filters.ReplaceTokens

plugins {
    id("com.gtnewhorizons.gtnhconvention")
}

// Keep the declared release separate from the convention's Git-derived build version.
extensions.configure<com.gtnewhorizons.retrofuturagradle.MinecraftExtension>("minecraft") {
    injectedTags.put("RELEASE_VERSION", providers.gradleProperty("RELEASE_VERSION"))
}

val releaseTagVersion = providers.gradleProperty("RELEASE_VERSION").map { "v$it" }

tasks.processResources {
    inputs.property("gtfiTagVersion", releaseTagVersion)
    filesMatching("mcmod.info") {
        filter<ReplaceTokens>("tokens" to mapOf("GTFI_TAG_VERSION" to releaseTagVersion.get()))
    }
}

// Pure rules use an explicit main entry point without a Minecraft bootstrap.
tasks.test {
    failOnNoDiscoveredTests = false
}

tasks.register<JavaExec>("runCoreRulesRegression") {
    group = "verification"
    description = "Checks swept collision math and transfer progression boundary rules."
    mainClass = "com.miaokatze.gtfi.core.CoreRulesRegression"
    classpath = sourceSets.getByName("test").runtimeClasspath
}

tasks.register<JavaExec>("runDedicatedServerAbiRegression") {
    group = "verification"
    description = "Rejects client-only Minecraft APIs in common and server bytecode."
    mainClass = "com.miaokatze.gtfi.core.DedicatedServerAbiRegression"
    classpath = sourceSets.getByName("test").runtimeClasspath
}

tasks.named("check") {
    dependsOn("runCoreRulesRegression", "runDedicatedServerAbiRegression")
}
