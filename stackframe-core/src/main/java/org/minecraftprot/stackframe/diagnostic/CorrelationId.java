package org.minecraftprot.stackframe.diagnostic;

import java.util.regex.Pattern;

/** Opaque 6-26 character uppercase Crockford Base32 correlation token. */
public record CorrelationId(String value) {
    private static final Pattern PATTERN =
            Pattern.compile("[0-9A-HJKMNP-TV-Z]{6,26}");

    public CorrelationId {
        value = Validation.identifier(
                value, PATTERN, "$.correlationId", "[0-9A-HJKMNP-TV-Z]{6,26}");
    }
}
