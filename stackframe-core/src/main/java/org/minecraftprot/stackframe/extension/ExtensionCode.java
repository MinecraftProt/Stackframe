package org.minecraftprot.stackframe.extension;

import java.util.regex.Pattern;

/** Namespaced extension finding identity, distinct from governed {@code SF####} codes. */
public record ExtensionCode(ExtensionNamespace namespace, String name) {
    private static final Pattern VALID = Pattern.compile("[a-z][a-z0-9-]{0,63}");

    public ExtensionCode {
        if (namespace == null || name == null || !VALID.matcher(name).matches()) {
            throw new IllegalArgumentException("invalid extension code");
        }
    }

    public String qualified() {
        return namespace.value() + ":" + name;
    }
}
