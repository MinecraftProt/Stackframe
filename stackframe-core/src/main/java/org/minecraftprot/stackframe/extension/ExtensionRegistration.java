package org.minecraftprot.stackframe.extension;

import java.util.Objects;
import java.util.Set;

/** Declares one extension owner and all finding codes it may return. */
public record ExtensionRegistration(
        ExtensionNamespace namespace,
        int apiMajor,
        Set<ExtensionCode> codes,
        DiagnosticExtension callback) {
    public static final int API_MAJOR = 1;

    public ExtensionRegistration {
        if (namespace == null || apiMajor != API_MAJOR
                || codes == null || codes.isEmpty() || codes.size() > 32
                || codes.stream().anyMatch(Objects::isNull) || callback == null) {
            throw new IllegalArgumentException("invalid extension registration");
        }
        codes = Set.copyOf(codes);
        for (var code : codes) {
            if (!code.namespace().equals(namespace)) {
                throw new IllegalArgumentException("finding code belongs to another namespace");
            }
        }
    }
}
