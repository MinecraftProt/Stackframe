package org.minecraftprot.stackframe.renderer;

import java.time.Duration;

/** Independent output limits applied while rendering an already bounded document. */
public record RenderLimits(long maxUtf8Bytes, int maxLines, long maxWorkUnits,
        Duration maxElapsed) {
    public static final RenderLimits DEFAULT =
            new RenderLimits(16L * 1024 * 1024, 262_144, 32L * 1024 * 1024,
                    Duration.ofSeconds(30));

    public RenderLimits(long maxUtf8Bytes, int maxLines, long maxWorkUnits) {
        this(maxUtf8Bytes, maxLines, maxWorkUnits, DEFAULT.maxElapsed());
    }

    public RenderLimits {
        if (maxUtf8Bytes <= 0 || maxLines <= 0 || maxWorkUnits <= 0) {
            throw new IllegalArgumentException("render limits must be positive");
        }
        if (maxElapsed == null || maxElapsed.isZero() || maxElapsed.isNegative()
                || maxElapsed.compareTo(Duration.ofMinutes(5)) > 0) {
            throw new IllegalArgumentException("render time limit must be between 1 ns and 5 min");
        }
    }
}
