package org.minecraftprot.stackframe.diagnostic.registry;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
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
        assertTrue(first.contains("`fallback-conflict`"));
        assertTrue(first.contains("`excluded-low-confidence`"));
        assertTrue(first.contains(ArbitrationReasonAssignment.CONTRACT_ID));
        assertTrue(first.contains("`RESTORE_FROM_BACKUP`"));
    }

    @Test
    void generationUsesAsciiUnderNonLatinDefaultLocale() {
        var expectedCatalog = RegistryCatalogGenerator.generate(REGISTRY);
        var expectedBaseline = RegistryCompatibility.baseline(REGISTRY);
        var previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("ar-EG"));
            assertEquals("SF0000", DiagnosticArea.GENERIC_INTERNAL.code(0).value());
            assertEquals("SF5123", DiagnosticArea.STORAGE_ENVIRONMENT.code(123).value());
            assertEquals(expectedCatalog, RegistryCatalogGenerator.generate(REGISTRY));
            assertEquals(expectedBaseline, RegistryCompatibility.baseline(REGISTRY));
        } finally {
            Locale.setDefault(previous);
        }
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
                        baseline.replace(
                                "GENERIC_INTERNAL\t0\t0\t999\tSF0xxx",
                                "GENERIC_INTERNAL\t9\t1\t999\tSF9xxx"),
                        baseline.replace(
                                "Storage, permissions, and environment",
                                "Changed empty-area meaning"),
                        baseline.replace("SCOPE", "OWNERSHIP"),
                        baseline.replace("true\ttrue\tfalse", "false\ttrue\tfalse"),
                        baseline.replace("INSPECT_ONLY", "REVERSIBLE_STATE_CHANGE"),
                        baseline.replace(
                                "sf0001-unexpected-operation",
                                "different-documentation-anchor"))
                .forEach(changed -> assertThrows(
                        RegistryValidationException.class,
                        () -> RegistryCompatibility.verify(REGISTRY, changed)));
        for (var area : DiagnosticArea.values()) {
            assertTrue(baseline.contains(
                    area.name() + "\t" + area.rangeDigit() + "\t"
                            + area.minimumSuffix() + "\t" + area.maximumSuffix()));
        }
    }

    @Test
    void compatibilityProtectsClassifierAndArbitrationMetadata() {
        var fallback = REGISTRY.genericFallback();
        var specialized = RegistryFixtures.specialized(
                "SF1001", "lifecycle.example", DiagnosticLifecycle.ACTIVE);
        var registry = DiagnosticRegistrySnapshot.of(
                List.of(fallback, specialized),
                List.of(new ClassifierRegistration(
                        "lifecycle.example",
                        specialized.code(),
                        25,
                        CombinationPolicy.COMPATIBLE_FACTS)));
        var baseline = RegistryCompatibility.baseline(registry);
        var catalog = RegistryCatalogGenerator.generate(registry);

        assertTrue(catalog.contains(
                "| `lifecycle.example` | `SF1001` | 25 | `COMPATIBLE_FACTS` |"));

        Stream.of(
                        baseline.replace("lifecycle.example\tSF1001\t25", "lifecycle.renamed\tSF1001\t25"),
                        baseline.replace("\t25\tCOMPATIBLE_FACTS", "\t24\tCOMPATIBLE_FACTS"),
                        baseline.replace("COMPATIBLE_FACTS", "NEVER"),
                        baseline.replace("fallback-conflict", "fallback-disagreement"),
                        baseline.replace(
                                "excluded-low-confidence\tNON_SELECTED_CANDIDATE",
                                "excluded-low-confidence\tFALLBACK_SELECTION"),
                        baseline.replace(
                                ArbitrationReasonAssignment.CONTRACT_ID,
                                "arbitration-reason-assignment-v2"),
                        baseline.replace(
                                "RESTORE_FROM_BACKUP\tDESTRUCTIVE_STATE_CHANGE",
                                "RESTORE_FROM_BACKUP\tINSPECT_ONLY"))
                .forEach(changed -> assertThrows(
                        RegistryValidationException.class,
                        () -> RegistryCompatibility.verify(registry, changed)));
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
