plugins {
    `java-library`
}

description = "Stackframe terminal, plain-text, and structured diagnostic rendering"

dependencies {
    api(project(":stackframe-core"))
    implementation("com.ibm.icu:icu4j:78.3")
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
