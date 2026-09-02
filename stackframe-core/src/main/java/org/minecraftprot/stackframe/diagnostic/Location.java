package org.minecraftprot.stackframe.diagnostic;

import java.util.Optional;

/**
 * Verified operator-actionable location ordered by the producer from most useful
 * to least useful. Position and excerpt coordinates use one-based Unicode code
 * points after redaction.
 */
public record Location(
        LocationId id,
        LocationKind kind,
        DisplayText display,
        Optional<SourceRange> position,
        Optional<Excerpt> excerpt,
        BoundedList<EvidenceId> evidenceIds) {

    public Location {
        id = Validation.required(id, "$.location.id");
        kind = Validation.required(kind, "$.location.kind");
        display = Validation.required(display, "$.location.display");
        if (display.value().isBlank()) {
            throw new TextValidationException("$.location.display", "must not be blank");
        }
        var length = display.value().codePointCount(0, display.value().length());
        if (length > ModelLimits.LOCATION_CODE_POINTS) {
            throw new LimitValidationException(
                    "$.location.display",
                    "contains " + length + " code points; maximum is "
                            + ModelLimits.LOCATION_CODE_POINTS);
        }
        position = Validation.optional(position, "$.location.position");
        excerpt = Validation.optional(excerpt, "$.location.excerpt");
        evidenceIds = Validation.required(evidenceIds, "$.location.evidenceIds");
        Validation.size(evidenceIds.items(), ModelLimits.EVIDENCE_PER_NODE, "$.location.evidenceIds");
    }
}
