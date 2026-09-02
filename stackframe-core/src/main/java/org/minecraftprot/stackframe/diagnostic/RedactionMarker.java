package org.minecraftprot.stackframe.diagnostic;

import java.util.regex.Pattern;

/**
 * Stable redaction category only. It must not contain a removed value's length,
 * hash, prefix, suffix, or any other derivative.
 */
public record RedactionMarker(String category) {
    private static final Pattern PATTERN = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");

    public RedactionMarker {
        category = Validation.identifier(
                category, PATTERN, "$.redactionMarker", "[A-Z][A-Z0-9_]{0,63}");
    }
}
