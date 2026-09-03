package org.minecraftprot.stackframe.normalization;

import java.util.Objects;
import org.minecraftprot.stackframe.diagnostic.CandidateText;

/**
 * Bounded pre-redaction text copied from a throwable scalar.
 *
 * <p>The value may still contain sensitive or unsafe external text. It must remain
 * short-lived and pass through redaction before entering a completed diagnostic.
 * Length accounting uses source UTF-16 units so truncation can be reported without
 * scanning an unbounded suffix.
 */
public record NormalizedText(
        CandidateText value,
        int sourceUtf16Length,
        int omittedUtf16Units,
        int malformedUtf16Units) {
    public NormalizedText {
        Objects.requireNonNull(value, "value");
        NormalizationValidation.nonNegative(sourceUtf16Length, "sourceUtf16Length");
        NormalizationValidation.nonNegative(omittedUtf16Units, "omittedUtf16Units");
        NormalizationValidation.nonNegative(malformedUtf16Units, "malformedUtf16Units");
        if ((long) value.value().length() + omittedUtf16Units != sourceUtf16Length) {
            throw new IllegalArgumentException(
                    "retained and omitted UTF-16 units must equal sourceUtf16Length");
        }
        if (malformedUtf16Units > value.value().length()) {
            throw new IllegalArgumentException(
                    "malformedUtf16Units must not exceed retained UTF-16 units");
        }
    }

    public boolean truncated() {
        return omittedUtf16Units > 0;
    }
}
