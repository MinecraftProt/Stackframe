package org.minecraftprot.stackframe.diagnostic;

import java.util.regex.Pattern;

/**
 * Trusted project-owned wording selected by a stable ASCII key. External input
 * must use the redaction boundary and cannot be promoted to catalog text.
 */
public record CatalogText(String key, String value) {
    private static final Pattern KEY_PATTERN =
            Pattern.compile("[a-z][a-z0-9.-]{0,95}");

    public CatalogText {
        key = Validation.identifier(
                key, KEY_PATTERN, "$.catalogText.key", "[a-z][a-z0-9.-]{0,95}");
        value = Validation.safeText(
                value, ModelLimits.TEXT_CODE_POINTS, false, "$.catalogText.value");
    }
}
