package org.minecraftprot.stackframe.diagnostic;

/** Indicates a completed text value that violates the post-redaction safety boundary. */
public final class RedactionValidationException extends DiagnosticValidationException {
    public RedactionValidationException(String modelPath, String message) {
        super(modelPath, message);
    }
}
