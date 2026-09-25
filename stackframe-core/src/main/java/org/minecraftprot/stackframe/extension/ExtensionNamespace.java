package org.minecraftprot.stackframe.extension;

import java.util.Set;
import java.util.regex.Pattern;

/** Stable owner namespace, normally the loader-verified mod ID. */
public record ExtensionNamespace(String value) {
    private static final Pattern VALID = Pattern.compile("[a-z][a-z0-9_-]{0,63}");
    private static final Set<String> RESERVED = Set.of("stackframe", "minecraft");

    public ExtensionNamespace {
        if (value == null || !VALID.matcher(value).matches() || RESERVED.contains(value)) {
            throw new IllegalArgumentException("invalid or reserved extension namespace");
        }
    }
}
