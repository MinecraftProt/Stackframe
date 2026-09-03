package org.minecraftprot.stackframe.diagnostic;

/** Indicates a malformed or unsupported diagnostic schema version. */
public final class SchemaValidationException extends DiagnosticValidationException {
    public SchemaValidationException(String modelPath, String message) {
        super(modelPath, message);
    }
}
