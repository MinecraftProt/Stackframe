package org.minecraftprot.stackframe.diagnostic.registry;

/** Allocation lifecycle; registered codes never become available for semantic reuse. */
public enum DiagnosticLifecycle {
    RESERVED,
    ACTIVE,
    DEPRECATED
}
