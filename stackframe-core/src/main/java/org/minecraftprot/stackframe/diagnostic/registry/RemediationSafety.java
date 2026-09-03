package org.minecraftprot.stackframe.diagnostic.registry;

/** Maximum operator action authorized by a registry entry. */
public enum RemediationSafety {
    NONE(false),
    INSPECT_ONLY(false),
    REVERSIBLE_STATE_CHANGE(true);

    private final boolean mutatesState;

    RemediationSafety(boolean mutatesState) {
        this.mutatesState = mutatesState;
    }

    public boolean mutatesState() {
        return mutatesState;
    }
}
