package org.minecraftprot.stackframe.diagnostic;

/**
 * Base class for deterministic caller-contract failures while constructing the
 * completed diagnostic model.
 */
public class DiagnosticValidationException extends IllegalArgumentException {
    private final String modelPath;

    public DiagnosticValidationException(String modelPath, String message) {
        super(modelPath + ": " + message);
        this.modelPath = modelPath;
    }

    public final String modelPath() {
        return modelPath;
    }
}
