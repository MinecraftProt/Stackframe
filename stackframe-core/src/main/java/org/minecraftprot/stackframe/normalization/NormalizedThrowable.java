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
        Optional<NormalizedThrowableElement> cause,
        SuppressedState suppressedState,
        List<NormalizedThrowableElement> suppressed,
        long omittedSuppressedCount)
        implements NormalizedThrowableElement {
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

    public NormalizedThrowable {
        NormalizationValidation.nonNegative(id, "id");
        Objects.requireNonNull(className, "className");
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(stackTraceState, "stackTraceState");
        stackFrames = NormalizationValidation.immutableList(stackFrames, "stackFrames");
        NormalizationValidation.nonNegative(omittedFrameCount, "omittedFrameCount");
        Objects.requireNonNull(cause, "cause");
        Objects.requireNonNull(suppressedState, "suppressedState");
        suppressed = NormalizationValidation.immutableList(suppressed, "suppressed");
        NormalizationValidation.nonNegative(omittedSuppressedCount, "omittedSuppressedCount");

        if ((stackTraceState == StackTraceState.PRESENT) != !stackFrames.isEmpty()) {
            throw new IllegalArgumentException(
                    "stackFrames must be non-empty exactly when stackTraceState is PRESENT");
        }
        if ((suppressedState == SuppressedState.PRESENT) != !suppressed.isEmpty()) {
            throw new IllegalArgumentException(
                    "suppressed must be non-empty exactly when suppressedState is PRESENT");
        }
    }
}
