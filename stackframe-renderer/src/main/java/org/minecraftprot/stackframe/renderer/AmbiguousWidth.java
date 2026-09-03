package org.minecraftprot.stackframe.renderer;

/** Column width assigned to Unicode East Asian Ambiguous characters. */
public enum AmbiguousWidth {
    NARROW(1),
    WIDE(2);

    private final int columns;

    AmbiguousWidth(int columns) {
        this.columns = columns;
    }

    int columns() {
        return columns;
    }
}
