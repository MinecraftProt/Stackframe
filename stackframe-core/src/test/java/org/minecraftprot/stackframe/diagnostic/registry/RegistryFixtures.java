package org.minecraftprot.stackframe.diagnostic.registry;

import java.util.Optional;
import java.util.Set;
import org.minecraftprot.stackframe.diagnostic.CatalogText;
import org.minecraftprot.stackframe.diagnostic.DiagnosticCode;

final class RegistryFixtures {
    private RegistryFixtures() {
    }

    static RegistryEntry specialized(String code, String key, DiagnosticLifecycle lifecycle) {
        var diagnosticCode = new DiagnosticCode(code);
        return new RegistryEntry(
                diagnosticCode,
                key,
                DiagnosticArea.forCode(diagnosticCode),
                "core-diagnostics",
                new CatalogText(key + ".title", "a specialized operation failed"),
                "Reports one specialized operation failure with verified identity and scope.",
                new EvidenceRequirement(
                        "Direct or corroborating evidence must establish identity and scope.",
                        Set.of(
                                EvidenceCapability.IDENTITY,
                                EvidenceCapability.SCOPE,
                                EvidenceCapability.FACT)),
                safeFallback(),
                RemediationPolicy.none(),
                lifecycle,
                lifecycle == DiagnosticLifecycle.DEPRECATED
                        ? Optional.of(CanonicalDiagnosticRegistry.GENERIC_FALLBACK_CODE)
                        : Optional.empty(),
                key.replace('.', '-'));
    }

    static FallbackPolicy safeFallback() {
        return new FallbackPolicy(
                "Use SF0001 without inferred cause or blame.",
                CanonicalDiagnosticRegistry.GENERIC_FALLBACK_CODE,
                true,
                true,
                false);
    }
}
