package org.minecraftprot.stackframe.extension;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.minecraftprot.stackframe.diagnostic.DisplayText;
import org.minecraftprot.stackframe.diagnostic.RedactionMarker;
import org.minecraftprot.stackframe.diagnostic.Sensitivity;
import org.minecraftprot.stackframe.diagnostic.TextOrigin;

/**
 * Bounded in-process extension dispatcher. Raw extension values become canonical
 * omission markers; the caller retains the original error and fallback path.
 */
public final class ExtensionHost implements AutoCloseable {
    private static final RedactionMarker EXTENSION_DATA = new RedactionMarker("EXTENSION_DATA");
    public static final Limits DEFAULT_LIMITS = new Limits(
            32, 8, 16, 16_384, 4, Duration.ofMillis(50), Duration.ofMillis(200));

    public enum RegistrationStatus {
        REGISTERED,
        NAMESPACE_COLLISION
    }

    public enum Status {
        ACCEPTED,
        EMPTY,
        NAMESPACE_COLLISION,
        MALFORMED,
        FAILED,
        TIMED_OUT,
        BUSY,
        EVENT_BUDGET_EXHAUSTED,
        QUARANTINED,
        INTERRUPTED
    }

    /** Hard bounds for one host and event; durations are upper waits, not hard thread kills. */
    public record Limits(
            int maxRegistrations,
            int maxFindingsPerExtension,
            int maxEvidencePerFinding,
            int maxEvidenceCodePoints,
            int maxWorkers,
            Duration perCallback,
            Duration perEvent) {
        public Limits {
            if (maxRegistrations < 1 || maxRegistrations > 256
                    || maxFindingsPerExtension < 1 || maxFindingsPerExtension > 64
                    || maxEvidencePerFinding < 1 || maxEvidencePerFinding > 32
                    || maxEvidenceCodePoints < 1 || maxEvidenceCodePoints > 262_144
                    || maxWorkers < 1 || maxWorkers > 8
                    || perCallback == null || perEvent == null
                    || perCallback.isZero() || perCallback.isNegative()
                    || perEvent.isZero() || perEvent.isNegative()
                    || perCallback.compareTo(Duration.ofSeconds(1)) > 0
                    || perEvent.compareTo(Duration.ofSeconds(2)) > 0) {
                throw new IllegalArgumentException("invalid extension limits");
            }
        }
    }

    /** Status only; exception messages and raw extension text are never logged here. */
    public record Outcome(
            ExtensionNamespace namespace, Status status, List<SafeExtensionFinding> findings) {
        public Outcome {
            if (namespace == null || status == null || findings == null) {
                throw new IllegalArgumentException("invalid extension outcome");
            }
            findings = List.copyOf(findings);
            if ((status == Status.ACCEPTED) != !findings.isEmpty()) {
                throw new IllegalArgumentException("accepted outcomes require findings only");
            }
        }
    }

    /** Values are omitted; the normal diagnostic pipeline still validates all claims. */
    public record Result(List<Outcome> outcomes) {
        public Result {
            if (outcomes == null) {
                throw new IllegalArgumentException("outcomes must not be null");
            }
            outcomes = List.copyOf(outcomes);
        }

        public List<SafeExtensionFinding> findings() {
            return outcomes.stream()
                    .filter(outcome -> outcome.status() == Status.ACCEPTED)
                    .flatMap(outcome -> outcome.findings().stream())
                    .toList();
        }

    }

    private enum State {
        OPEN,
        FROZEN,
        CLOSED
    }

    private final Limits limits;
    private final ThreadPoolExecutor executor;
    private final Map<ExtensionNamespace, Entry> active = new HashMap<>();
    private final Set<ExtensionNamespace> seenNamespaces = new HashSet<>();
    private final Set<ExtensionNamespace> collisions = new HashSet<>();
    private volatile List<Slot> frozenSlots = List.of();
    private volatile State state = State.OPEN;

    public ExtensionHost() {
        this(DEFAULT_LIMITS);
    }

    public ExtensionHost(Limits limits) {
        if (limits == null) {
            throw new IllegalArgumentException("limits must not be null");
        }
        this.limits = limits;
        var nextThread = new AtomicInteger();
        this.executor = new ThreadPoolExecutor(
                0,
                limits.maxWorkers(),
                30,
                TimeUnit.SECONDS,
                new SynchronousQueue<>(),
                task -> {
                    var thread = new Thread(task,
                            "stackframe-extension-" + nextThread.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy());
    }

    /**
     * Register before {@link #freeze()}. Duplicate namespaces disable every
     * claimant, independent of registration order.
     */
    public synchronized RegistrationStatus register(ExtensionRegistration registration) {
        if (state != State.OPEN) {
            throw new IllegalStateException("extension registration is closed");
        }
        if (registration == null) {
            throw new IllegalArgumentException("registration must not be null");
        }
        var namespace = registration.namespace();
        if (collisions.contains(namespace)) {
            return RegistrationStatus.NAMESPACE_COLLISION;
        }
        if (active.containsKey(namespace)) {
            active.remove(namespace);
            collisions.add(namespace);
            return RegistrationStatus.NAMESPACE_COLLISION;
        }
        if (seenNamespaces.size() >= limits.maxRegistrations()) {
            throw new IllegalArgumentException("extension registration limit exceeded");
        }
        seenNamespaces.add(namespace);
        active.put(namespace, new Entry(registration));
        return RegistrationStatus.REGISTERED;
    }

    /** Fix the set and order of extensions before observing errors. */
    public synchronized void freeze() {
        if (state == State.CLOSED) {
            throw new IllegalStateException("extension host is closed");
        }
        if (state == State.FROZEN) {
            return;
        }
        var slots = new ArrayList<Slot>();
        active.forEach((namespace, entry) -> slots.add(new Slot(namespace, entry)));
        collisions.forEach(namespace -> slots.add(new Slot(namespace, null)));
        slots.sort(Comparator.comparing(slot -> slot.namespace().value()));
        frozenSlots = List.copyOf(slots);
        state = State.FROZEN;
    }

    /**
     * Inspect a bounded, already-redacted event. A timeout or error skips only the
     * failing extension; the caller still owns the original error and fallback.
     */
    public Result inspect(ExtensionEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("event must not be null");
        }
        if (state != State.FROZEN) {
            throw new IllegalStateException("extension host must be frozen");
        }
        var deadline = System.nanoTime() + limits.perEvent().toNanos();
        var outcomes = new ArrayList<Outcome>();
        var interrupted = false;
        for (var slot : frozenSlots) {
            var namespace = slot.namespace();
            var entry = slot.entry();
            if (entry == null) {
                outcomes.add(empty(namespace, Status.NAMESPACE_COLLISION));
            } else if (entry.quarantined.get()) {
                outcomes.add(empty(namespace, Status.QUARANTINED));
            } else if (interrupted) {
                outcomes.add(empty(namespace, Status.INTERRUPTED));
            } else if (deadline - System.nanoTime() <= 0) {
                outcomes.add(empty(namespace, Status.EVENT_BUDGET_EXHAUSTED));
            } else if (!entry.running.compareAndSet(false, true)) {
                outcomes.add(empty(namespace, Status.BUSY));
            } else {
                outcomes.add(invoke(namespace, entry, event, deadline));
                if (Thread.currentThread().isInterrupted()) {
                    interrupted = true;
                }
            }
        }
        return new Result(outcomes);
    }

    private Outcome invoke(
            ExtensionNamespace namespace,
            Entry entry,
            ExtensionEvent event,
            long deadline) {
        Future<List<SafeExtensionFinding>> future;
        try {
            future = executor.submit(() -> {
                try {
                    return validate(entry.registration,
                            entry.registration.callback().inspect(event));
                } finally {
                    entry.running.set(false);
                }
            });
        } catch (RejectedExecutionException rejected) {
            entry.running.set(false);
            return empty(namespace, Status.BUSY);
        }

        try {
            var remaining = deadline - System.nanoTime();
            if (remaining <= 0) {
                entry.quarantined.set(true);
                future.cancel(true);
                return empty(namespace, Status.EVENT_BUDGET_EXHAUSTED);
            }
            var findings = future.get(
                    Math.min(remaining, limits.perCallback().toNanos()), TimeUnit.NANOSECONDS);
            return new Outcome(namespace,
                    findings.isEmpty() ? Status.EMPTY : Status.ACCEPTED, findings);
        } catch (TimeoutException timeout) {
            entry.quarantined.set(true);
            future.cancel(true);
            return empty(namespace, Status.TIMED_OUT);
        } catch (InterruptedException interrupted) {
            entry.quarantined.set(true);
            future.cancel(true);
            Thread.currentThread().interrupt();
            return empty(namespace, Status.INTERRUPTED);
        } catch (ExecutionException | CancellationException failed) {
            entry.quarantined.set(true);
            var cause = failed instanceof ExecutionException execution
                    ? execution.getCause() : failed;
            return empty(namespace,
                    cause instanceof MalformedContributionException
                            ? Status.MALFORMED : Status.FAILED);
        }
    }

    private List<SafeExtensionFinding> validate(
            ExtensionRegistration registration,
            List<ExtensionFinding> raw) {
        if (raw == null || raw.size() > limits.maxFindingsPerExtension()) {
            throw new MalformedContributionException();
        }
        var copy = new ArrayList<SafeExtensionFinding>();
        var seenCodes = new HashSet<ExtensionCode>();
        long codePoints = 0;
        for (var finding : raw) {
            if (finding == null || !registration.codes().contains(finding.code())
                    || !seenCodes.add(finding.code())
                    || finding.evidence().size() > limits.maxEvidencePerFinding()) {
                throw new MalformedContributionException();
            }
            var safeEvidence = new ArrayList<SafeExtensionEvidence>();
            for (var evidence : finding.evidence()) {
                codePoints += evidence.value().value().codePointCount(
                        0, evidence.value().value().length());
                if (codePoints > limits.maxEvidenceCodePoints()) {
                    throw new MalformedContributionException();
                }
                safeEvidence.add(new SafeExtensionEvidence(
                        evidence.sourceKey(), evidence.kind(), evidence.strength(),
                        evidence.capabilities(),
                        DisplayText.omitted(
                                TextOrigin.EXTERNAL, Sensitivity.SECRET, EXTENSION_DATA)));
            }
            copy.add(new SafeExtensionFinding(finding.code(), safeEvidence));
        }
        return List.copyOf(copy);
    }

    private static Outcome empty(ExtensionNamespace namespace, Status status) {
        return new Outcome(namespace, status, List.of());
    }

    @Override
    public synchronized void close() {
        if (state == State.CLOSED) {
            return;
        }
        state = State.CLOSED;
        active.clear();
        collisions.clear();
        frozenSlots = List.of();
        executor.shutdownNow();
    }

    private record Slot(ExtensionNamespace namespace, Entry entry) {
    }

    private static final class Entry {
        private final ExtensionRegistration registration;
        private final AtomicBoolean running = new AtomicBoolean();
        private final AtomicBoolean quarantined = new AtomicBoolean();

        private Entry(ExtensionRegistration registration) {
            this.registration = registration;
        }
    }

    private static final class MalformedContributionException extends RuntimeException {
    }
}
