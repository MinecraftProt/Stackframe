package org.minecraftprot.stackframe.diagnostic;

/** Indicates that a machine-facing identifier does not satisfy its ASCII grammar. */
public final class IdentifierValidationException extends DiagnosticValidationException {
    public IdentifierValidationException(String modelPath, String message) {
        super(modelPath, message);
    }
}
