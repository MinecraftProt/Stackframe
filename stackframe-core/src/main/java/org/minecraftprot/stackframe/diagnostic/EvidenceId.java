package org.minecraftprot.stackframe.diagnostic;

import java.util.regex.Pattern;

/** Node-local evidence reference key, unique only within its diagnostic node. */
public record EvidenceId(String value) {
    private static final Pattern PATTERN = Pattern.compile("[a-z][a-z0-9-]{0,31}");

    public EvidenceId {
        value = Validation.identifier(value, PATTERN, "$.evidenceId", "[a-z][a-z0-9-]{0,31}");
    }
}
