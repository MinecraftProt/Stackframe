package org.minecraftprot.stackframe.diagnostic.registry;

/** Subject whose deterministic arbitration outcome is explained by a reason code. */
public enum ArbitrationReasonScope {
    SELECTED_CANDIDATE,
    NON_SELECTED_CANDIDATE,
    FALLBACK_SELECTION,
    CLASSIFIER
}
