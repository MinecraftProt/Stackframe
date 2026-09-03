package org.minecraftprot.stackframe.diagnostic.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.minecraftprot.stackframe.diagnostic.DiagnosticCode;

class DiagnosticRegistrySnapshotTest {
    @Test
    void rejectsDuplicateCodesKeysAndAnchors() {
        var fallback = CanonicalDiagnosticRegistry.snapshot().genericFallback();
        var entry = RegistryFixtures.specialized(
                "SF1001", "lifecycle.first", DiagnosticLifecycle.ACTIVE);
        var duplicateCode = RegistryFixtures.specialized(
                "SF1001", "lifecycle.second", DiagnosticLifecycle.ACTIVE);
        assertThrows(
                RegistryValidationException.class,
                () -> DiagnosticRegistrySnapshot.of(List.of(fallback, entry, duplicateCode)));

        var duplicateKey = RegistryFixtures.specialized(
                "SF1002", "lifecycle.first", DiagnosticLifecycle.ACTIVE);
        assertThrows(
                RegistryValidationException.class,
                () -> DiagnosticRegistrySnapshot.of(List.of(fallback, entry, duplicateKey)));

        var duplicateAnchorSource = RegistryFixtures.specialized(
                "SF1003", "lifecycle-third", DiagnosticLifecycle.ACTIVE);
        var duplicateAnchor = new RegistryEntry(
                duplicateAnchorSource.code(),
                duplicateAnchorSource.symbolicKey(),
                duplicateAnchorSource.area(),
                duplicateAnchorSource.owner(),
                duplicateAnchorSource.title(),
                duplicateAnchorSource.meaning(),
                duplicateAnchorSource.evidence(),
                duplicateAnchorSource.fallback(),
                duplicateAnchorSource.remediation(),
                duplicateAnchorSource.lifecycle(),
                duplicateAnchorSource.replacementCode(),
                entry.documentationAnchor());
        assertThrows(
                RegistryValidationException.class,
                () -> DiagnosticRegistrySnapshot.of(List.of(fallback, entry, duplicateAnchor)));
    }

    @Test
    void providesDeterministicOrderingAndLookups() {
        var fallback = CanonicalDiagnosticRegistry.snapshot().genericFallback();
        var later = RegistryFixtures.specialized(
                "SF3002", "mods.later", DiagnosticLifecycle.ACTIVE);
        var earlier = RegistryFixtures.specialized(
                "SF1001", "lifecycle.earlier", DiagnosticLifecycle.ACTIVE);
        var registry = DiagnosticRegistrySnapshot.of(List.of(later, fallback, earlier));

        assertEquals(
                List.of("SF0001", "SF1001", "SF3002"),
                registry.entries().stream().map(entry -> entry.code().value()).toList());
        assertSame(earlier, registry.find("SF1001").orElseThrow());
        assertSame(
                later,
                registry.findBySymbolicKey("mods.later").orElseThrow());
        assertSame(
                earlier,
                registry.findByDocumentationAnchor("lifecycle-earlier").orElseThrow());
        assertTrue(registry.find(new DiagnosticCode("SF5001")).isEmpty());
        assertThrows(
                RegistryValidationException.class,
                () -> registry.find(new DiagnosticCode("SF6000")));
    }

    @Test
    void snapshotsAndNestedCollectionsAreImmutable() {
        var source = new ArrayList<>(CanonicalDiagnosticRegistry.snapshot().entries());
        var registry = DiagnosticRegistrySnapshot.of(source);
        source.clear();

        assertEquals(1, registry.entries().size());
        assertThrows(
                UnsupportedOperationException.class,
                () -> registry.entries().add(registry.genericFallback()));
        assertThrows(
                UnsupportedOperationException.class,
                () -> registry.genericFallback()
                        .evidence()
                        .capabilities()
                        .add(EvidenceCapability.OWNERSHIP));
        assertThrows(
                UnsupportedOperationException.class,
                () -> registry.availableCodes(DiagnosticArea.GENERIC_INTERNAL).clear());
    }

    @Test
    void separatesReservedActiveDeprecatedAndAvailableCodes() {
        var fallback = CanonicalDiagnosticRegistry.snapshot().genericFallback();
        var reserved = RegistryFixtures.specialized(
                "SF1001", "lifecycle.reserved", DiagnosticLifecycle.RESERVED);
        var active = RegistryFixtures.specialized(
                "SF1002", "lifecycle.active", DiagnosticLifecycle.ACTIVE);
        var deprecated = RegistryFixtures.specialized(
                "SF1003", "lifecycle.deprecated", DiagnosticLifecycle.DEPRECATED);
        var registry = DiagnosticRegistrySnapshot.of(
                List.of(deprecated, active, fallback, reserved));

        assertEquals(List.of(reserved), registry.entries(DiagnosticLifecycle.RESERVED));
        assertEquals(
                List.of(fallback, active),
                registry.entries(DiagnosticLifecycle.ACTIVE));
        assertEquals(List.of(deprecated), registry.entries(DiagnosticLifecycle.DEPRECATED));
        assertFalse(registry.availableCodes(DiagnosticArea.LIFECYCLE_STARTUP)
                .contains(new DiagnosticCode("SF1001")));
        assertTrue(registry.availableCodes(DiagnosticArea.LIFECYCLE_STARTUP)
                .contains(new DiagnosticCode("SF1000")));
    }

    @Test
    void enforcesLifecycleReplacementRules() {
        var active = RegistryFixtures.specialized(
                "SF1001", "lifecycle.active", DiagnosticLifecycle.ACTIVE);
        assertThrows(RegistryValidationException.class, () -> new RegistryEntry(
                active.code(),
                active.symbolicKey(),
                active.area(),
                active.owner(),
                active.title(),
                active.meaning(),
                active.evidence(),
                active.fallback(),
                active.remediation(),
                DiagnosticLifecycle.DEPRECATED,
                Optional.empty(),
                active.documentationAnchor()));

        var fallback = CanonicalDiagnosticRegistry.snapshot().genericFallback();
        var deprecated = RegistryFixtures.specialized(
                "SF1002", "lifecycle.deprecated", DiagnosticLifecycle.DEPRECATED);
        assertEquals(
                3,
                DiagnosticRegistrySnapshot.of(List.of(fallback, active, deprecated))
                        .entries()
                        .size());
    }

    @Test
    void requiresAndReturnsStableGenericFallback() {
        assertThrows(
                RegistryValidationException.class,
                () -> DiagnosticRegistrySnapshot.of(List.of()));
        var registry = CanonicalDiagnosticRegistry.snapshot();
        assertEquals("SF0001", registry.genericFallback().code().value());
        assertEquals(
                "generic.unexpected-operation",
                registry.genericFallback().symbolicKey());
        assertEquals(DiagnosticLifecycle.ACTIVE, registry.genericFallback().lifecycle());
    }
}
