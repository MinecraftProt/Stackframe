package org.minecraftprot.stackframe.correlation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.minecraftprot.stackframe.correlation.DiagnosticCorrelator.Config;
import org.minecraftprot.stackframe.correlation.DiagnosticCorrelator.Disposition;
import org.minecraftprot.stackframe.correlation.DiagnosticCorrelator.Importance;
import org.minecraftprot.stackframe.correlation.DiagnosticCorrelator.RepeatSummary;
import org.minecraftprot.stackframe.correlation.DiagnosticCorrelator.SummaryReason;
import org.minecraftprot.stackframe.diagnostic.CorrelationId;

class DiagnosticCorrelatorTest {
    @Test
    void duplicateObservationsShareOneDiagnosticAndExactRepeatSummary() {
        var failure = new IllegalStateException("private error text");
        var correlator = new DiagnosticCorrelator(config(2, 100), () -> 0);

        assertEquals(Disposition.EMIT_DIAGNOSTIC,
                correlator.observe(failure, id(1), Importance.ORDINARY).disposition());
        var second = correlator.observe(failure, id(2), Importance.ORDINARY);
        var third = correlator.observe(failure, id(3), Importance.ORDINARY);

        assertEquals(Disposition.SUPPRESS_DIAGNOSTIC_ONLY, second.disposition());
        assertEquals(Disposition.SUPPRESS_DIAGNOSTIC_ONLY, third.disposition());
        assertEquals(id(1), second.correlationId());
        assertEquals(id(1), third.correlationId());
        assertEquals(List.of(new RepeatSummary(id(1), 2, SummaryReason.DRAINED)),
                correlator.drainAll());
        assertEquals(3, new RepeatSummary(id(1), 2, SummaryReason.DRAINED)
                .totalObservations());
        assertEquals(0, correlator.activeEntries());
        assertTrue(correlator.drainAll().isEmpty());
        assertTrue(second.toString().contains("000001"));
        assertFalse(second.toString().contains("private error text"));
    }

    @Test
    void distinctThrowablesWithEqualMessagesNeverMerge() {
        var correlator = new DiagnosticCorrelator(config(2, 100), () -> 0);
        var first = new IllegalStateException("same message");
        var second = new IllegalStateException("same message");

        assertEquals(Disposition.EMIT_DIAGNOSTIC,
                correlator.observe(first, id(1), Importance.ORDINARY).disposition());
        assertEquals(Disposition.EMIT_DIAGNOSTIC,
                correlator.observe(second, id(2), Importance.ORDINARY).disposition());
        assertEquals(2, correlator.activeEntries());
        assertTrue(correlator.drainAll().isEmpty());
    }

    @Test
    void fixedWindowExpiresAtExactBoundaryAndDoesNotSlideOnRepeat() {
        var now = new AtomicLong(1_000);
        var correlator = new DiagnosticCorrelator(config(2, 100), now::get);
        var failure = new IllegalStateException();
        correlator.observe(failure, id(1), Importance.ORDINARY);
        now.set(1_099);
        assertEquals(Disposition.SUPPRESS_DIAGNOSTIC_ONLY,
                correlator.observe(failure, id(2), Importance.ORDINARY).disposition());
        now.set(1_100);

        var next = correlator.observe(failure, id(3), Importance.ORDINARY);
        assertEquals(Disposition.EMIT_DIAGNOSTIC, next.disposition());
        assertEquals(id(3), next.correlationId());
        assertEquals(List.of(new RepeatSummary(id(1), 1, SummaryReason.WINDOW_EXPIRED)),
                next.summaries());
        now.set(1_200);
        assertTrue(correlator.drainExpired().isEmpty());
        assertEquals(0, correlator.activeEntries());
    }

    @Test
    void expirationCanBeDrainedWithoutAnotherObservation() {
        var now = new AtomicLong();
        var correlator = new DiagnosticCorrelator(config(2, 100), now::get);
        var failure = new IllegalArgumentException();
        correlator.observe(failure, id(1), Importance.ORDINARY);
        correlator.observe(failure, id(2), Importance.ORDINARY);
        now.set(99);
        assertTrue(correlator.drainExpired().isEmpty());
        now.set(100);
        assertEquals(List.of(new RepeatSummary(id(1), 1, SummaryReason.WINDOW_EXPIRED)),
                correlator.drainExpired());
        assertTrue(correlator.drainExpired().isEmpty());
    }

    @Test
    void capacityEvictionPublishesPendingCountsAndBoundsState() {
        var correlator = new DiagnosticCorrelator(config(2, 100), () -> 0);
        var first = new IllegalStateException("one");
        var second = new IllegalStateException("two");
        var third = new IllegalStateException("three");
        correlator.observe(first, id(1), Importance.ORDINARY);
        correlator.observe(first, id(4), Importance.ORDINARY);
        correlator.observe(second, id(2), Importance.ORDINARY);

        var decision = correlator.observe(third, id(3), Importance.ORDINARY);
        assertEquals(Disposition.EMIT_DIAGNOSTIC, decision.disposition());
        assertEquals(List.of(new RepeatSummary(id(1), 1, SummaryReason.CAPACITY_EVICTED)),
                decision.summaries());
        assertEquals(2, correlator.activeEntries());
        assertEquals(Disposition.EMIT_DIAGNOSTIC,
                correlator.observe(first, id(5), Importance.ORDINARY).disposition());
    }

    @Test
    void concurrentObservationsHaveExactlyOneLeaderAndNoLostCounts() throws Exception {
        var correlator = new DiagnosticCorrelator(
                new Config(true, Duration.ofSeconds(30), 4), System::nanoTime);
        var failure = new IllegalStateException();
        var gate = new CountDownLatch(1);
        var futures = new ArrayList<java.util.concurrent.Future<DiagnosticCorrelator.Decision>>();
        try (var workers = Executors.newFixedThreadPool(8)) {
            for (var index = 0; index < 64; index++) {
                var proposed = id(index);
                futures.add(workers.submit(() -> {
                    gate.await();
                    return correlator.observe(failure, proposed, Importance.ORDINARY);
                }));
            }
            gate.countDown();
            var decisions = new ArrayList<DiagnosticCorrelator.Decision>();
            for (var future : futures) {
                decisions.add(future.get(5, TimeUnit.SECONDS));
            }
            assertEquals(1, decisions.stream()
                    .filter(item -> item.disposition() == Disposition.EMIT_DIAGNOSTIC).count());
            assertEquals(63, decisions.stream()
                    .filter(item -> item.disposition() == Disposition.SUPPRESS_DIAGNOSTIC_ONLY)
                    .count());
            assertEquals(1, new HashSet<>(decisions.stream()
                    .map(DiagnosticCorrelator.Decision::correlationId).toList()).size());
            var summary = correlator.drainAll().getFirst();
            assertEquals(63, summary.repeatCount());
            assertEquals(64, summary.totalObservations());
        }
    }

    @Test
    void criticalEventsAlwaysEmitAndCloseAnOrdinaryWindow() {
        var correlator = new DiagnosticCorrelator(config(2, 100), () -> 0);
        var failure = new IllegalStateException();
        correlator.observe(failure, id(1), Importance.ORDINARY);
        correlator.observe(failure, id(2), Importance.ORDINARY);

        var firstCritical = correlator.observe(failure, id(3), Importance.CRITICAL);
        var secondCritical = correlator.observe(failure, id(4), Importance.CRITICAL);
        assertEquals(Disposition.EMIT_DIAGNOSTIC, firstCritical.disposition());
        assertEquals(id(3), firstCritical.correlationId());
        assertEquals(List.of(new RepeatSummary(id(1), 1, SummaryReason.CRITICAL_BYPASS)),
                firstCritical.summaries());
        assertEquals(Disposition.EMIT_DIAGNOSTIC, secondCritical.disposition());
        assertTrue(secondCritical.summaries().isEmpty());
        assertEquals(0, correlator.activeEntries());
    }

    @Test
    void disabledModeEmitsEveryDiagnosticAndKeepsNoState() {
        var correlator = new DiagnosticCorrelator(Config.disabled(), () -> 0);
        var failure = new IllegalStateException();

        assertEquals(Disposition.EMIT_DIAGNOSTIC,
                correlator.observe(failure, id(1), Importance.ORDINARY).disposition());
        assertEquals(Disposition.EMIT_DIAGNOSTIC,
                correlator.observe(failure, id(2), Importance.ORDINARY).disposition());
        assertEquals(0, correlator.activeEntries());
        assertTrue(correlator.drainExpired().isEmpty());
        assertTrue(correlator.drainAll().isEmpty());
    }

    @Test
    void regressedClockRetiresTheWindowWithItsCount() {
        var now = new AtomicLong(100);
        var correlator = new DiagnosticCorrelator(config(2, 100), now::get);
        var failure = new IllegalStateException();
        correlator.observe(failure, id(1), Importance.ORDINARY);
        correlator.observe(failure, id(2), Importance.ORDINARY);
        now.set(99);

        var decision = correlator.observe(failure, id(3), Importance.ORDINARY);
        assertEquals(Disposition.EMIT_DIAGNOSTIC, decision.disposition());
        assertEquals(List.of(new RepeatSummary(id(1), 1, SummaryReason.CLOCK_REGRESSED)),
                decision.summaries());
    }

    @Test
    void invalidBoundsAndInputsFailWithoutChangingState() {
        assertThrows(IllegalArgumentException.class,
                () -> new Config(true, Duration.ZERO, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new Config(true, Duration.ofMinutes(6), 1));
        assertThrows(IllegalArgumentException.class,
                () -> new Config(true, Duration.ofNanos(1), 4_097));
        var correlator = new DiagnosticCorrelator(config(2, 100), () -> 0);
        assertThrows(NullPointerException.class,
                () -> correlator.observe(null, id(1), Importance.ORDINARY));
        assertThrows(NullPointerException.class,
                () -> correlator.observe(new IllegalStateException(), null,
                        Importance.ORDINARY));
        assertThrows(NullPointerException.class,
                () -> correlator.observe(new IllegalStateException(), id(1), null));
        assertEquals(0, correlator.activeEntries());
    }

    private static Config config(int capacity, long windowNanos) {
        return new Config(true, Duration.ofNanos(windowNanos), capacity);
    }

    private static CorrelationId id(int number) {
        return new CorrelationId(String.format(Locale.ROOT, "%06d", number));
    }
}
