package org.minecraftprot.stackframe.renderer;

/** Rendering stopped because a configured output or work bound was reached. */
public final class RenderLimitException extends RuntimeException {
    public RenderLimitException(String message) {
        super(message);
    }
}
