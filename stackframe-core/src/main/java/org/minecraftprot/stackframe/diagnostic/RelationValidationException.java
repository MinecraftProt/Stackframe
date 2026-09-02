package org.minecraftprot.stackframe.diagnostic;

/** Indicates a non-tree relationship, shared diagnostic node, cycle, or excessive depth. */
public final class RelationValidationException extends DiagnosticValidationException {
    public RelationValidationException(String modelPath, String message) {
        super(modelPath, message);
    }
}
