package org.minecraftprot.stackframe.normalization;

import java.util.Objects;
import java.util.Optional;

/** Explicit non-node outcome for one cause or suppressed edge. */
public record ThrowableGraphMarker(
        Kind kind, Optional<Integer> referencedNodeId, long omittedDirectNodes)
        implements NormalizedThrowableElement {
    public enum Kind {
        CYCLE_REFERENCE,
        SHARED_REFERENCE,
        DEPTH_LIMIT,
        NODE_LIMIT,
        UNREADABLE_CAUSE,
        MALFORMED_SUPPRESSED
    }

    public ThrowableGraphMarker {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(referencedNodeId, "referencedNodeId");
        NormalizationValidation.nonNegative(omittedDirectNodes, "omittedDirectNodes");

        var reference = kind == Kind.CYCLE_REFERENCE || kind == Kind.SHARED_REFERENCE;
        var truncation = kind == Kind.DEPTH_LIMIT || kind == Kind.NODE_LIMIT;
        if (reference != referencedNodeId.isPresent()) {
            throw new IllegalArgumentException(
                    "referencedNodeId is required exactly for reference markers");
        }
        if (referencedNodeId.isPresent() && referencedNodeId.orElseThrow() < 0) {
            throw new IllegalArgumentException("referencedNodeId must be non-negative");
        }
        if (truncation != (omittedDirectNodes == 1)) {
            throw new IllegalArgumentException(
                    "truncation markers must omit exactly one direct node");
        }
        if (!truncation && omittedDirectNodes != 0) {
            throw new IllegalArgumentException(
                    "non-truncation markers cannot report omitted nodes");
        }
    }

    public static ThrowableGraphMarker reference(Kind kind, int referencedNodeId) {
        return new ThrowableGraphMarker(kind, Optional.of(referencedNodeId), 0);
    }

    public static ThrowableGraphMarker truncation(Kind kind) {
        return new ThrowableGraphMarker(kind, Optional.empty(), 1);
    }

    public static ThrowableGraphMarker unreadableCause() {
        return new ThrowableGraphMarker(Kind.UNREADABLE_CAUSE, Optional.empty(), 0);
    }

    public static ThrowableGraphMarker malformedSuppressed() {
        return new ThrowableGraphMarker(Kind.MALFORMED_SUPPRESSED, Optional.empty(), 0);
    }
}
