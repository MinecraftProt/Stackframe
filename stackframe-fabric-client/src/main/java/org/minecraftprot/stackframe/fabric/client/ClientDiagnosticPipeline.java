package org.minecraftprot.stackframe.fabric.client;

import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.minecraftprot.stackframe.diagnostic.BoundedList;
import org.minecraftprot.stackframe.diagnostic.ConfidenceReference;
import org.minecraftprot.stackframe.diagnostic.Diagnostic;
import org.minecraftprot.stackframe.diagnostic.DiagnosticDocument;
import org.minecraftprot.stackframe.diagnostic.DiagnosticId;
import org.minecraftprot.stackframe.diagnostic.DisplayText;
import org.minecraftprot.stackframe.diagnostic.Note;
import org.minecraftprot.stackframe.diagnostic.NoteKind;
import org.minecraftprot.stackframe.diagnostic.SchemaVersion;
import org.minecraftprot.stackframe.diagnostic.Severity;
import org.minecraftprot.stackframe.diagnostic.TextOrigin;
import org.minecraftprot.stackframe.diagnostic.registry.CanonicalDiagnosticRegistry;
import org.minecraftprot.stackframe.renderer.DiagnosticRenderer;
import org.minecraftprot.stackframe.renderer.RenderOptions;
import org.minecraftprot.stackframe.renderer.RenderWidth;
import org.minecraftprot.stackframe.trace.TraceRecorder;

/**
 * Nonblocking client-thread handoff. Only fixed generated text reaches the
 * supplementary log; the original throwable is written to a private raw trace
 * by the worker. Capture never owns Minecraft's logging or crash behavior.
 */
public final class ClientDiagnosticPipeline implements AutoCloseable {
    private static final int DEFAULT_CAPACITY = 128;
    private static final int IDENTITY_LIMIT = 256;
    private static final long SHUTDOWN_WAIT_MILLIS = 500;
    private static final Logger DIAGNOSTIC_LOGGER =
            LogManager.getLogger("org.minecraftprot.stackframe.fabric.client.Diagnostic");

    private final ArrayBlockingQueue<Observation> pending;
    private final ArrayDeque<WeakReference<Throwable>> acceptedIdentities = new ArrayDeque<>();
    private final TraceRecorder recorder;
    private final Consumer<String> output;
    private final Thread worker;
    private final AtomicLong accepted = new AtomicLong();
    private final AtomicLong processed = new AtomicLong();
    private final AtomicLong duplicates = new AtomicLong();
    private final AtomicLong dropped = new AtomicLong();
    private final AtomicLong processingFailures = new AtomicLong();
    private volatile boolean accepting = true;

    public ClientDiagnosticPipeline() {
        this(new TraceRecorder(TraceRecorder.defaultDirectory()),
                ClientDiagnosticPipeline::logDiagnostic, DEFAULT_CAPACITY);
    }

    ClientDiagnosticPipeline(TraceRecorder recorder, Consumer<String> output, int capacity) {
        if (recorder == null || output == null || capacity < 1) {
            throw new IllegalArgumentException("recorder, output, and positive capacity are required");
        }
        this.recorder = recorder;
        this.output = output;
        pending = new ArrayBlockingQueue<>(capacity);
        worker = new Thread(this::run, "stackframe-client-diagnostic-worker");
        worker.setDaemon(true);
        worker.start();
    }

    /** Offer at most one observation per live throwable identity, without waiting for I/O or UI. */
    public synchronized boolean accept(Throwable throwable, ClientFailurePhase phase) {
        if (throwable == null || phase == null || !accepting) {
            dropped.incrementAndGet();
            return false;
        }
        for (var iterator = acceptedIdentities.iterator(); iterator.hasNext();) {
            var previous = iterator.next().get();
            if (previous == null) {
                iterator.remove();
            } else if (previous == throwable) {
                duplicates.incrementAndGet();
                return false;
            }
        }
        if (!pending.offer(new Observation(throwable, phase))) {
            dropped.incrementAndGet();
            return false;
        }
        if (acceptedIdentities.size() == IDENTITY_LIMIT) {
            acceptedIdentities.removeFirst();
        }
        acceptedIdentities.addLast(new WeakReference<>(throwable));
        accepted.incrementAndGet();
        return true;
    }

    public Stats stats() {
        return new Stats(accepted.get(), processed.get(), duplicates.get(),
                dropped.get(), processingFailures.get());
    }

    private void run() {
        while (accepting || !pending.isEmpty()) {
            try {
                var observation = pending.poll(100, TimeUnit.MILLISECONDS);
                if (observation != null) {
                    process(observation);
                }
            } catch (InterruptedException ignored) {
                // An interrupt must not consume or replace an original failure.
            }
        }
    }

    private void process(Observation observation) {
        try {
            var record = recorder.record(observation.throwable());
            var generic = CanonicalDiagnosticRegistry.snapshot().genericFallback();
            var notes = new ArrayList<Note>();
            notes.add(safeNote(observation.phase().safeDescription()));
            if (record.failure().isPresent()) {
                notes.add(safeNote(
                        "Stackframe could not save the full trace; inspect the original client log or crash report."));
            }
            var root = new Diagnostic(
                    Severity.ERROR,
                    generic.code(),
                    generic.title(),
                    BoundedList.empty(),
                    BoundedList.of(notes),
                    BoundedList.empty(),
                    record.summary(Optional.empty(), 0, 0, 0),
                    BoundedList.empty(),
                    ConfidenceReference.unassessed(),
                    BoundedList.empty(),
                    BoundedList.empty());
            var document = new DiagnosticDocument(
                    SchemaVersion.CURRENT,
                    new DiagnosticId("D" + record.correlationId().value()),
                    record.correlationId(),
                    root,
                    BoundedList.empty(),
                    BoundedList.empty());
            output.accept(DiagnosticRenderer.renderToString(
                    document, RenderOptions.plain(RenderWidth.unknown())));
            processed.incrementAndGet();
        } catch (Throwable ignored) {
            processingFailures.incrementAndGet();
            try {
                output.accept("[Stackframe] client diagnostic failed; inspect the original client log or crash report\n");
            } catch (Throwable alsoIgnored) {
                // No Stackframe output can be guaranteed; the original route continues.
            }
        }
    }

    private static Note safeNote(String text) {
        return new Note(NoteKind.NOTE,
                DisplayText.visible(text, TextOrigin.GENERATED), BoundedList.empty());
    }

    private static void logDiagnostic(String rendered) {
        DIAGNOSTIC_LOGGER.error(rendered.stripTrailing());
    }

    @Override
    public void close() {
        synchronized (this) {
            accepting = false;
        }
        if (Thread.currentThread() == worker) {
            return;
        }
        try {
            worker.join(SHUTDOWN_WAIT_MILLIS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    public record Stats(long accepted, long processed, long duplicates,
            long dropped, long processingFailures) {
    }

    private record Observation(Throwable throwable, ClientFailurePhase phase) {
    }
}
