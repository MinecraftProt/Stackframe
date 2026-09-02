plugins {
    alias(libs.plugins.fabric.loom)
}

description = "Stackframe dedicated-server integration for Fabric"

dependencies {
    add("minecraft", libs.minecraft)
    implementation(libs.fabric.loader)

    implementation(project(":stackframe-core"))
    implementation(project(":stackframe-renderer"))
    add("include", project(":stackframe-core"))
    add("include", project(":stackframe-renderer"))
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
