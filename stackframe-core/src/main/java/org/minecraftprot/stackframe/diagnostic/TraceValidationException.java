package org.minecraftprot.stackframe.diagnostic;

/** Indicates an inconsistent trace preservation state or frame accounting. */
public final class TraceValidationException extends DiagnosticValidationException {
    public TraceValidationException(String modelPath, String message) {
        super(modelPath, message);
    }
}
