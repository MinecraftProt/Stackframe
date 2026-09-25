package org.minecraftprot.stackframe.fabric;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
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
        assertFalse(rendered.contains("startup details"));
        assertFalse(rendered.contains("runtime details"));
        try (var files = Files.list(traces)) {
            var complete = files.filter(path -> path.toString().endsWith(".trace")).toList();
            assertEquals(2, complete.size());
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

    private static boolean contains(Path path, String text) {
        try {
            return Files.readString(path).contains(text);
        } catch (java.io.IOException failure) {
            throw new AssertionError(failure);
        }
    }
}
