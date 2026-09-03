package org.minecraftprot.stackframe.diagnostic.registry;

/** Deterministic failure for an invalid registry definition or governed artifact. */
public final class RegistryValidationException extends IllegalArgumentException {
    public RegistryValidationException(String message) {
        super(message);
    }
}
