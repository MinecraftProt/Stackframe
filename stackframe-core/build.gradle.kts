plugins {
    `java-library`
}

description = "Loader-independent Stackframe diagnostic contracts and processing"

dependencies {
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
