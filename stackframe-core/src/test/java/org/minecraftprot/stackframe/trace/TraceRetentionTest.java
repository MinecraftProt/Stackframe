package org.minecraftprot.stackframe.trace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TraceRetentionTest {
    @TempDir Path temporaryDirectory;

    @Test
    void manualDefaultNeverScansOrDeletes() throws IOException {
        var traces = Files.createDirectory(temporaryDirectory.resolve("traces"));
        var old = file(traces, "0000000000000001.trace", Instant.parse("2020-01-01T00:00:00Z"));

        var result = new TraceRetention(traces, TraceRetention.Policy.manual()).clean();

        assertEquals(TraceRetention.Status.MANUAL, result.status());
        assertEquals(0, result.inspected());
        assertTrue(Files.exists(old));
    }

    @Test
    void boundedCleanupDeletesOnlyOldOrExcessOwnedRegularTraces() throws IOException {
        var traces = Files.createDirectory(temporaryDirectory.resolve("traces"));
        var now = Instant.parse("2026-09-25T12:00:00Z");
        var newest = file(traces, "0000000000000001.trace", now.minus(Duration.ofDays(1)));
        var second = file(traces, "0000000000000002.trace", now.minus(Duration.ofDays(2)));
        var excess = file(traces, "0000000000000003.trace", now.minus(Duration.ofDays(3)));
        var expired = file(traces, "0000000000000004.trace", now.minus(Duration.ofDays(40)));
        var partial = file(traces, "0000000000000005.partial", now.minus(Duration.ofDays(40)));
        var unrelated = file(traces, "other.trace", now.minus(Duration.ofDays(40)));
        var unmarked = Files.writeString(traces.resolve("0000000000000007.trace"), "not Stackframe");
        Files.setLastModifiedTime(unmarked, FileTime.from(now.minus(Duration.ofDays(40))));
        var outside = file(temporaryDirectory, "outside", now.minus(Duration.ofDays(40)));
        var link = traces.resolve("0000000000000006.trace");
        var linked = createLinkIfSupported(link, outside);

        var retention = new TraceRetention(traces,
                TraceRetention.Policy.bounded(Duration.ofDays(30), 2),
                Clock.fixed(now, ZoneOffset.UTC));
        var result = retention.clean();

        assertEquals(TraceRetention.Status.CLEAN, result.status());
        assertEquals(2, result.deleted());
        assertTrue(Files.exists(newest));
        assertTrue(Files.exists(second));
        assertFalse(Files.exists(excess));
        assertFalse(Files.exists(expired));
        assertTrue(Files.exists(partial));
        assertTrue(Files.exists(unrelated));
        assertTrue(Files.exists(unmarked));
        assertTrue(Files.exists(outside));
        if (linked) {
            assertTrue(Files.isSymbolicLink(link));
        }
        assertEquals(0, retention.clean().deleted());
    }

    @Test
    void missingOrUnsafeDirectoriesSurfaceFailureWithoutDeletingAnything() throws IOException {
        var now = Clock.fixed(Instant.parse("2026-09-25T12:00:00Z"), ZoneOffset.UTC);
        var policy = TraceRetention.Policy.bounded(Duration.ofDays(1), 10);
        var missing = new TraceRetention(temporaryDirectory.resolve("missing"), policy, now);
        assertEquals(TraceRetention.Status.DIRECTORY_MISSING, missing.clean().status());
        assertTrue(missing.clean().failed());

        var ordinaryFile = Files.writeString(temporaryDirectory.resolve("not-a-directory"), "keep");
        var unsafe = new TraceRetention(ordinaryFile, policy, now);
        assertEquals(TraceRetention.Status.UNSAFE_DIRECTORY, unsafe.clean().status());
        assertEquals("keep", Files.readString(ordinaryFile));

        var link = temporaryDirectory.resolve("linked-directory");
        if (createLinkIfSupported(link, temporaryDirectory)) {
            var linked = new TraceRetention(link, policy, now);
            assertEquals(TraceRetention.Status.UNSAFE_DIRECTORY, linked.clean().status());
            assertTrue(Files.exists(ordinaryFile));
        }
    }

    @Test
    void newlyPublishedTraceIsKeptEvenWhenAnotherTraceHasAFutureTimestamp()
            throws IOException {
        var traces = Files.createDirectory(temporaryDirectory.resolve("traces"));
        var now = Instant.parse("2026-09-25T12:00:00Z");
        var future = file(traces, "0000000000000001.trace", now.plus(Duration.ofDays(1)));
        var current = file(traces, "0000000000000002.trace", now);
        var retention = new TraceRetention(traces,
                TraceRetention.Policy.bounded(Duration.ofDays(7), 1),
                Clock.fixed(now, ZoneOffset.UTC));

        var result = retention.clean(current);

        assertEquals(TraceRetention.Status.CLEAN, result.status());
        assertTrue(Files.exists(current));
        assertFalse(Files.exists(future));
    }

    @Test
    void invalidBoundsCannotEnableUnboundedCleanup() {
        assertThrows(IllegalArgumentException.class,
                () -> TraceRetention.Policy.bounded(Duration.ZERO, 1));
        assertThrows(IllegalArgumentException.class,
                () -> TraceRetention.Policy.bounded(Duration.ofDays(366), 1));
        assertThrows(IllegalArgumentException.class,
                () -> TraceRetention.Policy.bounded(Duration.ofDays(1), 0));
        assertThrows(IllegalArgumentException.class,
                () -> new TraceRetention.Policy(false, Duration.ofDays(1), 1));
    }

    private static Path file(Path directory, String name, Instant modified) throws IOException {
        var path = Files.writeString(directory.resolve(name),
                TraceRecorder.FILE_HEADER + "private trace");
        Files.setLastModifiedTime(path, FileTime.from(modified));
        return path;
    }

    private static boolean createLinkIfSupported(Path link, Path target) {
        try {
            Files.createSymbolicLink(link, target);
            return true;
        } catch (IOException | UnsupportedOperationException | SecurityException unavailable) {
            return false;
        }
    }
}
