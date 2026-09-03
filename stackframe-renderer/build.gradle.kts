plugins {
    `java-library`
}

description = "Stackframe terminal, plain-text, and structured diagnostic rendering"

dependencies {
    api(project(":stackframe-core"))
    implementation(libs.icu4j)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
