plugins {
    `java-library`
}

description = "Reusable Stackframe fixtures and verification utilities"

dependencies {
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
