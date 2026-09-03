package org.minecraftprot.stackframe.renderer;

import java.util.OptionalInt;

/** A caller-supplied terminal width or the deterministic unknown-width fallback. */
public record RenderWidth(OptionalInt columns) {
    public static final int UNKNOWN_FALLBACK = 80;
    public static final int MAXIMUM_TARGET = 100;

    public RenderWidth {
        if (columns == null) {
            throw new IllegalArgumentException("columns must not be null");
        }
        if (columns.isPresent() && columns.getAsInt() <= 0) {
            throw new IllegalArgumentException("known columns must be positive");
        }
    }

    public static RenderWidth known(int columns) {
        return new RenderWidth(OptionalInt.of(columns));
    }

    public static RenderWidth unknown() {
        return new RenderWidth(OptionalInt.empty());
    }

    public int targetColumns() {
        return columns.isPresent()
                ? Math.min(columns.getAsInt(), MAXIMUM_TARGET)
                : UNKNOWN_FALLBACK;
    }

    public boolean isKnown() {
        return columns.isPresent();
    }
}
