package org.minecraftprot.stackframe.renderer;

/** Explicit, environment-independent rendering choices. */
public record RenderOptions(
        OutputMode outputMode,
        RenderWidth width,
        AmbiguousWidth ambiguousWidth,
        RenderLimits limits) {

    public RenderOptions {
        if (outputMode == null || width == null || ambiguousWidth == null || limits == null) {
            throw new IllegalArgumentException("render options must not contain null");
        }
    }

    public static RenderOptions plain(RenderWidth width) {
        return new RenderOptions(
                OutputMode.PLAIN, width, AmbiguousWidth.NARROW, RenderLimits.DEFAULT);
    }

    public static RenderOptions ansi(RenderWidth width) {
        return new RenderOptions(
                OutputMode.ANSI, width, AmbiguousWidth.NARROW, RenderLimits.DEFAULT);
    }
}
