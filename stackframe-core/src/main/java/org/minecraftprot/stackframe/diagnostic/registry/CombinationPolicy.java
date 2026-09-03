package org.minecraftprot.stackframe.diagnostic.registry;

/** Registry-reviewed policy for combining candidates with the same identity. */
public enum CombinationPolicy {
    NEVER("Candidates remain separate and cannot be merged."),
    COMPATIBLE_FACTS(
            "Candidates may merge only when identity and required facts agree under ADR 004.");

    private final String meaning;

    CombinationPolicy(String meaning) {
        this.meaning = meaning;
    }

    public String meaning() {
        return meaning;
    }
}
