package org.minecraftprot.stackframe.diagnostic;

/** Indicates a duplicate local identifier or a reference that does not resolve in its node. */
public final class ReferenceValidationException extends DiagnosticValidationException {
    public ReferenceValidationException(String modelPath, String message) {
        super(modelPath, message);
    }
}
