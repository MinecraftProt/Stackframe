package org.minecraftprot.stackframe.fabric;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.minecraftprot.stackframe.correlation.DiagnosticCorrelator;
import org.minecraftprot.stackframe.correlation.DiagnosticCorrelator.Config;
import org.minecraftprot.stackframe.correlation.DiagnosticCorrelator.Disposition;
import org.minecraftprot.stackframe.correlation.DiagnosticCorrelator.Importance;
import org.minecraftprot.stackframe.correlation.DiagnosticCorrelator.RepeatSummary;
import org.minecraftprot.stackframe.diagnostic.BoundedList;
import org.minecraftprot.stackframe.diagnostic.ConfidenceReference;
import org.minecraftprot.stackframe.diagnostic.Diagnostic;
import org.minecraftprot.stackframe.diagnostic.DiagnosticCode;
import org.minecraftprot.stackframe.diagnostic.DiagnosticDocument;
import org.minecraftprot.stackframe.diagnostic.DiagnosticId;
import org.minecraftprot.stackframe.diagnostic.SchemaVersion;
import org.minecraftprot.stackframe.diagnostic.Severity;
import org.minecraftprot.stackframe.diagnostic.registry.CanonicalDiagnosticRegistry;
import org.minecraftprot.stackframe.renderer.DiagnosticRenderer;
import org.minecraftprot.stackframe.renderer.RenderOptions;
import org.minecraftprot.stackframe.renderer.RenderWidth;
import org.minecraftprot.stackframe.fabric.config.StackframeConfiguration;
import org.minecraftprot.stackframe.trace.TraceRecorder;
import org.minecraftprot.stackframe.trace.TraceRetention;

/**
 * Bounded handoff from Log4j's logging thread to the initial generic diagnostic
 * path. The original Log4j event is never consumed or queued by this class.
 */
public final class FabricDiagnosticPipeline implements AutoCloseable {
    private static final int DEFAULT_CAPACITY = 128;
    private static final long SHUTDOWN_WAIT_MILLIS = 2_000;
    private static final Logger DIAGNOSTIC_LOGGER =
            LogManager.getLogger("org.minecraftprot.stackframe.fabric.Diagnostic");
    private static final DiagnosticCode GENERIC_CODE = new DiagnosticCode("SF0001");

    private final ArrayBlockingQueue<Observation> pending;
    private final TraceRecorder recorder;
    private final DiagnosticCorrelator correlator;
    private final StackframeConfiguration configuration;
    private final TraceRetention retention;
    private final RenderOptions renderOptions;
    private final Consumer<String> output;
    private final Thread worker;
    private final AtomicLong accepted = new AtomicLong();
    private final AtomicLong processed = new AtomicLong();
    private final AtomicLong dropped = new AtomicLong();
    private final AtomicLong processingFailures = new AtomicLong();
    private final AtomicLong suppressed = new AtomicLong();
    private final AtomicLong repeatSummaries = new AtomicLong();
    private final AtomicLong filtered = new AtomicLong();
    private volatile boolean accepting = true;

    public FabricDiagnosticPipeline() {
        this(StackframeConfiguration.DEFAULT, Path.of(""));
    }

    public FabricDiagnosticPipeline(Config correlationConfig) {
        this(new TraceRecorder(TraceRecorder.defaultDirectory()),
                FabricDiagnosticPipeline::logDiagnostic, DEFAULT_CAPACITY,
                withCorrelationConfig(correlationConfig));
    }

    public FabricDiagnosticPipeline(StackframeConfiguration configuration, Path serverDirectory) {
        this(configuration, serverDirectory, FabricDiagnosticPipeline::logDiagnostic);
    }

    FabricDiagnosticPipeline(
            StackframeConfiguration configuration, Path serverDirectory,
            Consumer<String> output) {
        this(new TraceRecorder(resolveTraceDirectory(configuration, serverDirectory)),
                output, DEFAULT_CAPACITY, configuration);
    }

    FabricDiagnosticPipeline(TraceRecorder recorder, PrintStream output, int capacity) {
        this(recorder, rendered -> {
            output.print(rendered);
            if (output.checkError()) {
                throw new IllegalStateException("diagnostic output failed");
            }
        }, capacity, StackframeConfiguration.DEFAULT);
    }

    FabricDiagnosticPipeline(TraceRecorder recorder, Consumer<String> output, int capacity) {
        this(recorder, output, capacity, StackframeConfiguration.DEFAULT);
    }

    FabricDiagnosticPipeline(
            TraceRecorder recorder, Consumer<String> output, int capacity,
            Config correlationConfig) {
        this(recorder, output, capacity, withCorrelationConfig(correlationConfig));
    }

    FabricDiagnosticPipeline(
            TraceRecorder recorder, Consumer<String> output, int capacity,
            StackframeConfiguration configuration) {
        if (recorder == null || output == null || configuration == null || capacity < 1) {
            throw new IllegalArgumentException("recorder, output, and positive capacity are required");
        }
        this.recorder = recorder;
        this.configuration = configuration;
        this.correlator = new DiagnosticCorrelator(configuration.deduplication());
        this.retention = new TraceRetention(recorder.directory(), configuration.retention());
        this.renderOptions = configuration.output() == StackframeConfiguration.Output.ANSI
                ? RenderOptions.ansi(RenderWidth.unknown())
                : RenderOptions.plain(RenderWidth.unknown());
        this.output = output;
        if (configuration.retention().automatic()
                && Files.exists(recorder.directory(), LinkOption.NOFOLLOW_LINKS)) {
            publishRetention(retention.clean());
        }
        pending = new ArrayBlockingQueue<>(capacity);
        worker = new Thread(this::run, "stackframe-diagnostic-worker");
        worker.setDaemon(true);
        worker.start();
    }

    /** Never blocks a Log4j appender on trace storage or rendering. */
    public synchronized void accept(Throwable throwable) {
        accept(throwable, Importance.ORDINARY);
    }

    /** Critical events bypass duplicate suppression; original logging stays independent. */
    public synchronized void accept(Throwable throwable, Importance importance) {
        if (throwable == null) {
            throw new IllegalArgumentException("throwable must not be null");
        }
        if (importance == null) {
            throw new IllegalArgumentException("importance must not be null");
        }
        if (!configuration.includes(GENERIC_CODE)) {
            filtered.incrementAndGet();
            return;
        }
        if (!accepting || !pending.offer(new Observation(throwable, importance))) {
            dropped.incrementAndGet();
            return;
        }
        accepted.incrementAndGet();
    }

    public PipelineStats stats() {
        return new PipelineStats(
                accepted.get(), processed.get(), dropped.get(), processingFailures.get(),
                suppressed.get(), repeatSummaries.get(), filtered.get());
    }

    private void run() {
        while (accepting || !pending.isEmpty()) {
            try {
                var observation = pending.poll(100, TimeUnit.MILLISECONDS);
                if (observation != null) {
                    process(observation);
                }
                publishSummaries(correlator.drainExpired());
            } catch (InterruptedException ignored) {
                // An external interrupt must not skip accepted events.
            }
        }
        publishSummaries(correlator.drainAll());
    }

    private void process(Observation observation) {
        try {
            var decision = correlator.observe(
                    observation.throwable(), recorder.newCorrelationId(),
                    observation.importance());
            publishSummaries(decision.summaries());
            if (decision.disposition() == Disposition.SUPPRESS_DIAGNOSTIC_ONLY) {
                suppressed.incrementAndGet();
                processed.incrementAndGet();
                return;
            }
            var record = recorder.record(observation.throwable(), decision.correlationId());
            if (record.file().isPresent() && configuration.retention().automatic()) {
                publishRetention(retention.clean(record.file().orElseThrow()));
            }
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
                    document, renderOptions);
            output.accept(rendered);
            processed.incrementAndGet();
        } catch (Throwable ignored) {
            processingFailures.incrementAndGet();
            // The original event has already continued through normal appenders.
            try {
                output.accept("[Stackframe] diagnostic failed; inspect the original server log\n");
            } catch (Throwable alsoIgnored) {
                // The normal Log4j event still contains the original failure.
            }
        }
    }

    private void publishSummaries(List<RepeatSummary> summaries) {
        for (var summary : summaries) {
            try {
                output.accept("[Stackframe] Failure " + summary.correlationId().value()
                        + " was observed " + summary.repeatCount()
                        + (summary.repeatCount() == 1 ? " more time" : " more times")
                        + " in the correlation window.\n");
                repeatSummaries.incrementAndGet();
            } catch (Throwable ignored) {
                processingFailures.incrementAndGet();
                // Original Log4j observations remain available to the operator.
            }
        }
    }

    private void publishRetention(TraceRetention.Result result) {
        if (!result.failed()) {
            return;
        }
        try {
            output.accept("[Stackframe] trace retention could not complete ("
                    + result.status() + "); inspect the trace directory.\n");
        } catch (Throwable ignored) {
            processingFailures.incrementAndGet();
        }
    }

    private static StackframeConfiguration withCorrelationConfig(Config correlationConfig) {
        var defaults = StackframeConfiguration.DEFAULT;
        return new StackframeConfiguration(defaults.output(), defaults.traceDirectory(),
                defaults.retention(), correlationConfig, defaults.includeGeneric(),
                defaults.excludeGeneric());
    }

    private static Path resolveTraceDirectory(
            StackframeConfiguration configuration, Path serverDirectory) {
        if (configuration == null || serverDirectory == null) {
            throw new IllegalArgumentException("configuration and serverDirectory are required");
        }
        var root = serverDirectory.toAbsolutePath().normalize();
        var resolved = root.resolve(configuration.traceDirectory()).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("trace directory must stay inside server directory");
        }
        return resolved;
    }

    private static void logDiagnostic(String rendered) {
        // A single log event lets each existing appender serialize its own output.
        DIAGNOSTIC_LOGGER.error(rendered.stripTrailing());
    }

    @Override
    public void close() {
        synchronized (this) {
            accepting = false;
        }
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
            long processingFailures,
            long suppressed,
            long repeatSummaries,
            long filtered) {
    }

    private record Observation(Throwable throwable, Importance importance) {
    }
}
