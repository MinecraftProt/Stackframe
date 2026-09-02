package org.minecraftprot.stackframe.diagnostic;

import java.util.regex.Pattern;

/** Node-local location reference key, unique only within its diagnostic node. */
public record LocationId(String value) {
    private static final Pattern PATTERN = Pattern.compile("[a-z][a-z0-9-]{0,31}");

    public LocationId {
        value = Validation.identifier(value, PATTERN, "$.locationId", "[a-z][a-z0-9-]{0,31}");
    }
}
