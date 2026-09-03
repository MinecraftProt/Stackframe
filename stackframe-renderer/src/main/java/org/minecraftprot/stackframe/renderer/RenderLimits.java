package org.minecraftprot.stackframe.renderer;

/** Independent output limits applied while rendering an already bounded document. */
public record RenderLimits(long maxUtf8Bytes, int maxLines, long maxWorkUnits) {
    public static final RenderLimits DEFAULT =
            new RenderLimits(16L * 1024 * 1024, 262_144, 32L * 1024 * 1024);

    public RenderLimits {
        if (maxUtf8Bytes <= 0 || maxLines <= 0 || maxWorkUnits <= 0) {
            throw new IllegalArgumentException("render limits must be positive");
        }
    }
}
