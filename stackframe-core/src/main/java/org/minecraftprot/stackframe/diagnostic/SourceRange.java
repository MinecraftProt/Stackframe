package org.minecraftprot.stackframe.diagnostic;

/**
 * Verified one-based source range with an end-exclusive end position. A point or
 * zero-width span is represented by absence, not by an equal start and end.
 */
public record SourceRange(SourcePosition start, SourcePosition end) {
    public SourceRange {
        start = Validation.required(start, "$.sourceRange.start");
        end = Validation.required(end, "$.sourceRange.end");
        if (start.compareTo(end) >= 0) {
            throw new RangeValidationException(
                    "$.sourceRange", "start must strictly precede the end-exclusive end");
        }
    }
}
