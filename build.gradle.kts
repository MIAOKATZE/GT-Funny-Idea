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

// Complete replacement variant; production jars never include active test fixtures.
val testlabSourceSet = sourceSets.create("testlab") {
    compileClasspath += sourceSets.main.get().output + sourceSets.main.get().compileClasspath
    runtimeClasspath += output + compileClasspath + sourceSets.main.get().runtimeClasspath
}
configurations.named(testlabSourceSet.implementationConfigurationName) { extendsFrom(configurations.implementation.get()) }
configurations.named(testlabSourceSet.compileOnlyConfigurationName) { extendsFrom(configurations.compileOnly.get()) }
val testlabJar = tasks.register<Jar>("testlabJar") {
    group = "build"
    description = "Complete opt-in testlab replacement; install instead of the ordinary jar."
    archiveClassifier.set("testlab-dev")
    with(tasks.jar.get())
    from(testlabSourceSet.output)
    manifest.from(tasks.jar.get().manifest)
    manifest.attributes("GTFI-Testlab-Variant" to "replacement")
}
tasks.named<com.gtnewhorizons.retrofuturagradle.mcp.ReobfuscatedJar>("reobfTestlabJar") {
    archiveClassifier.set("testlab")
    referenceClasspath.from(testlabSourceSet.compileClasspath)
    val productionReobf = tasks.named<com.gtnewhorizons.retrofuturagradle.mcp.ReobfuscatedJar>("reobfJar")
    extraSrgFiles.from(productionReobf.map { it.extraSrgFiles })
    extraSrgEntries.addAll(productionReobf.flatMap { it.extraSrgEntries })
}
tasks.register<JavaExec>("runTestlabAbiRegression") {
    group = "verification"
    mainClass = "com.miaokatze.gtfi.core.DedicatedServerAbiRegression"
    classpath = sourceSets.test.get().runtimeClasspath
    dependsOn(testlabSourceSet.classesTaskName)
    args(testlabSourceSet.output.classesDirs.singleFile.absolutePath)
}
tasks.register<JavaExec>("runTestlabPackagingRegression") {
    group = "verification"
    description = "Checks active fixture isolation and byte-identical production entries in the replacement jar."
    mainClass = "com.miaokatze.gtfi.core.TestlabPackagingRegression"
    classpath = sourceSets.test.get().runtimeClasspath
    val normal = tasks.named<com.gtnewhorizons.retrofuturagradle.mcp.ReobfuscatedJar>("reobfJar")
    val replacement = tasks.named<com.gtnewhorizons.retrofuturagradle.mcp.ReobfuscatedJar>("reobfTestlabJar")
    dependsOn(normal, replacement)
    doFirst { setArgs(listOf(normal.get().archiveFile.get().asFile.absolutePath, replacement.get().archiveFile.get().asFile.absolutePath)) }
}
tasks.named("check") { dependsOn("runTestlabAbiRegression", "runTestlabPackagingRegression") }
tasks.named("assemble") { dependsOn("reobfTestlabJar") }
