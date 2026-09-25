package org.minecraftprot.stackframe.fabric;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.AbstractConfiguration;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.minecraftprot.stackframe.correlation.DiagnosticCorrelator.Importance;

/**
 * Observes throwable-bearing ERROR/FATAL events without consuming or changing
 * the original Log4j event. This adapter owns every Log4j type in the capture
 * path; the sink should only enqueue work and must never format synchronously.
 */
public final class Log4jFailureCapture implements AutoCloseable {
    private static final String APPENDER_NAME = "StackframeFailureObserver";

    private final LoggerContext context;
    private final BiConsumer<Throwable, Importance> sink;
    private final Counters counters = new Counters();
    private final PropertyChangeListener configurationListener;
    private final Map<Configuration, ObserverAppender> attached = new IdentityHashMap<>();
    private final AtomicLong installationFailures = new AtomicLong();
    private volatile boolean closed;

    private Log4jFailureCapture(LoggerContext context,
            BiConsumer<Throwable, Importance> sink) {
        this.context = Objects.requireNonNull(context, "context");
        this.sink = Objects.requireNonNull(sink, "sink");
        configurationListener = this::onConfigurationChanged;
    }

    public static Log4jFailureCapture install(
            LoggerContext context, Consumer<Throwable> sink) {
        Objects.requireNonNull(sink, "sink");
        return installWithImportance(
                context, (throwable, importance) -> sink.accept(throwable));
    }

    public static Log4jFailureCapture installWithImportance(
            LoggerContext context, BiConsumer<Throwable, Importance> sink) {
        var capture = new Log4jFailureCapture(context, sink);
        try {
            context.addPropertyChangeListener(capture.configurationListener);
            capture.attach(context.getConfiguration());
            var current = context.getConfiguration();
            capture.attach(current);
            capture.keepOnly(current);
            return capture;
        } catch (RuntimeException | Error failure) {
            try {
                capture.close();
            } catch (RuntimeException | Error cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    public synchronized CaptureStats stats() {
        return new CaptureStats(
                counters.delivered.get(),
                counters.recursive.get(),
                counters.sinkFailures.get(),
                installationFailures.get(),
                attached.size());
    }

    private void onConfigurationChanged(PropertyChangeEvent change) {
        if (!LoggerContext.PROPERTY_CONFIG.equals(change.getPropertyName())
                || !(change.getNewValue() instanceof Configuration configuration)) {
            return;
        }
        try {
            attach(configuration);
            if (context.getConfiguration() == configuration) {
                keepOnly(configuration);
            }
        } catch (Throwable ignored) {
            // Reconfiguration and every original appender continue unchanged.
            installationFailures.incrementAndGet();
        }
    }

    private synchronized void attach(Configuration configuration) {
        if (closed || attached.containsKey(configuration)) {
            return;
        }
        var existing = configuration.getAppender(APPENDER_NAME);
        if (existing != null) {
            throw new IllegalStateException("Stackframe observer appender name is already in use");
        }
        var appender = new ObserverAppender(sink, counters);
        appender.start();
        try {
            configuration.addAppender(appender);
            addIfAbsent(configuration.getRootLogger(), appender);
            for (var logger : configuration.getLoggers().values()) {
                if (!logger.isAdditive()) {
                    addIfAbsent(logger, appender);
                }
            }
            attached.put(configuration, appender);
            context.updateLoggers();
        } catch (RuntimeException | Error failure) {
            removeIfOwned(configuration.getRootLogger(), appender);
            for (var logger : configuration.getLoggers().values()) {
                if (!logger.isAdditive()) {
                    removeIfOwned(logger, appender);
                }
            }
            if (configuration instanceof AbstractConfiguration mutable
                    && configuration.getAppender(APPENDER_NAME) == appender) {
                mutable.removeAppender(APPENDER_NAME);
            }
            appender.stop();
            throw failure;
        }
    }

    private synchronized void keepOnly(Configuration current) {
        // Log4j owns stopping old configurations. Do not retain them forever.
        attached.keySet().removeIf(previous -> previous != current);
    }

    private void addIfAbsent(LoggerConfig logger, ObserverAppender appender) {
        var existing = logger.getAppenders().get(APPENDER_NAME);
        if (existing != null && existing != appender) {
            throw new IllegalStateException("Stackframe observer appender name is already in use");
        }
        if (existing == null) {
            logger.addAppender(appender, Level.ERROR, null);
        }
    }

    private static void removeIfOwned(LoggerConfig logger, ObserverAppender appender) {
        if (logger.getAppenders().get(APPENDER_NAME) == appender) {
            logger.removeAppender(APPENDER_NAME);
        }
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        context.removePropertyChangeListener(configurationListener);
        for (var entry : attached.entrySet()) {
            var configuration = entry.getKey();
            var appender = entry.getValue();
            removeIfOwned(configuration.getRootLogger(), appender);
            for (var logger : configuration.getLoggers().values()) {
                if (!logger.isAdditive()) {
                    removeIfOwned(logger, appender);
                }
            }
            if (configuration instanceof AbstractConfiguration mutable
                    && configuration.getAppender(APPENDER_NAME) == appender) {
                mutable.removeAppender(APPENDER_NAME);
            }
            appender.stop();
        }
        context.updateLoggers();
    }

    public record CaptureStats(
            long delivered,
            long recursiveSuppressed,
            long sinkFailures,
            long installationFailures,
            int activeConfigurations) {
    }

    private static final class Counters {
        private final AtomicLong delivered = new AtomicLong();
        private final AtomicLong recursive = new AtomicLong();
        private final AtomicLong sinkFailures = new AtomicLong();
    }

    private static final class ObserverAppender extends AbstractAppender {
        private final BiConsumer<Throwable, Importance> sink;
        private final Counters counters;
        private final ThreadLocal<Boolean> active = ThreadLocal.withInitial(() -> false);

        private ObserverAppender(BiConsumer<Throwable, Importance> sink, Counters counters) {
            super(APPENDER_NAME, null, null, true);
            this.sink = sink;
            this.counters = counters;
        }

        @Override
        public void append(LogEvent event) {
            if (Boolean.TRUE.equals(active.get())) {
                counters.recursive.incrementAndGet();
                return;
            }
            active.set(true);
            try {
                var loggerName = event.getLoggerName();
                var throwable = event.getThrown();
                if (!event.getLevel().isMoreSpecificThan(Level.ERROR)
                        || throwable == null
                        || (loggerName != null
                                && loggerName.startsWith("org.minecraftprot.stackframe"))) {
                    return;
                }
                sink.accept(throwable, event.getLevel() == Level.FATAL
                        ? Importance.CRITICAL : Importance.ORDINARY);
                counters.delivered.incrementAndGet();
            } catch (Throwable ignored) {
                // A failed observer must not prevent the remaining original appenders.
                counters.sinkFailures.incrementAndGet();
            } finally {
                active.remove();
            }
        }
    }
}
