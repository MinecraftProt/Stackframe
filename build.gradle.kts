import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.api.tasks.compile.JavaCompile
import java.nio.file.Path
import java.util.Properties

plugins {
    base
}

group = "org.minecraftprot.stackframe"
version = "0.1.0-SNAPSHOT"

val productionConfigurations = setOf(
    "annotationProcessor",
    "api",
    "compileOnly",
    "compileOnlyApi",
    "implementation",
    "runtimeOnly",
)
val forbiddenPlatformGroups = listOf(
    "com.mojang",
    "net.fabricmc",
    "net.minecraftforge",
    "org.apache.logging.log4j",
)
val generatedMinecraftGroup = "net.minecraft"
val generatedMinecraftModule = "minecraft-server-deobf"
val generatedMinecraftVersion = libs.versions.minecraft.get()
val generatedMinecraftFile = "$generatedMinecraftModule-$generatedMinecraftVersion.jar"
val allowedProjectDependencies = mapOf(
    "stackframe-core" to emptySet(),
    "stackframe-renderer" to setOf("stackframe-core"),
    "stackframe-fabric" to setOf("stackframe-core", "stackframe-renderer"),
    "stackframe-testkit" to setOf(
        "stackframe-core",
        "stackframe-renderer",
        "stackframe-fabric",
    ),
)

fun String.isForbiddenPlatformGroup(): Boolean =
    forbiddenPlatformGroups.any { this == it || startsWith("$it.") }

allprojects {
    group = rootProject.group
    version = rootProject.version

    repositories {
        exclusiveContent {
            forRepository {
                maven("https://maven.fabricmc.net/") {
                    name = "Fabric"
                }
            }
            filter {
                includeGroupByRegex("net\\.fabricmc(\\..*)?")
            }
        }
        mavenCentral()
    }

    configurations.configureEach {
        resolutionStrategy {
            failOnVersionConflict()
        }
    }

    dependencyLocking {
        lockAllConfigurations()
    }

    tasks.withType<AbstractArchiveTask>().configureEach {
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
    }
}

subprojects {
    pluginManager.withPlugin("fabric-loom") {
        val loomRepository = repositories.named("LoomGlobalMinecraft").get()
        check(loomRepository is MavenArtifactRepository) {
            "LoomGlobalMinecraft must be a Maven repository."
        }

        val expectedRepositoryPath = gradle.gradleUserHomeDir.toPath()
            .resolve("caches/fabric-loom/minecraftMaven")
            .toAbsolutePath()
            .normalize()
        check(loomRepository.url.scheme == "file") {
            "LoomGlobalMinecraft must remain file-backed."
        }
        check(Path.of(loomRepository.url).toAbsolutePath().normalize() == expectedRepositoryPath) {
            "LoomGlobalMinecraft must remain inside the Gradle User Home Loom cache."
        }

        repositories.withType<MavenArtifactRepository>().configureEach {
            if (this !== loomRepository) {
                content {
                    excludeModule(generatedMinecraftGroup, generatedMinecraftModule)
                }
            }
        }
        repositories.exclusiveContent {
            forRepositories(loomRepository)
            filter {
                includeModule(generatedMinecraftGroup, generatedMinecraftModule)
            }
        }
    }

    if (name in setOf("stackframe-core", "stackframe-renderer")) {
        configurations.matching {
            it.name in setOf("compileClasspath", "runtimeClasspath")
        }.configureEach {
            resolutionStrategy.componentSelection.all {
                if (candidate.group.isForbiddenPlatformGroup()) {
                    reject(
                        "${project.path} cannot resolve platform or logging implementation " +
                            "${candidate.group}:${candidate.module}:${candidate.version}",
                    )
                }
            }
        }
    }

    plugins.withType<JavaPlugin> {
        extensions.configure<JavaPluginExtension> {
            toolchain {
                languageVersion = JavaLanguageVersion.of(
                    libs.versions.java.get().toInt(),
                )
            }
            withSourcesJar()
        }

        tasks.withType<JavaCompile>().configureEach {
            options.release = libs.versions.java.get().toInt()
            options.encoding = "UTF-8"
        }

        tasks.withType<Test>().configureEach {
            useJUnitPlatform()
        }
    }
}

val verifyModuleBoundaries by tasks.registering {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Verifies Stackframe's module dependency architecture."

    doLast {
        check(subprojects.map { it.name }.toSet() == allowedProjectDependencies.keys) {
            "Configured modules differ from the approved server foundation modules."
        }

        subprojects.forEach { module ->
            val allowedProjects = allowedProjectDependencies.getValue(module.name)

            module.configurations.forEach { configuration ->
                configuration.dependencies.withType(ProjectDependency::class.java).forEach { dependency ->
                    val target = dependency.path.substringAfterLast(':')
                    check(target != "stackframe-testkit" || module.name == "stackframe-testkit") {
                        "Production module ${module.path} must not depend on stackframe-testkit " +
                            "(${configuration.name})."
                    }
                    check(target in allowedProjects) {
                        "${module.path} must not depend on ${dependency.path} " +
                            "(${configuration.name})."
                    }
                }

                if (
                    module.name in setOf("stackframe-core", "stackframe-renderer") &&
                    configuration.name in productionConfigurations
                ) {
                    configuration.dependencies.withType(ExternalModuleDependency::class.java)
                        .forEach { dependency ->
                            val dependencyGroup = dependency.group.orEmpty()
                            check(!dependencyGroup.isForbiddenPlatformGroup()) {
                                "${module.path} must not declare platform or logging dependency " +
                                    "$dependencyGroup:${dependency.name} (${configuration.name})."
                            }
                        }
                }
            }

        }
    }
}

val verifyGradleWrapper by tasks.registering {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Verifies that the wrapper matches the version catalog and pins a checksum."

    doLast {
        val wrapperProperties = Properties().apply {
            rootProject.file("gradle/wrapper/gradle-wrapper.properties").inputStream().use(::load)
        }
        val expectedVersion = libs.versions.gradle.get()
        val distributionUrl = wrapperProperties.getProperty("distributionUrl").orEmpty()
        val checksum = wrapperProperties.getProperty("distributionSha256Sum").orEmpty()

        check(distributionUrl.endsWith("/gradle-$expectedVersion-bin.zip")) {
            "Gradle wrapper distribution does not match catalog version $expectedVersion."
        }
        check(checksum.matches(Regex("[0-9a-f]{64}"))) {
            "Gradle wrapper must specify a SHA-256 distribution checksum."
        }
    }
}

val verifyGeneratedMinecraftRepository by tasks.registering {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Verifies Loom's generated Minecraft artifact resolves only from its local repository."
    dependsOn(":stackframe-fabric:compileJava")

    doLast {
        val fabricProject = project(":stackframe-fabric")
        val loomRepository = fabricProject.repositories.named("LoomGlobalMinecraft").get()
        check(loomRepository is MavenArtifactRepository) {
            "LoomGlobalMinecraft must be a Maven repository."
        }

        val repositoryPath = Path.of(loomRepository.url).toRealPath()
        val generatedArtifact = fabricProject.configurations
            .getByName("compileClasspath")
            .incoming
            .artifacts
            .artifacts
            .single { artifact ->
                val component = artifact.id.componentIdentifier as? ModuleComponentIdentifier
                component?.group == generatedMinecraftGroup &&
                    component.module == generatedMinecraftModule &&
                    component.version == generatedMinecraftVersion &&
                    artifact.file.name == generatedMinecraftFile
            }
        val artifactPath = generatedArtifact.file.toPath().toRealPath()

        check(artifactPath.startsWith(repositoryPath)) {
            "$generatedMinecraftGroup:$generatedMinecraftModule:$generatedMinecraftVersion " +
                "must resolve from $repositoryPath, but resolved from $artifactPath."
        }
        logger.lifecycle(
            "Verified {} resolves from Loom's local repository: {}",
            "$generatedMinecraftGroup:$generatedMinecraftModule:$generatedMinecraftVersion",
            repositoryPath,
        )
    }
}

tasks.named("check") {
    dependsOn(verifyGradleWrapper, verifyModuleBoundaries, verifyGeneratedMinecraftRepository)
}
