package org.minecraftprot.stackframe.diagnostic;

import java.util.Optional;

/**
 * Bounded trace accounting. Frame and cause counts are exact non-negative
 * integers; a present total equals shown plus omitted.
 */
public record TraceSummary(
        TraceState state,
        Optional<Integer> totalFrames,
        int shownFrames,
        int omittedFrames,
        int omittedCauses,
        Optional<DisplayText> destination,
        Optional<DiagnosticId> recordId) {

    public TraceSummary {
        state = Validation.required(state, "$.trace.state");
        totalFrames = Validation.optional(totalFrames, "$.trace.totalFrames");
        shownFrames = nonNegativeCount(shownFrames, "$.trace.shownFrames");
        omittedFrames = nonNegativeCount(omittedFrames, "$.trace.omittedFrames");
        omittedCauses = nonNegativeCount(omittedCauses, "$.trace.omittedCauses");
        destination = Validation.optional(destination, "$.trace.destination");
        destination.ifPresent(value -> {
            if (value.value().isBlank()) {
                throw new TextValidationException("$.trace.destination", "must not be blank");
            }
        });
        recordId = Validation.optional(recordId, "$.trace.recordId");
        if (totalFrames.isPresent()) {
            var total = totalFrames.orElseThrow();
            nonNegativeCount(total, "$.trace.totalFrames");
            if ((long) shownFrames + omittedFrames != total) {
                throw new TraceValidationException(
                        "$.trace.totalFrames", "must equal shownFrames plus omittedFrames");
            }
        }

        if (state == TraceState.PRESERVED && destination.isEmpty() && recordId.isEmpty()) {
            throw new TraceValidationException(
                    "$.trace", "PRESERVED requires a destination or record ID");
        }
        if (state != TraceState.PRESERVED && (destination.isPresent() || recordId.isPresent())) {
            throw new TraceValidationException(
                    "$.trace", state + " must not claim a preserved destination or record ID");
        }
        if (state == TraceState.NOT_APPLICABLE
                && (totalFrames.orElse(0) != 0
                        || shownFrames != 0
                        || omittedFrames != 0
                        || omittedCauses != 0)) {
            throw new TraceValidationException(
                    "$.trace", "NOT_APPLICABLE cannot report originating trace counts");
        }
    }

    public static TraceSummary notApplicable() {
        return new TraceSummary(
                TraceState.NOT_APPLICABLE,
                Optional.empty(),
                0,
                0,
                0,
                Optional.empty(),
                Optional.empty());
    }

    private static int nonNegativeCount(int value, String path) {
        if (value < 0) {
            throw new TraceValidationException(path, "must be non-negative");
        }
        return value;
    }
}
