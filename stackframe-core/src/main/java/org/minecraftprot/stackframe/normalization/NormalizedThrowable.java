package org.minecraftprot.stackframe.normalization;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One retained throwable node copied into immutable, loader-neutral scalar data.
 *
 * <p>Class name and graph relationships are neutral metadata. This type deliberately
 * makes no wrapper, blame, classifier, diagnostic-code, or rendering decision.
 */
public record NormalizedThrowable(
        int id,
        NormalizedText className,
        NormalizedMessage message,
        StackTraceState stackTraceState,
        List<NormalizedFrameEntry> stackFrames,
        long omittedFrameCount,
        Optional<FrameTruncationReason> frameTruncationReason,
        Optional<NormalizedThrowableElement> cause,
        SuppressedState suppressedState,
        List<NormalizedThrowableElement> suppressed,
        long omittedSuppressedCount,
        Optional<SuppressedTruncationReason> suppressedTruncationReason)
        implements NormalizedThrowableElement {
    public enum FrameTruncationReason {
        PER_THROWABLE_LIMIT,
        TOTAL_FRAME_LIMIT,
        SCALAR_WORK_LIMIT
    }

    public enum StackTraceState {
        PRESENT,
        EMPTY,
        NULL_ARRAY,
        UNREADABLE
    }

    public enum SuppressedState {
        PRESENT,
        EMPTY,
        NULL_ARRAY,
        UNREADABLE
    }

    public enum SuppressedTruncationReason {
        PER_THROWABLE_LIMIT,
        SCALAR_WORK_LIMIT
    }

    public NormalizedThrowable {
        NormalizationValidation.nonNegative(id, "id");
        Objects.requireNonNull(className, "className");
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(stackTraceState, "stackTraceState");
        stackFrames = NormalizationValidation.immutableList(stackFrames, "stackFrames");
        NormalizationValidation.nonNegative(omittedFrameCount, "omittedFrameCount");
        Objects.requireNonNull(frameTruncationReason, "frameTruncationReason");
        Objects.requireNonNull(cause, "cause");
        Objects.requireNonNull(suppressedState, "suppressedState");
        suppressed = NormalizationValidation.immutableList(suppressed, "suppressed");
        NormalizationValidation.nonNegative(omittedSuppressedCount, "omittedSuppressedCount");
        Objects.requireNonNull(suppressedTruncationReason, "suppressedTruncationReason");

        if ((stackTraceState == StackTraceState.PRESENT)
                != (!stackFrames.isEmpty() || omittedFrameCount > 0)) {
            throw new IllegalArgumentException(
                    "present stack traces must retain or explicitly omit at least one frame");
        }
        if ((omittedFrameCount > 0) != frameTruncationReason.isPresent()) {
            throw new IllegalArgumentException(
                    "frameTruncationReason is required exactly when frames were omitted");
        }
        if ((suppressedState == SuppressedState.PRESENT)
                != (!suppressed.isEmpty() || omittedSuppressedCount > 0)) {
            throw new IllegalArgumentException(
                    "present suppressed arrays must retain or explicitly omit at least one child");
        }
        if ((omittedSuppressedCount > 0) != suppressedTruncationReason.isPresent()) {
            throw new IllegalArgumentException(
                    "suppressedTruncationReason is required exactly when children were omitted");
        }
    }
}
