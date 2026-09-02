package org.minecraftprot.stackframe.diagnostic;

/** Sensitivity classification assigned before transformation into display-safe text. */
public enum Sensitivity {
    PUBLIC,
    SERVER_SENSITIVE,
    PERSONAL,
    SECRET,
    WORLD_DATA
}
