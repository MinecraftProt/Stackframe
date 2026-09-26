package org.minecraftprot.stackframe.fabric;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.minecraftprot.stackframe.correlation.DiagnosticCorrelator.Config;
import org.minecraftprot.stackframe.correlation.DiagnosticCorrelator.Importance;
import org.minecraftprot.stackframe.fabric.config.ConfigurationFile;
import org.minecraftprot.stackframe.fabric.config.StackframeConfiguration;
import org.minecraftprot.stackframe.trace.TraceRecorder;
import org.minecraftprot.stackframe.trace.TraceRetention;

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
    void earlyCloseAndJvmFallbackClosePublishOnlyOneRepeatSummary() {
        var output = new ByteArrayOutputStream();
        var pipeline = new FabricDiagnosticPipeline(
                new TraceRecorder(temp.resolve("early-stop-traces")),
                rendered -> write(output, rendered), 4);
        var failure = new IllegalStateException("shutdown detail");
        pipeline.accept(failure);
        pipeline.accept(failure);

        pipeline.close();
        pipeline.close();

        var rendered = output.toString(StandardCharsets.UTF_8);
        assertEquals(1, occurrences(rendered, "error[SF0001]"));
        assertEquals(1, occurrences(rendered, "was observed 1 more time"));
        assertEquals(1, pipeline.stats().repeatSummaries());
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

    @Test
    void loadedSettingsControlOutputTraceDirectoryAndDeduplication() throws Exception {
        var configDirectory = Files.createDirectory(temp.resolve("config"));
        Files.writeString(configDirectory.resolve("stackframe.properties"), """
                schema_version=1
                output=ansi
                trace_directory=logs/configured-traces
                dedup_window_ms=0
                """);
        var configuration = ConfigurationFile.load(temp);
        var output = new ByteArrayOutputStream();
        var pipeline = new FabricDiagnosticPipeline(
                configuration, temp, rendered -> write(output, rendered));
        var failure = new IllegalStateException("private text");
        pipeline.accept(failure);
        pipeline.accept(failure);
        pipeline.close();

        var rendered = output.toString(StandardCharsets.UTF_8);
        assertEquals(2, occurrences(rendered, "SF0001"));
        assertTrue(rendered.contains("\u001b["), "explicit ANSI should reach the renderer");
        assertFalse(rendered.contains("private text"));
        assertEquals(0, pipeline.stats().suppressed());
        try (var files = Files.list(temp.resolve("logs/configured-traces"))) {
            assertEquals(2, files.filter(path -> path.toString().endsWith(".trace")).count());
        }
    }

    @Test
    void codeFilterDisablesSupplementalDiagnosticsAndTraceWrites() {
        var config = new StackframeConfiguration(
                StackframeConfiguration.Output.PLAIN, Path.of("filtered-traces"),
                TraceRetention.Policy.manual(), Config.disabled(), false, false);
        var output = new ByteArrayOutputStream();
        var pipeline = new FabricDiagnosticPipeline(
                config, temp, rendered -> write(output, rendered));
        pipeline.accept(new IllegalStateException("source event"));
        pipeline.close();

        assertEquals(1, pipeline.stats().filtered());
        assertEquals(0, pipeline.stats().accepted());
        assertEquals("", output.toString(StandardCharsets.UTF_8));
        assertFalse(Files.exists(temp.resolve("filtered-traces")));
    }

    @Test
    void boundedRetentionCleansOwnedFilesAtStartupAndAfterWrites() throws Exception {
        var traces = Files.createDirectory(temp.resolve("retained-traces"));
        var old = Files.writeString(traces.resolve("ABCDEFGHJKMNPQRS.trace"),
                TraceRecorder.FILE_HEADER + "old trace");
        Files.setLastModifiedTime(old, FileTime.from(Instant.now().minus(Duration.ofDays(10))));
        var unrelated = Files.writeString(traces.resolve("notes.trace"), "leave alone");
        var partial = Files.writeString(traces.resolve("ABCDEFGHJKMNPQRS.partial"), "leave alone");
        var config = new StackframeConfiguration(
                StackframeConfiguration.Output.PLAIN, Path.of("retained-traces"),
                TraceRetention.Policy.bounded(Duration.ofDays(7), 1),
                Config.disabled(), true, false);
        var output = new ByteArrayOutputStream();
        var pipeline = new FabricDiagnosticPipeline(
                config, temp, rendered -> write(output, rendered));
        assertFalse(Files.exists(old), "startup cleanup should remove expired owned traces");
        pipeline.accept(new IllegalStateException("first"));
        pipeline.accept(new IllegalStateException("second"));
        pipeline.close();

        try (var files = Files.list(traces)) {
            assertEquals(1, files.filter(path -> path.toString().endsWith(".trace")
                    && !path.equals(unrelated)).count());
        }
        assertTrue(Files.exists(unrelated));
        assertTrue(Files.exists(partial));
        assertFalse(output.toString(StandardCharsets.UTF_8).contains("retention could not complete"));
    }

    @Test
    void unsafeRetentionDirectorySurfacesAnOperatorWarning() throws Exception {
        Files.writeString(temp.resolve("not-a-directory"), "blocked");
        var config = new StackframeConfiguration(
                StackframeConfiguration.Output.PLAIN, Path.of("not-a-directory"),
                TraceRetention.Policy.bounded(Duration.ofDays(7), 10),
                Config.disabled(), true, false);
        var output = new ByteArrayOutputStream();
        var pipeline = new FabricDiagnosticPipeline(
                config, temp, rendered -> write(output, rendered));
        pipeline.close();

        var rendered = output.toString(StandardCharsets.UTF_8);
        assertTrue(rendered.contains("trace retention could not complete"));
        assertTrue(rendered.contains("UNSAFE_DIRECTORY"));
        assertFalse(rendered.contains("not-a-directory"));
    }

    @Test
    void burstSaturationKeepsCaptureNonblockingAndReportsSkippedDiagnostics() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var firstOutput = new AtomicBoolean(true);
        var output = new ByteArrayOutputStream();
        var pipeline = new FabricDiagnosticPipeline(
                new TraceRecorder(temp.resolve("burst-traces")), rendered -> {
                    if (firstOutput.compareAndSet(true, false)) {
                        entered.countDown();
                        try {
                            release.await(5, TimeUnit.SECONDS);
                        } catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(interrupted);
                        }
                    }
                    write(output, rendered);
                }, 2);
        try {
            pipeline.accept(new IllegalStateException("first failure"));
            assertTrue(entered.await(3, TimeUnit.SECONDS));
            var sender = Thread.ofPlatform().start(() -> {
                var repeated = new IllegalStateException("private repeated failure");
                for (var index = 0; index < 1_000; index++) {
                    pipeline.accept(repeated);
                }
            });
            sender.join(3_000);
            assertFalse(sender.isAlive(), "queue saturation blocked the capture thread");
            assertTrue(pipeline.stats().dropped() > 0);
            assertEquals(2, pipeline.stats().queueCapacity());
            assertTrue(pipeline.stats().queued() <= 2);
            assertTrue(pipeline.stats().peakQueued() <= 2);
            assertThrows(IllegalArgumentException.class,
                    () -> new FabricDiagnosticPipeline(
                            new TraceRecorder(temp.resolve("too-large")), rendered -> {}, 4_097));
        } finally {
            release.countDown();
            pipeline.close();
        }
        var stats = pipeline.stats();
        assertEquals(stats.accepted(), stats.processed());
        assertEquals(0, stats.queued());
        assertEquals(1_001, stats.accepted() + stats.dropped());
        assertTrue(output.toString(StandardCharsets.UTF_8)
                .contains("supplemental diagnostics skipped"));
    }

    @Test
    void sustainedPressureKeepsRetentionWithinQueueCapacity() throws Exception {
        var permits = new Semaphore(0);
        var pipeline = new FabricDiagnosticPipeline(
                new TraceRecorder(temp.resolve("sustained-traces")), rendered -> {
                    if (!rendered.contains("SF0001")) {
                        return;
                    }
                    try {
                        if (!permits.tryAcquire(5, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("test output gate timed out");
                        }
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(interrupted);
                    }
                }, 3);
        try {
            for (var round = 0; round < 20; round++) {
                for (var index = 0; index < 20; index++) {
                    pipeline.accept(new IllegalStateException("round " + round + " event " + index));
                }
                permits.release();
                awaitProcessed(pipeline, round + 1);
                assertTrue(pipeline.stats().queued() <= 3);
            }
        } finally {
            permits.release(1_000);
            pipeline.close();
        }
        var stats = pipeline.stats();
        assertEquals(400, stats.accepted() + stats.dropped());
        assertEquals(stats.accepted(), stats.processed());
        assertEquals(0, stats.queued());
        assertTrue(stats.peakQueued() <= stats.queueCapacity());
    }

    private static void awaitProcessed(FabricDiagnosticPipeline pipeline, long target)
            throws InterruptedException {
        var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (pipeline.stats().processed() < target && System.nanoTime() < deadline) {
            Thread.sleep(1);
        }
        assertTrue(pipeline.stats().processed() >= target,
                "worker did not make progress under sustained pressure");
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
