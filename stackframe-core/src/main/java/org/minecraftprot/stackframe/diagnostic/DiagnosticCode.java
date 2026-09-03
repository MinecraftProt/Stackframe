package org.minecraftprot.stackframe.diagnostic;

import java.util.regex.Pattern;

/** Stable six-character semantic code. Allocation and uniqueness belong to the code registry. */
public record DiagnosticCode(String value) {
    private static final Pattern PATTERN = Pattern.compile("SF[0-9]{4}");

    public DiagnosticCode {
        value = Validation.identifier(value, PATTERN, "$.code", "SF[0-9]{4}");
    }
}
