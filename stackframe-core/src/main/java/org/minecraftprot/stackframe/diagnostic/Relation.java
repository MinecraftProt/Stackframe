package org.minecraftprot.stackframe.diagnostic;

/** Semantic relationship between a parent diagnostic and one ordered child. */
public enum Relation {
    CAUSE,
    SUPPRESSED,
    RELATED,
    AGGREGATE_ITEM
}
