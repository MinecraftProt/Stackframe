package org.minecraftprot.stackframe.diagnostic;

/** Indicates unsafe, malformed, blank, or oversized model text. */
public final class TextValidationException extends DiagnosticValidationException {
    public TextValidationException(String modelPath, String message) {
        super(modelPath, message);
    }
}
