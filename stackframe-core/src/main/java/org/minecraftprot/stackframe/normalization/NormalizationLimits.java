package org.minecraftprot.stackframe.normalization;

import org.minecraftprot.stackframe.diagnostic.ModelLimits;

/**
 * Hard traversal and retention limits for one normalization operation after each
 * public {@link Throwable} accessor returns.
 *
 * <p>The defaults retain at most 256 throwable nodes, 64 levels, 256 frames per
 * node, 64 suppressed children per node, 4,096 Unicode code points per text
 * scalar, 4,096 frames overall, 65,536 candidate-text code points, 262,144
 * candidate-text UTF-8 bytes, and 262,144 deterministic scalar-work units.
 * All limits are positive and per-value text cannot exceed the diagnostic
 * candidate-text boundary.
 *
 * <p>One scalar-work unit is charged for each retained throwable accessor result,
 * inspected frame/suppressed array element, and source UTF-16 unit inspected while
 * copying text. The JDK accessors materialize complete defensive frame and
 * suppressed arrays before a caller can inspect their lengths; that accessor
 * allocation cannot be preempted through the public Throwable API.
 */
public record NormalizationLimits(
        int maxNodes,
        int maxDepth,
        int maxFramesPerThrowable,
        int maxSuppressedPerThrowable,
        int maxTextCodePoints,
        int maxTotalFrames,
        int maxTotalTextCodePoints,
        int maxTotalTextUtf8Bytes,
        int maxScalarWorkUnits) {
    private static final int DEFAULT_TOTAL_FRAMES = 4_096;
    private static final int DEFAULT_TOTAL_TEXT_CODE_POINTS = 65_536;
    private static final int DEFAULT_TOTAL_TEXT_UTF8_BYTES = 262_144;
    private static final int DEFAULT_SCALAR_WORK_UNITS = 262_144;

    public static final NormalizationLimits DEFAULTS = new NormalizationLimits(
            256,
            64,
            256,
            64,
            ModelLimits.TEXT_CODE_POINTS,
            DEFAULT_TOTAL_FRAMES,
            DEFAULT_TOTAL_TEXT_CODE_POINTS,
            DEFAULT_TOTAL_TEXT_UTF8_BYTES,
            DEFAULT_SCALAR_WORK_UNITS);

    public NormalizationLimits(
            int maxNodes,
            int maxDepth,
            int maxFramesPerThrowable,
            int maxSuppressedPerThrowable,
            int maxTextCodePoints) {
        this(
                maxNodes,
                maxDepth,
                maxFramesPerThrowable,
                maxSuppressedPerThrowable,
                maxTextCodePoints,
                DEFAULT_TOTAL_FRAMES,
                DEFAULT_TOTAL_TEXT_CODE_POINTS,
                DEFAULT_TOTAL_TEXT_UTF8_BYTES,
                DEFAULT_SCALAR_WORK_UNITS);
    }

    public NormalizationLimits {
        NormalizationValidation.positive(maxNodes, "maxNodes");
        NormalizationValidation.positive(maxDepth, "maxDepth");
        NormalizationValidation.positive(maxFramesPerThrowable, "maxFramesPerThrowable");
        NormalizationValidation.positive(maxSuppressedPerThrowable, "maxSuppressedPerThrowable");
        NormalizationValidation.positive(maxTextCodePoints, "maxTextCodePoints");
        NormalizationValidation.positive(maxTotalFrames, "maxTotalFrames");
        NormalizationValidation.positive(maxTotalTextCodePoints, "maxTotalTextCodePoints");
        NormalizationValidation.positive(maxTotalTextUtf8Bytes, "maxTotalTextUtf8Bytes");
        NormalizationValidation.positive(maxScalarWorkUnits, "maxScalarWorkUnits");
        if (maxScalarWorkUnits < 4) {
            throw new IllegalArgumentException(
                    "maxScalarWorkUnits must allow the four root throwable accessors");
        }
        if (maxTextCodePoints > ModelLimits.TEXT_CODE_POINTS) {
            throw new IllegalArgumentException(
                    "maxTextCodePoints must not exceed " + ModelLimits.TEXT_CODE_POINTS);
        }
    }
}
