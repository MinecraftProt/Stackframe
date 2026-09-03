package org.minecraftprot.stackframe.diagnostic;

import java.util.regex.Pattern;

/**
 * Loader-neutral ASCII key whose vocabulary is owned by another contract, such
 * as classifier arbitration. The completed model preserves but does not
 * interpret it.
 */
public record OpaqueIdentifier(String value) {
    private static final Pattern PATTERN =
            Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,95}");

    public OpaqueIdentifier {
        value = Validation.identifier(
                value, PATTERN, "$.opaqueIdentifier", "[A-Za-z0-9][A-Za-z0-9._:-]{0,95}");
    }
}
