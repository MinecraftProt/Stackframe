package org.minecraftprot.stackframe.normalization;

import java.util.Objects;
import java.util.Optional;
import org.minecraftprot.stackframe.diagnostic.CandidateText;

/**
 * Bounded pre-redaction text copied from a throwable scalar.
 *
 * <p>The value may still contain sensitive or unsafe external text. It must remain
 * short-lived and pass through redaction before entering a completed diagnostic.
 * Length accounting uses source UTF-16 units so truncation can be reported without
 * scanning an unbounded suffix. Extra inspected units record malformed-surrogate
 * lookahead so the operation-wide scalar-work count remains exact.
 */
public record NormalizedText(
        CandidateText value,
        int sourceUtf16Length,
        int omittedUtf16Units,
        int malformedUtf16Units,
        int extraInspectedUtf16Units,
        Optional<TruncationReason> truncationReason) {
    public enum TruncationReason {
        PER_VALUE_CODE_POINT_LIMIT,
        TOTAL_CODE_POINT_LIMIT,
        TOTAL_UTF8_BYTE_LIMIT,
        SCALAR_WORK_LIMIT
    }

    public NormalizedText {
        Objects.requireNonNull(value, "value");
        NormalizationValidation.nonNegative(sourceUtf16Length, "sourceUtf16Length");
        NormalizationValidation.nonNegative(omittedUtf16Units, "omittedUtf16Units");
        NormalizationValidation.nonNegative(malformedUtf16Units, "malformedUtf16Units");
        NormalizationValidation.nonNegative(
                extraInspectedUtf16Units, "extraInspectedUtf16Units");
        Objects.requireNonNull(truncationReason, "truncationReason");
        if ((long) value.value().length() + omittedUtf16Units != sourceUtf16Length) {
            throw new IllegalArgumentException(
                    "retained and omitted UTF-16 units must equal sourceUtf16Length");
        }
        if (malformedUtf16Units > value.value().length()) {
            throw new IllegalArgumentException(
                    "malformedUtf16Units must not exceed retained UTF-16 units");
        }
        if (extraInspectedUtf16Units > malformedUtf16Units) {
            throw new IllegalArgumentException(
                    "extraInspectedUtf16Units must not exceed malformedUtf16Units");
        }
        if ((omittedUtf16Units > 0) != truncationReason.isPresent()) {
            throw new IllegalArgumentException(
                    "truncationReason is required exactly when text was omitted");
        }
    }

    public boolean truncated() {
        return omittedUtf16Units > 0;
    }
}
