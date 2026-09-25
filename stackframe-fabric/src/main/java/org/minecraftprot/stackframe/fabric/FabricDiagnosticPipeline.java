package org.minecraftprot.stackframe.fabric;

import java.io.PrintStream;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.minecraftprot.stackframe.diagnostic.BoundedList;
import org.minecraftprot.stackframe.diagnostic.ConfidenceReference;
import org.minecraftprot.stackframe.diagnostic.Diagnostic;
import org.minecraftprot.stackframe.diagnostic.DiagnosticDocument;
import org.minecraftprot.stackframe.diagnostic.DiagnosticId;
import org.minecraftprot.stackframe.diagnostic.SchemaVersion;
import org.minecraftprot.stackframe.diagnostic.Severity;
import org.minecraftprot.stackframe.diagnostic.registry.CanonicalDiagnosticRegistry;
import org.minecraftprot.stackframe.renderer.DiagnosticRenderer;
import org.minecraftprot.stackframe.renderer.RenderOptions;
import org.minecraftprot.stackframe.renderer.RenderWidth;
import org.minecraftprot.stackframe.trace.TraceRecorder;

/**
 * Bounded handoff from Log4j's logging thread to the initial generic diagnostic
 * path. The original Log4j event is never consumed or queued by this class.
 */
public final class FabricDiagnosticPipeline implements AutoCloseable {
    private static final int DEFAULT_CAPACITY = 128;
    private static final long SHUTDOWN_WAIT_MILLIS = 2_000;

    private final ArrayBlockingQueue<Throwable> pending;
    private final TraceRecorder recorder;
    private final PrintStream output;
    private final Thread worker;
    private final AtomicLong accepted = new AtomicLong();
    private final AtomicLong processed = new AtomicLong();
    private final AtomicLong dropped = new AtomicLong();
    private final AtomicLong processingFailures = new AtomicLong();
    private volatile boolean accepting = true;

    public FabricDiagnosticPipeline() {
        this(new TraceRecorder(TraceRecorder.defaultDirectory()), System.err, DEFAULT_CAPACITY);
    }

    FabricDiagnosticPipeline(TraceRecorder recorder, PrintStream output, int capacity) {
        if (recorder == null || output == null || capacity < 1) {
            throw new IllegalArgumentException("recorder, output, and positive capacity are required");
        }
        this.recorder = recorder;
        this.output = output;
        pending = new ArrayBlockingQueue<>(capacity);
        worker = new Thread(this::run, "stackframe-diagnostic-worker");
        worker.setDaemon(true);
        worker.start();
    }

    /** Never blocks a Log4j appender on trace storage or rendering. */
    public synchronized void accept(Throwable throwable) {
        if (throwable == null) {
            throw new IllegalArgumentException("throwable must not be null");
        }
        if (!accepting || !pending.offer(throwable)) {
            dropped.incrementAndGet();
            return;
        }
        accepted.incrementAndGet();
    }

    public PipelineStats stats() {
        return new PipelineStats(
                accepted.get(), processed.get(), dropped.get(), processingFailures.get());
    }

    private void run() {
        while (accepting || !pending.isEmpty()) {
            try {
                var throwable = pending.poll(100, TimeUnit.MILLISECONDS);
                if (throwable != null) {
                    process(throwable);
                }
            } catch (InterruptedException ignored) {
                // Closing wakes the worker so it can drain accepted events.
            }
        }
    }

    private void process(Throwable throwable) {
        try {
            var record = recorder.record(throwable);
            var generic = CanonicalDiagnosticRegistry.snapshot().genericFallback();
            var notes = record.failure().isPresent()
                    ? BoundedList.of(List.of(record.failureNote()))
                    : BoundedList.<org.minecraftprot.stackframe.diagnostic.Note>empty();
            var root = new Diagnostic(
                    Severity.ERROR,
                    generic.code(),
                    generic.title(),
                    BoundedList.empty(),
                    notes,
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
            var rendered = DiagnosticRenderer.renderToString(
                    document, RenderOptions.plain(RenderWidth.unknown()));
            synchronized (output) {
                output.print(rendered);
                output.flush();
                if (output.checkError()) {
                    processingFailures.incrementAndGet();
                }
            }
            processed.incrementAndGet();
        } catch (Throwable ignored) {
            processingFailures.incrementAndGet();
            // The original event has already continued through normal appenders.
            synchronized (output) {
                output.println("[Stackframe] diagnostic failed; inspect the original server log");
            }
        }
    }

    @Override
    public void close() {
        synchronized (this) {
            accepting = false;
        }
        worker.interrupt();
        try {
            worker.join(SHUTDOWN_WAIT_MILLIS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    public record PipelineStats(
            long accepted,
            long processed,
            long dropped,
            long processingFailures) {
    }
}
