package org.minecraftprot.stackframe.extension;

import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.minecraftprot.stackframe.diagnostic.CandidateText;
import org.minecraftprot.stackframe.diagnostic.EvidenceKind;
import org.minecraftprot.stackframe.diagnostic.registry.EvidenceCapability;

/** Untrusted, typed, pre-redaction evidence proposed by an extension. */
public record ExtensionEvidence(
        String sourceKey,
        EvidenceKind kind,
        Strength strength,
        Set<EvidenceCapability> capabilities,
        CandidateText value) {
    private static final Pattern SOURCE = Pattern.compile("[a-z][a-z0-9.-]{0,63}");

    public enum Strength {
        DIRECT,
        CORROBORATING,
        CONTEXTUAL,
        HEURISTIC
    }

    public ExtensionEvidence {
        if (sourceKey == null || !SOURCE.matcher(sourceKey).matches()
                || kind == null || strength == null || capabilities == null
                || capabilities.isEmpty() || capabilities.stream().anyMatch(Objects::isNull)
                || value == null || value.value().isBlank()) {
            throw new IllegalArgumentException("invalid extension evidence");
        }
        capabilities = Set.copyOf(capabilities);
        if ((strength == Strength.CONTEXTUAL || strength == Strength.HEURISTIC)
                && (capabilities.contains(EvidenceCapability.OWNERSHIP)
                        || capabilities.contains(EvidenceCapability.REMEDY))) {
            throw new IllegalArgumentException(
                    "contextual and heuristic evidence cannot assert ownership or remedy");
        }
        if (kind == EvidenceKind.MESSAGE_PATTERN && strength != Strength.HEURISTIC) {
            throw new IllegalArgumentException("message patterns are heuristic evidence");
        }
    }

    @Override
    public String toString() {
        return "ExtensionEvidence[sourceKey=" + sourceKey + ", kind=" + kind
                + ", strength=" + strength + ", capabilities=" + capabilities
                + ", value=<untrusted>]";
    }
}
