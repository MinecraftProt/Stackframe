package org.minecraftprot.stackframe.trace;

import java.nio.file.Path;
import java.util.Optional;
import org.minecraftprot.stackframe.diagnostic.BoundedList;
import org.minecraftprot.stackframe.diagnostic.CorrelationId;
import org.minecraftprot.stackframe.diagnostic.DiagnosticId;
import org.minecraftprot.stackframe.diagnostic.DisplayText;
import org.minecraftprot.stackframe.diagnostic.Note;
import org.minecraftprot.stackframe.diagnostic.NoteKind;
import org.minecraftprot.stackframe.diagnostic.TextOrigin;
import org.minecraftprot.stackframe.diagnostic.TraceState;
import org.minecraftprot.stackframe.diagnostic.TraceSummary;

/** Result of one attempt to preserve an original throwable. No source object is retained. */
public record TraceRecord(
        CorrelationId correlationId,
        DiagnosticId recordId,
        TraceState state,
        Optional<Path> file,
        Optional<TraceWriteFailure> failure) {

    public TraceRecord {
        if (correlationId == null || recordId == null || state == null || file == null
                || failure == null) {
            throw new IllegalArgumentException("trace record fields must not be null");
        }
        if (!correlationId.value().equals(recordId.value())) {
            throw new IllegalArgumentException("trace record and correlation IDs must match");
        }
        if (state == TraceState.PRESERVED && (file.isEmpty() || failure.isPresent())) {
            throw new IllegalArgumentException("preserved traces require a file and no failure");
        }
        if (state == TraceState.WRITE_FAILED && (file.isPresent() || failure.isEmpty())) {
            throw new IllegalArgumentException("failed traces require a failure and no published file");
        }
        if (state == TraceState.NOT_APPLICABLE) {
            throw new IllegalArgumentException("a trace record must be attempted");
        }
    }

    /** Attach to a diagnostic after selecting exact operator-facing frame counts. */
    public TraceSummary summary(
            Optional<Integer> totalFrames,
            int shownFrames,
            int omittedFrames,
            int omittedCauses) {
        return new TraceSummary(
                state,
                totalFrames,
                shownFrames,
                omittedFrames,
                omittedCauses,
                Optional.empty(),
                state == TraceState.PRESERVED ? Optional.of(recordId) : Optional.empty());
    }

    /** Required node-local explanation when {@link #state()} is WRITE_FAILED. */
    public Note failureNote() {
        if (state != TraceState.WRITE_FAILED) {
            throw new IllegalStateException("the trace was preserved");
        }
        return new Note(
                NoteKind.NOTE,
                DisplayText.visible(
                        "Stackframe could not save the full trace; inspect the original server error log",
                        TextOrigin.GENERATED),
                BoundedList.empty());
    }
}
