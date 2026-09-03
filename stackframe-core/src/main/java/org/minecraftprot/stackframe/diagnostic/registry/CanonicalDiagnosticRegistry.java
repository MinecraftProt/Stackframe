package org.minecraftprot.stackframe.diagnostic.registry;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.minecraftprot.stackframe.diagnostic.CatalogText;
import org.minecraftprot.stackframe.diagnostic.DiagnosticCode;

/** Version-controlled canonical diagnostic registry. */
public final class CanonicalDiagnosticRegistry {
    public static final DiagnosticCode GENERIC_FALLBACK_CODE = new DiagnosticCode("SF0001");

    private static final DiagnosticRegistrySnapshot SNAPSHOT =
            DiagnosticRegistrySnapshot.of(List.of(genericFallbackEntry()));

    private CanonicalDiagnosticRegistry() {
    }

    public static DiagnosticRegistrySnapshot snapshot() {
        return SNAPSHOT;
    }

    private static RegistryEntry genericFallbackEntry() {
        return new RegistryEntry(
                GENERIC_FALLBACK_CODE,
                "generic.unexpected-operation",
                DiagnosticArea.GENERIC_INTERNAL,
                "core-diagnostics",
                new CatalogText(
                        "generic.unexpected-operation.title",
                        "an unexpected server operation failed"),
                "Reports an unexpected operation failure when no safe specialized diagnosis is available.",
                new EvidenceRequirement(
                        "Verified operation scope, redacted exception type when available, and trace preservation facts only.",
                        Set.of(EvidenceCapability.SCOPE, EvidenceCapability.FACT)),
                new FallbackPolicy(
                        "Emit SF0001 without inferred cause or blame; if completion fails, emit the original event unchanged.",
                        GENERIC_FALLBACK_CODE,
                        true,
                        true,
                        false),
                RemediationPolicy.inspectOnly(
                        RemediationAction.INSPECT_CORRELATED_TRACE,
                        RemediationAction.CREATE_SANITIZED_SUPPORT_BUNDLE),
                DiagnosticLifecycle.ACTIVE,
                Optional.empty(),
                "sf0001-unexpected-operation");
    }
}
