package org.minecraftprot.stackframe.diagnostic;

/**
 * One absolute source line after redaction. Text excludes newline characters and
 * may be empty or whitespace-only to preserve faithful line numbering.
 */
public record ExcerptLine(int lineNumber, DisplayText text) {
    public ExcerptLine {
        Validation.positive(lineNumber, "$.excerptLine.lineNumber");
        text = Validation.required(text, "$.excerptLine.text");
        Validation.noNewline(text.value(), "$.excerptLine.text");
        var length = text.value().codePointCount(0, text.value().length());
        if (length > ModelLimits.EXCERPT_LINE_CODE_POINTS) {
            throw new LimitValidationException(
                    "$.excerptLine.text",
                    "contains " + length + " code points; maximum is "
                            + ModelLimits.EXCERPT_LINE_CODE_POINTS);
        }
    }
}
