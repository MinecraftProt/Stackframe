package org.minecraftprot.stackframe.diagnostic;

/** Indicates an invalid one-based, end-exclusive source or excerpt range. */
public final class RangeValidationException extends DiagnosticValidationException {
    public RangeValidationException(String modelPath, String message) {
        super(modelPath, message);
    }
}
