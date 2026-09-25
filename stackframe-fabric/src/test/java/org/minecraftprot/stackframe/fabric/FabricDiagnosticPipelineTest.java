package org.minecraftprot.stackframe.fabric;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.minecraftprot.stackframe.correlation.DiagnosticCorrelator.Config;
import org.minecraftprot.stackframe.correlation.DiagnosticCorrelator.Importance;
import org.minecraftprot.stackframe.trace.TraceRecorder;

class FabricDiagnosticPipelineTest {
    @TempDir Path temp;

    @Test
    void shutdownDrainsEarlyAndRuntimeFailuresWithFullTraces() throws Exception {
        var output = new ByteArrayOutputStream();
        var traces = temp.resolve("traces");
        var pipeline = new FabricDiagnosticPipeline(
                new TraceRecorder(traces), new PrintStream(output, true, StandardCharsets.UTF_8), 2);
        pipeline.accept(new IllegalStateException("startup details"));
        pipeline.accept(new IllegalArgumentException("runtime details"));
        pipeline.close();

        assertEquals(2, pipeline.stats().accepted());
        assertEquals(2, pipeline.stats().processed());
        assertEquals(0, pipeline.stats().dropped());
        var rendered = output.toString(StandardCharsets.UTF_8);
        assertEquals(2, rendered.split("SF0001", -1).length - 1);
        assertFalse(rendered.contains("could not save the full trace"), rendered);
        assertFalse(rendered.contains("startup details"));
        assertFalse(rendered.contains("runtime details"));
        try (var files = Files.list(traces)) {
            var complete = files.filter(path -> path.toString().endsWith(".trace")).toList();
            assertEquals(2, complete.size(), rendered);
            assertTrue(complete.stream().anyMatch(path -> contains(path, "startup details")));
            assertTrue(complete.stream().anyMatch(path -> contains(path, "runtime details")));
        }
    }

    @Test
    void unavailableTraceStorageIsExplicitInSafeOutput() {
        var output = new ByteArrayOutputStream();
        var blocked = temp.resolve("blocked");
        try {
            Files.writeString(blocked, "file, not a directory");
        } catch (java.io.IOException failure) {
            throw new AssertionError(failure);
        }
        var pipeline = new FabricDiagnosticPipeline(
                new TraceRecorder(blocked), new PrintStream(output, true, StandardCharsets.UTF_8), 1);
        pipeline.accept(new IllegalStateException("original detail"));
        pipeline.close();

        assertEquals(1, pipeline.stats().processed());
        assertTrue(output.toString(StandardCharsets.UTF_8)
                .contains("could not save the full trace"));
    }

    @Test
    void outputFailureStaysOnWorkerAndDoesNotPreventTracePreservation() throws Exception {
        var traces = temp.resolve("traces");
        var pipeline = new FabricDiagnosticPipeline(
                new TraceRecorder(traces),
                rendered -> { throw new IllegalStateException("output unavailable"); },
                1);
        pipeline.accept(new IllegalStateException("original detail"));
        pipeline.close();

        assertEquals(1, pipeline.stats().accepted());
        assertEquals(1, pipeline.stats().processingFailures());
        try (var files = Files.list(traces)) {
            assertEquals(1, files.filter(path -> path.toString().endsWith(".trace")).count());
        }
    }

    @Test
    void shutdownDoesNotInterruptAnAcceptedTraceWrite() throws Exception {
        var enteredPrinter = new CountDownLatch(1);
        var releasePrinter = new CountDownLatch(1);
        var interrupted = new AtomicBoolean();
        var output = new ByteArrayOutputStream();
        var traces = temp.resolve("shutdown-traces");
        var failure = new IllegalStateException("original detail") {
            @Override
            public void printStackTrace(PrintWriter writer) {
                enteredPrinter.countDown();
                try {
                    releasePrinter.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException unexpected) {
                    interrupted.set(true);
                }
                super.printStackTrace(writer);
            }
        };
        var pipeline = new FabricDiagnosticPipeline(
                new TraceRecorder(traces), rendered -> write(output, rendered), 1);
        pipeline.accept(failure);
        assertTrue(enteredPrinter.await(2, TimeUnit.SECONDS));
        var closer = Thread.ofPlatform().start(pipeline::close);
        try {
            assertTrue(waitForJoin(closer), "close did not begin waiting for the worker");
        } finally {
            releasePrinter.countDown();
        }
        closer.join(3_000);

        assertFalse(closer.isAlive());
        assertFalse(interrupted.get());
        assertEquals(1, pipeline.stats().processed());
        assertFalse(output.toString(StandardCharsets.UTF_8)
                .contains("could not save the full trace"));
        try (var files = Files.list(traces)) {
            assertEquals(1, files.filter(path -> path.toString().endsWith(".trace")).count());
        }
    }

    @Test
    void repeatedObservationsProduceOneDiagnosticOneTraceAndExactSummary() throws Exception {
        var output = new ByteArrayOutputStream();
        var traces = temp.resolve("repeated-traces");
        var pipeline = new FabricDiagnosticPipeline(
                new TraceRecorder(traces),
                rendered -> write(output, rendered), 4);
        var failure = new IllegalStateException("private repeated detail");
        pipeline.accept(failure);
        pipeline.accept(failure);
        pipeline.accept(failure);
        pipeline.close();

        var rendered = output.toString(StandardCharsets.UTF_8);
        assertEquals(1, occurrences(rendered, "SF0001"));
        assertEquals(1, occurrences(rendered, "was observed 2 more times"));
        assertFalse(rendered.contains("private repeated detail"));
        assertEquals(3, pipeline.stats().processed());
        assertEquals(2, pipeline.stats().suppressed());
        assertEquals(1, pipeline.stats().repeatSummaries());
        try (var files = Files.list(traces)) {
            var published = files.filter(path -> path.toString().endsWith(".trace")).toList();
            assertEquals(1, published.size());
            var id = published.getFirst().getFileName().toString().replace(".trace", "");
            assertTrue(occurrences(rendered, id) >= 2);
        }
    }

    @Test
    void distinctErrorsWithTheSameMessageRemainSeparate() throws Exception {
        var output = new ByteArrayOutputStream();
        var traces = temp.resolve("distinct-traces");
        var pipeline = new FabricDiagnosticPipeline(
                new TraceRecorder(traces),
                rendered -> write(output, rendered), 2);
        pipeline.accept(new IllegalStateException("identical text"));
        pipeline.accept(new IllegalStateException("identical text"));
        pipeline.close();

        assertEquals(2, occurrences(output.toString(StandardCharsets.UTF_8), "SF0001"));
        assertEquals(0, pipeline.stats().suppressed());
        try (var files = Files.list(traces)) {
            assertEquals(2, files.filter(path -> path.toString().endsWith(".trace")).count());
        }
    }

    @Test
    void criticalObservationIsNeverSuppressedAndClosesPendingSummary() throws Exception {
        var output = new ByteArrayOutputStream();
        var traces = temp.resolve("critical-traces");
        var pipeline = new FabricDiagnosticPipeline(
                new TraceRecorder(traces),
                rendered -> write(output, rendered), 3);
        var failure = new IllegalStateException("fatal detail");
        pipeline.accept(failure);
        pipeline.accept(failure);
        pipeline.accept(failure, Importance.CRITICAL);
        pipeline.close();

        var rendered = output.toString(StandardCharsets.UTF_8);
        assertEquals(2, occurrences(rendered, "SF0001"));
        assertEquals(1, occurrences(rendered, "was observed 1 more time"));
        assertEquals(1, pipeline.stats().suppressed());
        try (var files = Files.list(traces)) {
            assertEquals(2, files.filter(path -> path.toString().endsWith(".trace")).count());
        }
    }

    @Test
    void disabledDeduplicationEmitsEveryObservation() throws Exception {
        var output = new ByteArrayOutputStream();
        var traces = temp.resolve("disabled-traces");
        var pipeline = new FabricDiagnosticPipeline(
                new TraceRecorder(traces),
                rendered -> write(output, rendered), 2, Config.disabled());
        var failure = new IllegalStateException();
        pipeline.accept(failure);
        pipeline.accept(failure);
        pipeline.close();

        assertEquals(2, occurrences(output.toString(StandardCharsets.UTF_8), "SF0001"));
        assertEquals(0, pipeline.stats().suppressed());
        assertEquals(0, pipeline.stats().repeatSummaries());
        try (var files = Files.list(traces)) {
            assertEquals(2, files.filter(path -> path.toString().endsWith(".trace")).count());
        }
    }

    private static int occurrences(String text, String fragment) {
        return text.split(java.util.regex.Pattern.quote(fragment), -1).length - 1;
    }

    private static boolean waitForJoin(Thread thread) {
        var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        while (System.nanoTime() < deadline) {
            if (thread.getState() == Thread.State.TIMED_WAITING) {
                return true;
            }
            Thread.onSpinWait();
        }
        return false;
    }

    private static void write(ByteArrayOutputStream output, String rendered) {
        try {
            output.write(rendered.getBytes(StandardCharsets.UTF_8));
        } catch (java.io.IOException failure) {
            throw new AssertionError(failure);
        }
    }

    private static boolean contains(Path path, String text) {
        try {
            return Files.readString(path).contains(text);
        } catch (java.io.IOException failure) {
            throw new AssertionError(failure);
        }
    }
}
