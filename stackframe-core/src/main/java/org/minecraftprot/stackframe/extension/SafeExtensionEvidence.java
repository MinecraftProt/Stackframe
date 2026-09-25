package org.minecraftprot.stackframe.extension;

import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.minecraftprot.stackframe.diagnostic.DisplayText;
import org.minecraftprot.stackframe.diagnostic.EvidenceKind;
import org.minecraftprot.stackframe.diagnostic.registry.EvidenceCapability;

/** Evidence metadata after the extension host has removed its raw value. */
public record SafeExtensionEvidence(
        String sourceKey,
        EvidenceKind kind,
        ExtensionEvidence.Strength strength,
        Set<EvidenceCapability> capabilities,
        DisplayText value) {
    private static final Pattern SOURCE = Pattern.compile("[a-z][a-z0-9.-]{0,63}");

    public SafeExtensionEvidence {
        if (sourceKey == null || !SOURCE.matcher(sourceKey).matches()
                || kind == null || strength == null || capabilities == null
                || capabilities.isEmpty() || capabilities.stream().anyMatch(Objects::isNull)
                || value == null) {
            throw new IllegalArgumentException("invalid safe extension evidence");
        }
        capabilities = Set.copyOf(capabilities);
    }
}
