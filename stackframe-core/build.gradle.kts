plugins {
    `java-library`
}

description = "Loader-independent Stackframe diagnostic contracts and processing"

dependencies {
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

val registryMain = sourceSets.named("main")
val registryCatalog = rootProject.layout.projectDirectory.file(
    "docs/diagnostic-registry/catalog.md",
)
val registryBaseline = layout.projectDirectory.file(
    "src/main/resources/org/minecraftprot/stackframe/diagnostic/registry/compatibility-baseline.tsv",
)

fun JavaExec.configureRegistryTool(vararg arguments: Any) {
    dependsOn(tasks.named("classes"))
    classpath = registryMain.get().runtimeClasspath
    mainClass = "org.minecraftprot.stackframe.diagnostic.registry.RegistryGovernanceTool"
    args(*arguments)
}

val generateDiagnosticRegistryCatalog by tasks.registering(JavaExec::class) {
    group = "documentation"
    description = "Generates the searchable diagnostic-code catalog."
    configureRegistryTool("write-catalog", registryCatalog.asFile.absolutePath)
}

val updateDiagnosticRegistryBaseline by tasks.registering(JavaExec::class) {
    group = "documentation"
    description = "Intentionally updates the reviewed registry compatibility baseline."
    configureRegistryTool("write-baseline", registryBaseline.asFile.absolutePath)
}

val verifyDiagnosticRegistry by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Checks the canonical registry, generated catalog, and compatibility baseline."
    configureRegistryTool(
        "check",
        registryCatalog.asFile.absolutePath,
        registryBaseline.asFile.absolutePath,
    )
}

tasks.named("check") {
    dependsOn(verifyDiagnosticRegistry)
}
