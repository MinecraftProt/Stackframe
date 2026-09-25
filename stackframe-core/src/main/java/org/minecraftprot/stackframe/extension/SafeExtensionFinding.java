package org.minecraftprot.stackframe.extension;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** Namespaced finding whose evidence values have crossed the safe output boundary. */
public record SafeExtensionFinding(
        ExtensionCode code, List<SafeExtensionEvidence> evidence) {
    public SafeExtensionFinding {
        if (code == null || evidence == null || evidence.isEmpty() || evidence.size() > 32
                || evidence.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("invalid safe extension finding");
        }
        evidence = List.copyOf(evidence);
        var sourceKeys = new HashSet<String>();
        for (var item : evidence) {
            if (!sourceKeys.add(item.sourceKey())) {
                throw new IllegalArgumentException("duplicate safe evidence source key");
            }
        }
    }
}
