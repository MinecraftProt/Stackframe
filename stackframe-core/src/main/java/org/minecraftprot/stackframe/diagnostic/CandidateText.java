package org.minecraftprot.stackframe.diagnostic;

/**
 * Bounded pre-redaction input. It may retain protected external text and must
 * never be stored in a {@link DiagnosticDocument}; redaction policy owns its
 * conversion to {@link DisplayText}.
 */
public record CandidateText(String value) {
    public CandidateText {
        Validation.required(value, "$.candidateText");
        var codePoints = value.codePointCount(0, value.length());
        if (codePoints > ModelLimits.TEXT_CODE_POINTS) {
            throw new LimitValidationException(
                    "$.candidateText",
                    "contains " + codePoints + " code points; maximum is " + ModelLimits.TEXT_CODE_POINTS);
        }
        for (var index = 0; index < value.length(); index++) {
            if (Character.isSurrogate(value.charAt(index))) {
                if (!Character.isHighSurrogate(value.charAt(index))
                        || index + 1 >= value.length()
                        || !Character.isLowSurrogate(value.charAt(index + 1))) {
                    throw new TextValidationException(
                            "$.candidateText", "contains an unpaired UTF-16 surrogate");
                }
                index++;
            }
        }
    }
}
