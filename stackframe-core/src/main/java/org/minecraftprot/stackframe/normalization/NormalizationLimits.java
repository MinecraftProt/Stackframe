package org.minecraftprot.stackframe.normalization;

import org.minecraftprot.stackframe.diagnostic.ModelLimits;

/**
 * Hard traversal and retention limits for one normalization operation after each
 * public {@link Throwable} accessor returns.
 *
 * <p>The defaults retain at most 256 throwable nodes, 64 levels, 256 frames per
 * node, 64 suppressed children per node, and 4,096 Unicode code points from each
 * text scalar. All limits are positive and text cannot exceed the diagnostic
 * candidate-text boundary. The JDK accessors materialize complete defensive frame
 * and suppressed arrays before a caller can inspect their lengths; that accessor
 * allocation cannot be preempted through the public Throwable API.
 */
public record NormalizationLimits(
        int maxNodes,
        int maxDepth,
        int maxFramesPerThrowable,
        int maxSuppressedPerThrowable,
        int maxTextCodePoints) {
    public static final NormalizationLimits DEFAULTS =
            new NormalizationLimits(256, 64, 256, 64, ModelLimits.TEXT_CODE_POINTS);

    public NormalizationLimits {
        NormalizationValidation.positive(maxNodes, "maxNodes");
        NormalizationValidation.positive(maxDepth, "maxDepth");
        NormalizationValidation.positive(maxFramesPerThrowable, "maxFramesPerThrowable");
        NormalizationValidation.positive(maxSuppressedPerThrowable, "maxSuppressedPerThrowable");
        NormalizationValidation.positive(maxTextCodePoints, "maxTextCodePoints");
        if (maxTextCodePoints > ModelLimits.TEXT_CODE_POINTS) {
            throw new IllegalArgumentException(
                    "maxTextCodePoints must not exceed " + ModelLimits.TEXT_CODE_POINTS);
        }
    }
}
