package org.minecraftprot.stackframe.diagnostic.registry;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class RegistryGovernanceTest {
    private static final DiagnosticRegistrySnapshot REGISTRY =
            CanonicalDiagnosticRegistry.snapshot();

    @Test
    void generatedCatalogIsDeterministicAndSearchable() {
        var first = RegistryCatalogGenerator.generate(REGISTRY);
        var second = RegistryCatalogGenerator.generate(REGISTRY);

        assertEquals(first, second);
        assertTrue(first.contains("## SF0001 - an unexpected server operation failed"));
        assertTrue(first.contains("generic.unexpected-operation"));
    }

    @Test
    void detectsGeneratedCatalogDrift() {
        var generated = RegistryCatalogGenerator.generate(REGISTRY);
        assertDoesNotThrow(() -> RegistryCatalogGenerator.verify(REGISTRY, generated));
        assertThrows(
                RegistryValidationException.class,
                () -> RegistryCatalogGenerator.verify(
                        REGISTRY, generated.replace("SF0001", "SF0002")));
    }

    @Test
    void detectsCompatibilityDeletionReassignmentAndContractWeakening() {
        var baseline = RegistryCompatibility.baseline(REGISTRY);
        assertDoesNotThrow(() -> RegistryCompatibility.verify(REGISTRY, baseline));

        Stream.of(
                        "",
                        baseline.replace("SF0001", "SF0002"),
                        baseline.replace("generic.unexpected-operation", "generic.reassigned"),
                        baseline.replace("GENERIC_INTERNAL", "LIFECYCLE_STARTUP"),
                        baseline.replace("SCOPE", "OWNERSHIP"),
                        baseline.replace("true\ttrue\tfalse", "false\ttrue\tfalse"),
                        baseline.replace("INSPECT_ONLY", "REVERSIBLE_STATE_CHANGE"),
                        baseline.replace(
                                "sf0001-unexpected-operation",
                                "different-documentation-anchor"))
                .forEach(changed -> assertThrows(
                        RegistryValidationException.class,
                        () -> RegistryCompatibility.verify(REGISTRY, changed)));
    }

    @Test
    void committedGeneratedArtifactsHaveNoDrift() throws IOException {
        var projectDirectory = Path.of(System.getProperty("user.dir"));
        var catalog = Files.readString(
                projectDirectory.getParent()
                        .resolve("docs/diagnostic-registry/catalog.md"),
                StandardCharsets.UTF_8);
        var baseline = Files.readString(
                projectDirectory.resolve(
                        "src/main/resources/org/minecraftprot/stackframe/diagnostic/registry/"
                                + "compatibility-baseline.tsv"),
                StandardCharsets.UTF_8);

        assertDoesNotThrow(() -> RegistryCatalogGenerator.verify(REGISTRY, catalog));
        assertDoesNotThrow(() -> RegistryCompatibility.verify(REGISTRY, baseline));
    }
}
