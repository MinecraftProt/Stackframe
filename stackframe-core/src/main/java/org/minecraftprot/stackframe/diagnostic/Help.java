package org.minecraftprot.stackframe.diagnostic;

/** Evidence-backed action or inspection guidance; evidence references are always non-empty. */
public record Help(DisplayText text, HelpKind kind, BoundedList<EvidenceId> evidenceIds) {
    public Help {
        text = Validation.required(text, "$.help.text");
        if (text.value().isBlank()) {
            throw new TextValidationException("$.help.text", "must not be blank");
        }
        kind = Validation.required(kind, "$.help.kind");
        evidenceIds = Validation.required(evidenceIds, "$.help.evidenceIds");
        Validation.size(evidenceIds.items(), ModelLimits.EVIDENCE_PER_NODE, "$.help.evidenceIds");
        if (evidenceIds.items().isEmpty()) {
            throw new ReferenceValidationException("$.help.evidenceIds", "must retain at least one evidence ID");
        }
    }
}
