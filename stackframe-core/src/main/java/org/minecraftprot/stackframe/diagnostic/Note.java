package org.minecraftprot.stackframe.diagnostic;

/** Ordered evidence-backed fact; cause notes run from operator-facing to lower-level cause. */
public record Note(NoteKind kind, DisplayText text, BoundedList<EvidenceId> evidenceIds) {
    public Note {
        kind = Validation.required(kind, "$.note.kind");
        text = Validation.required(text, "$.note.text");
        if (text.value().isBlank()) {
            throw new TextValidationException("$.note.text", "must not be blank");
        }
        evidenceIds = Validation.required(evidenceIds, "$.note.evidenceIds");
        Validation.size(evidenceIds.items(), ModelLimits.EVIDENCE_PER_NODE, "$.note.evidenceIds");
    }
}
