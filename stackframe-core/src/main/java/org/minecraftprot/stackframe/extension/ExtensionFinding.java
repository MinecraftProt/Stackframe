package org.minecraftprot.stackframe.extension;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** One namespaced claim with independently sourced pre-redaction evidence. */
public record ExtensionFinding(ExtensionCode code, List<ExtensionEvidence> evidence) {
    public ExtensionFinding {
        if (code == null || evidence == null || evidence.isEmpty() || evidence.size() > 32
                || evidence.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("finding requires 1-32 evidence items");
        }
        evidence = List.copyOf(evidence);
        var sourceKeys = new HashSet<String>();
        for (var item : evidence) {
            if (!sourceKeys.add(item.sourceKey())) {
                throw new IllegalArgumentException("duplicate evidence source key");
            }
        }
    }
}
