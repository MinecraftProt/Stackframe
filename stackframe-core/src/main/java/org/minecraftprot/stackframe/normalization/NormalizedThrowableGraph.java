package org.minecraftprot.stackframe.normalization;

import java.util.Objects;

/**
 * Result of one normalization pass.
 *
 * <p>This graph contains candidate text and is not safe for rendering or durable
 * retention. Consumers must complete classification/redaction promptly and release
 * this value. It never retains the source throwable graph.
 */
public record NormalizedThrowableGraph(
        NormalizedThrowable root,
        NormalizationLimits limits,
        NormalizationStatistics statistics) {
    public NormalizedThrowableGraph {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(limits, "limits");
        Objects.requireNonNull(statistics, "statistics");
        NormalizedGraphValidator.validate(root, limits, statistics);
    }
}
