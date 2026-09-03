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
}

loom {
    serverOnlyMinecraftJar()
}

val artifactVersion = version.toString()

tasks.processResources {
    inputs.property("version", artifactVersion)
    filteringCharset = "UTF-8"

    filesMatching("fabric.mod.json") {
        expand("version" to artifactVersion)
    }
}

tasks.jar {
    from(rootProject.file("LICENSE")) {
        rename { "LICENSE_stackframe" }
    }
}
