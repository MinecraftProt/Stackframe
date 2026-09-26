package org.minecraftprot.stackframe.fabric.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.minecraftprot.stackframe.trace.TraceRecorder;

class ClientDiagnosticPipelineTest {
    @TempDir Path temporaryDirectory;

    @Test
    void startupBeforeUiAndRuntimeWithUiUseTheSameSafeLogPath() throws Exception {
        var output = new StringBuilder();
        var outputThread = new AtomicReference<String>();
        var traces = temporaryDirectory.resolve("traces");
        var startup = new IllegalStateException("private profile from early startup");
        var runtime = new IllegalArgumentException("private address from runtime");
        try (var pipeline = new ClientDiagnosticPipeline(
                new TraceRecorder(traces), rendered -> {
                    outputThread.set(Thread.currentThread().getName());
                    output.append(rendered);
                }, 2)) {
            assertTrue(pipeline.accept(startup, ClientFailurePhase.LOGGED));
            assertTrue(pipeline.accept(runtime, ClientFailurePhase.RESOURCE_RELOAD));
            pipeline.close();
            assertEquals(2, pipeline.stats().processed());
        }

        assertEquals(2, output.toString().split("SF0001", -1).length - 1);
        assertTrue(output.toString().contains("A client resource reload failed."));
        assertFalse(output.toString().contains("private profile"));
        assertFalse(output.toString().contains("private address"));
        assertEquals("stackframe-client-diagnostic-worker", outputThread.get());
        try (var files = Files.list(traces)) {
            var complete = files.filter(path -> path.toString().endsWith(".trace")).toList();
            assertEquals(2, complete.size());
            var raw = Files.readString(complete.getFirst(), StandardCharsets.UTF_8)
                    + Files.readString(complete.getLast(), StandardCharsets.UTF_8);
            assertTrue(raw.contains("private profile"));
            assertTrue(raw.contains("private address"));
        }
    }

    @Test
    void renderThreadNeverWaitsForTraceWritingOrUi() throws Exception {
        var enteredPrint = new CountDownLatch(1);
        var releasePrint = new CountDownLatch(1);
        var returned = new CountDownLatch(1);
        var accepted = new AtomicBoolean();
        var failure = new IllegalStateException("raw render failure") {
            @Override
            public void printStackTrace(PrintWriter writer) {
                enteredPrint.countDown();
                try {
                    if (!releasePrint.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("test timed out");
                    }
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
                super.printStackTrace(writer);
            }
        };
        try (var pipeline = new ClientDiagnosticPipeline(
                new TraceRecorder(temporaryDirectory.resolve("traces")), ignored -> { }, 1)) {
            var render = new Thread(() -> {
                accepted.set(pipeline.accept(failure, ClientFailurePhase.CRASH));
                returned.countDown();
            }, "render-thread-fixture");
            render.start();
            assertTrue(enteredPrint.await(3, TimeUnit.SECONDS));
            assertTrue(returned.await(1, TimeUnit.SECONDS), "render thread waited for the trace worker");
            assertTrue(accepted.get());
            releasePrint.countDown();
            render.join(1_000);
            pipeline.close();
            assertEquals(1, pipeline.stats().processed());
        } finally {
            releasePrint.countDown();
        }
    }

    @Test
    void failedFormattingAndTraceStorageLeaveOriginalThrowableUntouched() throws Exception {
        var blocked = temporaryDirectory.resolve("not-a-directory");
        Files.writeString(blocked, "occupied");
        var original = new IllegalStateException("private crash detail");
        var cause = new IllegalArgumentException("original cause");
        original.initCause(cause);
        try (var pipeline = new ClientDiagnosticPipeline(
                new TraceRecorder(blocked), rendered -> {
                    throw new IllegalStateException("output unavailable");
                }, 1)) {
            assertTrue(pipeline.accept(original, ClientFailurePhase.CRASH));
            pipeline.close();
            assertEquals(1, pipeline.stats().processingFailures());
            assertEquals(1, pipeline.stats().accepted());
        }
        assertSame(cause, original.getCause());
        assertEquals("private crash detail", original.getMessage());
    }

    @Test
    void crashReportAndCrashRouteAdmitTheSameThrowableOnlyOnce() {
        var output = new StringBuilder();
        var failure = new IllegalStateException("private crash detail");
        try (var pipeline = new ClientDiagnosticPipeline(
                new TraceRecorder(temporaryDirectory.resolve("traces")),
                output::append, 2)) {
            assertTrue(pipeline.accept(failure, ClientFailurePhase.CRASH_REPORT));
            assertFalse(pipeline.accept(failure, ClientFailurePhase.CRASH));
            pipeline.close();
            assertEquals(1, pipeline.stats().accepted());
            assertEquals(1, pipeline.stats().duplicates());
            assertEquals(1, pipeline.stats().processed());
        }
        assertEquals(1, output.toString().split("SF0001", -1).length - 1);
        assertFalse(output.toString().contains("private crash detail"));
    }

    @Test
    void overloadDropsOnlySupplementalObservations() throws Exception {
        var enteredPrint = new CountDownLatch(1);
        var releasePrint = new CountDownLatch(1);
        var first = new IllegalStateException("first") {
            @Override
            public void printStackTrace(PrintWriter writer) {
                enteredPrint.countDown();
                try {
                    releasePrint.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
                super.printStackTrace(writer);
            }
        };
        try (var pipeline = new ClientDiagnosticPipeline(
                new TraceRecorder(temporaryDirectory.resolve("traces")), ignored -> { }, 1)) {
            assertTrue(pipeline.accept(first, ClientFailurePhase.LOGGED));
            assertTrue(enteredPrint.await(3, TimeUnit.SECONDS));
            assertTrue(pipeline.accept(new IllegalStateException("queued"), ClientFailurePhase.CONNECTION));
            assertFalse(pipeline.accept(new IllegalStateException("dropped"), ClientFailurePhase.CRASH));
            assertEquals(1, pipeline.stats().dropped());
            releasePrint.countDown();
            pipeline.close();
            assertEquals(2, pipeline.stats().processed());
        } finally {
            releasePrint.countDown();
        }
    }
}
