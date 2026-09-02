package org.minecraftprot.stackframe.diagnostic;

import java.util.Optional;

/**
 * Safe descriptive evidence without source-object retention. The optional source
 * is a redacted description, never a platform object or unrestricted path.
 */
public record EvidenceReference(
        EvidenceId id,
        EvidenceKind kind,
        DisplayText summary,
        Optional<DisplayText> source) {

    public EvidenceReference {
        id = Validation.required(id, "$.evidence.id");
        kind = Validation.required(kind, "$.evidence.kind");
        summary = Validation.required(summary, "$.evidence.summary");
        if (summary.value().isBlank()) {
            throw new TextValidationException("$.evidence.summary", "must not be blank");
        }
        source = Validation.optional(source, "$.evidence.source");
        source.ifPresent(value -> {
            if (value.value().isBlank()) {
                throw new TextValidationException("$.evidence.source", "must not be blank");
            }
        });
    }
}
