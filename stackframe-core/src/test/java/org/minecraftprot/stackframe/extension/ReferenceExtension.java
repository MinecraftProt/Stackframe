package org.minecraftprot.stackframe.extension;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.minecraftprot.stackframe.diagnostic.CandidateText;
import org.minecraftprot.stackframe.diagnostic.EvidenceKind;
import org.minecraftprot.stackframe.diagnostic.TextDisposition;
import org.minecraftprot.stackframe.diagnostic.registry.EvidenceCapability;

/** Minimal third-party example: a hint, not a claim of root cause or ownership. */
public final class ReferenceExtension implements DiagnosticExtension {
    private static final ExtensionNamespace NAMESPACE = new ExtensionNamespace("example_mod");
    private static final ExtensionCode CODE = new ExtensionCode(NAMESPACE, "illegal-state-context");

    public static ExtensionRegistration registration() {
        return new ExtensionRegistration(
                NAMESPACE, ExtensionRegistration.API_MAJOR, Set.of(CODE),
                new ReferenceExtension());
    }

    @Override
    public List<ExtensionFinding> inspect(ExtensionEvent event) {
        if (event.failureType().disposition() != TextDisposition.VISIBLE
                || !event.failureType().value().equals("java.lang.IllegalStateException")) {
            return List.of();
        }
        var evidence = new ArrayList<ExtensionEvidence>();
        evidence.add(new ExtensionEvidence(
                "failure-type",
                EvidenceKind.OTHER,
                ExtensionEvidence.Strength.HEURISTIC,
                Set.of(EvidenceCapability.FACT),
                new CandidateText(event.failureType().value())));
        var traceState = event.context().get("trace-state");
        if (traceState != null && traceState.disposition() == TextDisposition.VISIBLE) {
            evidence.add(new ExtensionEvidence(
                    "trace-state",
                    EvidenceKind.STRUCTURED_METADATA,
                    ExtensionEvidence.Strength.CONTEXTUAL,
                    Set.of(EvidenceCapability.FACT),
                    new CandidateText(traceState.value())));
        }
        return List.of(new ExtensionFinding(CODE, evidence));
    }
}
