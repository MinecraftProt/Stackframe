plugins {
    alias(libs.plugins.fabric.loom)
}

description = "Stackframe dedicated-server integration for Fabric"

dependencies {
    add("minecraft", libs.minecraft)
    implementation(libs.fabric.loader)

    implementation(project(":stackframe-core"))
    implementation(project(":stackframe-renderer"))
    runtimeOnly(libs.icu4j)
    add("include", project(":stackframe-core"))
    add("include", project(":stackframe-renderer"))
    add("include", libs.icu4j)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

loom {
    serverOnlyMinecraftJar()
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
