package org.minecraftprot.stackframe.diagnostic;

/** Indicates that a schema 1.0 count, depth, text, or UTF-8 byte limit was exceeded. */
public final class LimitValidationException extends DiagnosticValidationException {
    public LimitValidationException(String modelPath, String message) {
        super(modelPath, message);
    }
}
