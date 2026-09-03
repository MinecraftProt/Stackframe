package org.minecraftprot.stackframe.diagnostic;

import java.util.regex.Pattern;

/**
 * Opaque occurrence or record identifier. Producers own uniqueness and must not
 * encode timestamps, host names, paths, users, secrets, or diagnostic meaning.
 */
public record DiagnosticId(String value) {
    private static final Pattern PATTERN =
            Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{7,63}");

    public DiagnosticId {
        value = Validation.identifier(
                value, PATTERN, "$.diagnosticId", "[A-Za-z0-9][A-Za-z0-9._:-]{7,63}");
    }
}
