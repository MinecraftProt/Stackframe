package org.minecraftprot.stackframe.diagnostic;

/**
 * Exact omission accounting for one repeated field. It contains only a resolvable
 * logical path, positive count, and reason, never omitted content.
 */
public record Omission(ModelPath affectedPath, int omittedCount, OmissionReason reason) {
    public Omission {
        affectedPath = Validation.required(affectedPath, "$.omission.affectedPath");
        if (omittedCount < 1) {
            throw new OmissionValidationException("$.omission.omittedCount", "must be positive");
        }
        reason = Validation.required(reason, "$.omission.reason");
    }
}
