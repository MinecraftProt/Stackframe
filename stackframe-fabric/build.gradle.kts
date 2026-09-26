plugins {
    alias(libs.plugins.fabric.loom)
}

description = "Stackframe dedicated-server integration for Fabric"

// This fixture is a separate, test-only Fabric mod. It is never included in the
// Stackframe artifact or any production dependency configuration.
val serverMatrixFixture = sourceSets.create("serverMatrixFixture") {
    compileClasspath += sourceSets.getByName("main").output
    compileClasspath += sourceSets.getByName("main").compileClasspath
}

val serverMatrixFixtureJar by tasks.registering(Jar::class) {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Packages the pinned dedicated-server failure fixture mod."
    archiveBaseName.set("stackframe-server-matrix-fixture")
    archiveVersion.set("0.1.0")
    from(serverMatrixFixture.output)
}

dependencies {
    add("minecraft", libs.minecraft)
    implementation(libs.fabric.loader)

    implementation(project(":stackframe-core"))
    implementation(project(":stackframe-renderer"))
    runtimeOnly(libs.icu4j)
    add("include", project(":stackframe-core"))
    add("include", project(":stackframe-renderer"))
    add("include", libs.icu4j)
    testImplementation(project(":stackframe-testkit"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

loom {
    serverOnlyMinecraftJar()
    providers.gradleProperty("serverMatrixRunDir").orNull?.let { requested ->
        val buildPath = layout.buildDirectory.get().asFile.toPath().toAbsolutePath().normalize()
        val runPath = file(requested).toPath().toAbsolutePath().normalize()
        check(runPath.startsWith(buildPath)) {
            "serverMatrixRunDir must stay inside stackframe-fabric/build"
        }
        runs.named("server") {
            runDir = runPath.toString()
        }
    }
}

tasks.named<JavaExec>("runServer") {
    // The matrix runner sends reload/stop over stdin to finish every process.
    standardInput = System.`in`
}

val artifactVersion = version.toString()
val fabricArtifact = tasks.jar.flatMap { it.archiveFile }
val stackframeLicense = rootProject.file("LICENSE")
val icuLicense = rootProject.file("THIRD-PARTY-NOTICES/icu4j-78.3-LICENSE.txt")

tasks.processResources {
    inputs.property("version", artifactVersion)
    filteringCharset = "UTF-8"

    filesMatching("fabric.mod.json") {
        expand("version" to artifactVersion)
    }
}

tasks.jar {
    from(stackframeLicense) {
        rename { "LICENSE_stackframe" }
    }
    from(icuLicense) {
        into("META-INF/licenses")
    }
}

tasks.test {
    dependsOn(tasks.jar)
    inputs.file(fabricArtifact).withPathSensitivity(PathSensitivity.NONE)
    inputs.file(stackframeLicense).withPathSensitivity(PathSensitivity.NONE)
    inputs.file(icuLicense).withPathSensitivity(PathSensitivity.NONE)
    inputs.property("stackframe.artifactVersion", artifactVersion)
    doFirst {
        systemProperty(
            "stackframe.fabricArtifact",
            fabricArtifact.get().asFile.absolutePath,
        )
        systemProperty(
            "stackframe.stackframeLicense",
            stackframeLicense.absolutePath,
        )
        systemProperty(
            "stackframe.icuLicense",
            icuLicense.absolutePath,
        )
        systemProperty("stackframe.artifactVersion", artifactVersion)
    }
}
