package org.minecraftprot.stackframe.trace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.minecraftprot.stackframe.diagnostic.BoundedList;
import org.minecraftprot.stackframe.diagnostic.CatalogText;
import org.minecraftprot.stackframe.diagnostic.ConfidenceReference;
import org.minecraftprot.stackframe.diagnostic.Diagnostic;
import org.minecraftprot.stackframe.diagnostic.DiagnosticCode;
import org.minecraftprot.stackframe.diagnostic.DiagnosticDocument;
import org.minecraftprot.stackframe.diagnostic.DiagnosticId;
import org.minecraftprot.stackframe.diagnostic.SchemaVersion;
import org.minecraftprot.stackframe.diagnostic.Severity;
import org.minecraftprot.stackframe.diagnostic.TraceState;
import org.minecraftprot.stackframe.diagnostic.TraceValidationException;

class TraceRecorderTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void preservesTheOriginalCauseSuppressedExceptionsAndEveryFrame() throws IOException {
        var cause = new IllegalArgumentException("underlying failure");
        cause.setStackTrace(new StackTraceElement[] {
            new StackTraceElement("example.Cause", "load", "Cause.java", 31)
        });
        var failure = new IllegalStateException("outer failure", cause);
        failure.addSuppressed(new IOException("secondary failure"));
        failure.setStackTrace(new StackTraceElement[] {
            new StackTraceElement("example.Server", "start", "Server.java", 7)
        });

        var record = new TraceRecorder(temporaryDirectory.resolve("traces")).record(failure);

        assertEquals(TraceState.PRESERVED, record.state());
        assertEquals(record.correlationId().value(), record.recordId().value());
        assertEquals(16, record.correlationId().value().length());
        var content = Files.readString(record.file().orElseThrow());
        assertTrue(content.contains("outer failure"));
        assertTrue(content.contains("underlying failure"));
        assertTrue(content.contains("secondary failure"));
        assertTrue(content.contains("example.Server.start(Server.java:7)"));
        assertTrue(content.contains("example.Cause.load(Cause.java:31)"));

        var trace = record.summary(Optional.of(2), 0, 2, 1);
        assertEquals(record.recordId(), trace.recordId().orElseThrow());
        assertEquals(2, trace.omittedFrames());
        assertEquals(TraceState.PRESERVED, trace.state());
        assertThrows(IllegalStateException.class, record::failureNote);
    }

    @Test
    void failedWritesCanBeReportedWithoutClaimingCompleteDetails() throws IOException {
        var blockedDirectory = temporaryDirectory.resolve("not-a-directory");
        Files.writeString(blockedDirectory, "existing file");
        var failure = new IllegalStateException("original failure");

        var record = new TraceRecorder(blockedDirectory).record(failure);

        assertEquals(TraceState.WRITE_FAILED, record.state());
        assertEquals(Optional.of(TraceWriteFailure.STORAGE_UNAVAILABLE), record.failure());
        assertTrue(record.file().isEmpty());
        var trace = record.summary(Optional.empty(), 0, 1, 0);
        assertTrue(trace.recordId().isEmpty());
        var diagnostic = diagnostic(record, BoundedList.of(List.of(record.failureNote())));
        assertEquals(TraceState.WRITE_FAILED, diagnostic.root().trace().state());
        assertEquals("existing file", Files.readString(blockedDirectory));
    }

    @Test
    void throwablePrinterFailureLeavesNoPublishedTrace() throws IOException {
        var directory = temporaryDirectory.resolve("traces");
        var failure = new IllegalStateException("original failure") {
            @Override
            public void printStackTrace(PrintWriter writer) {
                throw new IllegalStateException("printer failed");
            }
        };

        var record = new TraceRecorder(directory).record(failure);

        assertEquals(TraceState.WRITE_FAILED, record.state());
        assertEquals(Optional.of(TraceWriteFailure.TRACE_UNREADABLE), record.failure());
        try (var files = Files.list(directory)) {
            assertEquals(List.of(), files.toList());
        }
    }

    @Test
    void concurrentFailuresGetIndependentCompleteRecords() throws Exception {
        var recorder = new TraceRecorder(temporaryDirectory.resolve("traces"));
        var records = new ArrayList<TraceRecord>();
        try (var executor = Executors.newFixedThreadPool(8)) {
            var pending = new ArrayList<java.util.concurrent.Future<TraceRecord>>();
            for (var index = 0; index < 40; index++) {
                var event = index;
                pending.add(executor.submit(() -> recorder.record(
                        new IllegalStateException("failure " + event))));
            }
            for (var future : pending) {
                records.add(future.get());
            }
        }
        var ids = new HashSet<String>();
        for (var index = 0; index < records.size(); index++) {
            var record = records.get(index);
            assertEquals(TraceState.PRESERVED, record.state());
            assertTrue(ids.add(record.correlationId().value()));
            assertTrue(Files.readString(record.file().orElseThrow())
                    .contains("failure " + index));
        }
        assertEquals(40, ids.size());
        assertNotEquals(records.getFirst().file(), records.getLast().file());
    }

    @Test
    void callerSelectedIdMatchesThePublishedTraceAndCannotOverwriteIt() throws IOException {
        var recorder = new TraceRecorder(temporaryDirectory.resolve("traces"));
        var id = recorder.newCorrelationId();
        var first = recorder.record(new IllegalStateException("first failure"), id);

        assertEquals(TraceState.PRESERVED, first.state());
        assertEquals(id, first.correlationId());
        assertEquals(id.value() + ".trace", first.file().orElseThrow().getFileName().toString());
        var collision = recorder.record(new IllegalStateException("second failure"), id);
        assertEquals(TraceState.WRITE_FAILED, collision.state());
        assertEquals(id, collision.correlationId());
        assertEquals(Optional.of(TraceWriteFailure.IDENTIFIER_EXHAUSTED), collision.failure());
        assertTrue(Files.readString(first.file().orElseThrow()).contains("first failure"));
        assertFalse(Files.readString(first.file().orElseThrow()).contains("second failure"));
        try (var files = Files.list(recorder.directory())) {
            assertEquals(1, files.count());
        }
    }

    @Test
    void linkedTraceAncestorCannotRedirectRawRecordsOutsideTheChosenPath() throws IOException {
        var outside = Files.createDirectory(temporaryDirectory.resolve("outside"));
        var link = temporaryDirectory.resolve("linked");
        try {
            Files.createSymbolicLink(link, outside);
        } catch (IOException | UnsupportedOperationException | SecurityException unavailable) {
            return;
        }

        var record = new TraceRecorder(link.resolve("traces"))
                .record(new IllegalStateException("private detail"));

        assertEquals(TraceState.WRITE_FAILED, record.state());
        assertFalse(Files.exists(outside.resolve("traces")));
    }

    @Test
    void theTraceSummaryCannotClaimAnIncorrectFrameCount() {
        var record = new TraceRecorder(temporaryDirectory.resolve("traces"))
                .record(new IllegalStateException("failure"));

        assertEquals(TraceState.PRESERVED, record.state());
        assertThrows(TraceValidationException.class,
                () -> record.summary(Optional.of(1), 0, 2, 0));
        assertFalse(diagnostic(record, BoundedList.empty()).root().trace().recordId().isEmpty());
    }

    private static DiagnosticDocument diagnostic(
            TraceRecord record,
            BoundedList<org.minecraftprot.stackframe.diagnostic.Note> notes) {
        var root = new Diagnostic(
                Severity.ERROR,
                new DiagnosticCode("SF0001"),
                new CatalogText("generic.failure", "an unexpected operation failed"),
                BoundedList.empty(),
                notes,
                BoundedList.empty(),
                record.summary(Optional.empty(), 0, 0, 0),
                BoundedList.empty(),
                ConfidenceReference.unassessed(),
                BoundedList.empty(),
                BoundedList.empty());
        return new DiagnosticDocument(
                SchemaVersion.CURRENT,
                new DiagnosticId("D" + record.correlationId().value()),
                record.correlationId(),
                root,
                BoundedList.empty(),
                BoundedList.empty());
    }
}
