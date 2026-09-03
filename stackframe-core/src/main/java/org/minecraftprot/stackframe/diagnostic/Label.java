package org.minecraftprot.stackframe.diagnostic;

/**
 * Post-redaction source annotation. Its code-point range must resolve within the
 * containing excerpt; overlapping labels retain producer order.
 */
public record Label(
        SourceRange range,
        LabelStyle style,
        DisplayText message,
        BoundedList<EvidenceId> evidenceIds) {

    public Label {
        range = Validation.required(range, "$.label.range");
        style = Validation.required(style, "$.label.style");
        message = Validation.required(message, "$.label.message");
        if (message.value().isBlank()) {
            throw new TextValidationException("$.label.message", "must not be blank");
        }
        evidenceIds = Validation.required(evidenceIds, "$.label.evidenceIds");
        Validation.size(evidenceIds.items(), ModelLimits.EVIDENCE_PER_NODE, "$.label.evidenceIds");
    }
}
