package org.minecraftprot.stackframe.correlation;

import java.lang.ref.WeakReference;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.LongSupplier;
import org.minecraftprot.stackframe.diagnostic.CorrelationId;

/**
 * Bounded, loader-neutral suppression of repeated observations of the same
 * throwable object. This never changes the caller's original logging path.
 */
public final class DiagnosticCorrelator {
    public static final Config DEFAULT_CONFIG =
            new Config(true, Duration.ofSeconds(30), 256);

    public enum Importance {
        ORDINARY,
        CRITICAL
    }

    public enum Disposition {
        EMIT_DIAGNOSTIC,
        SUPPRESS_DIAGNOSTIC_ONLY
    }

    public enum SummaryReason {
        WINDOW_EXPIRED,
        CAPACITY_EVICTED,
        IDENTITY_COLLECTED,
        CLOCK_REGRESSED,
        CRITICAL_BYPASS,
        COUNT_LIMIT,
        DRAINED
    }

    /** The window starts with the first observation, not the most recent repeat. */
    public record Config(boolean enabled, Duration window, int maxEntries) {
        public Config {
            if (window == null || window.isZero() || window.isNegative()
                    || window.compareTo(Duration.ofMinutes(5)) > 0
                    || maxEntries < 1 || maxEntries > 4_096) {
                throw new IllegalArgumentException("invalid correlation bounds");
            }
        }

        public static Config disabled() {
            return new Config(false, DEFAULT_CONFIG.window(), DEFAULT_CONFIG.maxEntries());
        }
    }

    /** A compact, renderer-independent summary; count excludes the first event. */
    public record RepeatSummary(
            CorrelationId correlationId, long repeatCount, SummaryReason reason) {
        public RepeatSummary {
            if (correlationId == null || repeatCount < 1 || reason == null) {
                throw new IllegalArgumentException("invalid repeat summary");
            }
        }

        public long totalObservations() {
            return Math.addExact(repeatCount, 1);
        }
    }

    /** Summaries must be published even when a new diagnostic is also emitted. */
    public record Decision(
            Disposition disposition,
            CorrelationId correlationId,
            List<RepeatSummary> summaries) {
        public Decision {
            if (disposition == null || correlationId == null || summaries == null) {
                throw new IllegalArgumentException("invalid correlation decision");
            }
            summaries = List.copyOf(summaries);
        }
    }

    private final Config config;
    private final LongSupplier ticker;
    private final List<Entry> entries = new ArrayList<>();

    public DiagnosticCorrelator(Config config) {
        this(config, System::nanoTime);
    }

    DiagnosticCorrelator(Config config, LongSupplier ticker) {
        this.config = Objects.requireNonNull(config, "config");
        this.ticker = Objects.requireNonNull(ticker, "ticker");
    }

    /**
     * Decide only whether Stackframe emits a supplemental diagnostic. Callers
     * must preserve the original log/crash event for every observation.
     * Distinct throwable instances are never grouped by text or stack trace.
     */
    public synchronized Decision observe(
            Throwable failure,
            CorrelationId proposedCorrelationId,
            Importance importance) {
        Objects.requireNonNull(failure, "failure");
        Objects.requireNonNull(proposedCorrelationId, "proposedCorrelationId");
        Objects.requireNonNull(importance, "importance");
        if (!config.enabled()) {
            return new Decision(Disposition.EMIT_DIAGNOSTIC,
                    proposedCorrelationId, List.of());
        }

        var now = ticker.getAsLong();
        var summaries = new ArrayList<RepeatSummary>();
        retireExpired(now, summaries);
        var existingIndex = indexOf(failure);
        if (importance == Importance.CRITICAL) {
            if (existingIndex >= 0) {
                retire(existingIndex, SummaryReason.CRITICAL_BYPASS, summaries);
            }
            return new Decision(Disposition.EMIT_DIAGNOSTIC,
                    proposedCorrelationId, summaries);
        }
        if (existingIndex >= 0) {
            var existing = entries.get(existingIndex);
            // Keep both the repeat count and total-observation count exact in long.
            if (existing.repeats == Long.MAX_VALUE - 1) {
                retire(existingIndex, SummaryReason.COUNT_LIMIT, summaries);
            } else {
                existing.repeats++;
                return new Decision(Disposition.SUPPRESS_DIAGNOSTIC_ONLY,
                        existing.correlationId, summaries);
            }
        }
        if (entries.size() == config.maxEntries()) {
            retire(0, SummaryReason.CAPACITY_EVICTED, summaries);
        }
        entries.add(new Entry(failure, proposedCorrelationId, now));
        return new Decision(Disposition.EMIT_DIAGNOSTIC,
                proposedCorrelationId, summaries);
    }

    /** Retire elapsed windows, for example on a periodic platform tick. */
    public synchronized List<RepeatSummary> drainExpired() {
        if (!config.enabled()) {
            return List.of();
        }
        var summaries = new ArrayList<RepeatSummary>();
        retireExpired(ticker.getAsLong(), summaries);
        return List.copyOf(summaries);
    }

    /** Publish returned summaries during shutdown or before replacing config. */
    public synchronized List<RepeatSummary> drainAll() {
        var summaries = new ArrayList<RepeatSummary>();
        while (!entries.isEmpty()) {
            retire(0, SummaryReason.DRAINED, summaries);
        }
        return List.copyOf(summaries);
    }

    /** Retained source identities and counters, never more than maxEntries. */
    public synchronized int activeEntries() {
        return entries.size();
    }

    private int indexOf(Throwable failure) {
        for (var index = 0; index < entries.size(); index++) {
            if (entries.get(index).source.get() == failure) {
                return index;
            }
        }
        return -1;
    }

    private void retireExpired(long now, List<RepeatSummary> summaries) {
        for (var index = 0; index < entries.size();) {
            var entry = entries.get(index);
            var elapsed = now - entry.startedAtNanos;
            SummaryReason reason = null;
            if (elapsed < 0) {
                reason = SummaryReason.CLOCK_REGRESSED;
            } else if (elapsed >= config.window().toNanos()) {
                reason = SummaryReason.WINDOW_EXPIRED;
            } else if (entry.source.get() == null) {
                reason = SummaryReason.IDENTITY_COLLECTED;
            }
            if (reason == null) {
                index++;
            } else {
                retire(index, reason, summaries);
            }
        }
    }

    private void retire(int index, SummaryReason reason, List<RepeatSummary> summaries) {
        var removed = entries.remove(index);
        if (removed.repeats > 0) {
            summaries.add(new RepeatSummary(removed.correlationId, removed.repeats, reason));
        }
    }

    private static final class Entry {
        private final WeakReference<Throwable> source;
        private final CorrelationId correlationId;
        private final long startedAtNanos;
        private long repeats;

        private Entry(Throwable source, CorrelationId correlationId, long startedAtNanos) {
            this.source = new WeakReference<>(source);
            this.correlationId = correlationId;
            this.startedAtNanos = startedAtNanos;
        }
    }
}
