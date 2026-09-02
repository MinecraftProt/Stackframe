package org.minecraftprot.stackframe.diagnostic;

/** Indicates invalid or inconsistent explicit omission accounting. */
public final class OmissionValidationException extends DiagnosticValidationException {
    public OmissionValidationException(String modelPath, String message) {
        super(modelPath, message);
    }
}
