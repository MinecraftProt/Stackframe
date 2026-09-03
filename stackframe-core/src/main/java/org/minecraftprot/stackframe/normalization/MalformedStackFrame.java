package org.minecraftprot.stackframe.normalization;

import java.util.Objects;

/** Explicit placeholder for malformed frame data at a retained array position. */
public record MalformedStackFrame(int originalIndex, Reason reason) implements NormalizedFrameEntry {
    public enum Reason {
        NULL_ELEMENT,
        NULL_DECLARING_CLASS,
        NULL_METHOD_NAME,
        UNREADABLE
    }

    public MalformedStackFrame {
        NormalizationValidation.nonNegative(originalIndex, "originalIndex");
        Objects.requireNonNull(reason, "reason");
    }
}
