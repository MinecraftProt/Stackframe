package org.minecraftprot.stackframe.diagnostic.registry;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.minecraftprot.stackframe.diagnostic.CatalogText;
import org.minecraftprot.stackframe.diagnostic.DiagnosticCode;
import org.minecraftprot.stackframe.diagnostic.IdentifierValidationException;

class RegistryEntryValidationTest {
    @ParameterizedTest
    @ValueSource(strings = {"SF6000", "SF9999", "SF0000"})
    void rejectsUnallocatedOrOutOfRangeCodes(String value) {
        var code = new DiagnosticCode(value);
        assertThrows(RegistryValidationException.class, () -> DiagnosticArea.forCode(code));
    }

    @ParameterizedTest
    @ValueSource(strings = {"sf0001", "SF001", "SG0001", "SF00A1"})
    void mergedModelRejectsMalformedCodes(String value) {
        assertThrows(IdentifierValidationException.class, () -> new DiagnosticCode(value));
    }

    @Test
    void rejectsAreaRangeMismatch() {
        assertThrows(RegistryValidationException.class, () -> new RegistryEntry(
                new DiagnosticCode("SF2001"),
                "data.example",
                DiagnosticArea.MODS_DEPENDENCIES,
                "core-diagnostics",
                new CatalogText("data.example.title", "data validation failed"),
                "Reports a data validation failure.",
                evidence(Set.of(EvidenceCapability.IDENTITY, EvidenceCapability.SCOPE)),
                RegistryFixtures.safeFallback(),
                RemediationPolicy.none("No remediation is authorized."),
                DiagnosticLifecycle.ACTIVE,
                Optional.empty(),
                "data-example"));
    }

    @Test
    void rejectsMissingOrIncompleteSpecializedEvidence() {
        assertThrows(
                RegistryValidationException.class,
                () -> new EvidenceRequirement("Missing capabilities.", Set.of()));
        assertThrows(RegistryValidationException.class, () -> new RegistryEntry(
                new DiagnosticCode("SF1001"),
                "lifecycle.example",
                DiagnosticArea.LIFECYCLE_STARTUP,
                "core-diagnostics",
                new CatalogText("lifecycle.example.title", "server startup failed"),
                "Reports a startup failure.",
                evidence(Set.of(EvidenceCapability.FACT)),
                RegistryFixtures.safeFallback(),
                RemediationPolicy.none("No remediation is authorized."),
                DiagnosticLifecycle.ACTIVE,
                Optional.empty(),
                "lifecycle-example"));
    }

    @Test
    void rejectsMissingOrUnsafeFallback() {
        assertThrows(RegistryValidationException.class, () -> new FallbackPolicy(
                "Unsafe fallback.",
                CanonicalDiagnosticRegistry.GENERIC_FALLBACK_CODE,
                false,
                true,
                false));
        assertThrows(RegistryValidationException.class, () -> new RegistryEntry(
                new DiagnosticCode("SF1001"),
                "lifecycle.example",
                DiagnosticArea.LIFECYCLE_STARTUP,
                "core-diagnostics",
                new CatalogText("lifecycle.example.title", "server startup failed"),
                "Reports a startup failure.",
                evidence(Set.of(EvidenceCapability.IDENTITY, EvidenceCapability.SCOPE)),
                null,
                RemediationPolicy.none("No remediation is authorized."),
                DiagnosticLifecycle.ACTIVE,
                Optional.empty(),
                "lifecycle-example"));
    }

    @Test
    void rejectsUnsafeStateChangingRemediation() {
        assertThrows(RegistryValidationException.class, () -> new RemediationPolicy(
                "Change server state.",
                RemediationSafety.REVERSIBLE_STATE_CHANGE,
                false,
                true,
                true,
                true));
        assertDoesNotThrow(() -> new RemediationPolicy(
                "Change reversible state only with evidence, confirmation, and backup.",
                RemediationSafety.REVERSIBLE_STATE_CHANGE,
                true,
                true,
                true,
                true));
    }

    @Test
    void genericFallbackCannotAuthorizeStateChanges() {
        var fallback = CanonicalDiagnosticRegistry.snapshot().genericFallback();
        var mutating = new RemediationPolicy(
                "Change reversible state only with evidence, confirmation, and backup.",
                RemediationSafety.REVERSIBLE_STATE_CHANGE,
                true,
                true,
                true,
                true);

        assertThrows(RegistryValidationException.class, () -> new RegistryEntry(
                fallback.code(),
                fallback.symbolicKey(),
                fallback.area(),
                fallback.owner(),
                fallback.title(),
                fallback.meaning(),
                new EvidenceRequirement(
                        fallback.evidence().description(),
                        Set.of(
                                EvidenceCapability.SCOPE,
                                EvidenceCapability.FACT,
                                EvidenceCapability.REMEDY)),
                fallback.fallback(),
                mutating,
                fallback.lifecycle(),
                fallback.replacementCode(),
                fallback.documentationAnchor()));
    }

    private static EvidenceRequirement evidence(Set<EvidenceCapability> capabilities) {
        return new EvidenceRequirement("Required classification evidence.", capabilities);
    }
}
