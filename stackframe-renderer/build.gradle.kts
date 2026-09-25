plugins {
    `java-library`
}

description = "Stackframe terminal, plain-text, and structured diagnostic rendering"

dependencies {
    api(project(":stackframe-core"))
    implementation(libs.icu4j)
    testImplementation(project(":stackframe-testkit"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

val updateGoldenSnapshots by tasks.registering(Test::class) {
    group = "verification"
    description = "Explicitly rewrites renderer golden files for review."
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform { includeTags("golden") }
    systemProperty("stackframe.updateGoldens", "true")
    outputs.upToDateWhen { false }
}
