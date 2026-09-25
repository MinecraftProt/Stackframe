package org.minecraftprot.stackframe.fabric;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.AbstractConfiguration;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;

/**
 * Observes throwable-bearing ERROR/FATAL events without consuming or changing
 * the original Log4j event. This adapter owns every Log4j type in the capture
 * path; the sink should only enqueue work and must never format synchronously.
 */
public final class Log4jFailureCapture implements AutoCloseable {
    private static final String APPENDER_NAME = "StackframeFailureObserver";

    private final LoggerContext context;
    private final Consumer<Throwable> sink;
    private final Counters counters = new Counters();
    private final PropertyChangeListener configurationListener;
    private final Map<Configuration, ObserverAppender> attached = new IdentityHashMap<>();
    private final AtomicLong installationFailures = new AtomicLong();
    private volatile boolean closed;

    private Log4jFailureCapture(LoggerContext context, Consumer<Throwable> sink) {
        this.context = Objects.requireNonNull(context, "context");
        this.sink = Objects.requireNonNull(sink, "sink");
        configurationListener = this::onConfigurationChanged;
    }

    public static Log4jFailureCapture install(
            LoggerContext context, Consumer<Throwable> sink) {
        var capture = new Log4jFailureCapture(context, sink);
        try {
            capture.attach(context.getConfiguration());
            context.addPropertyChangeListener(capture.configurationListener);
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

    public CaptureStats stats() {
        return new CaptureStats(
                counters.delivered.get(),
                counters.recursive.get(),
                counters.sinkFailures.get(),
                installationFailures.get());
    }

    private void onConfigurationChanged(PropertyChangeEvent change) {
        if (!LoggerContext.PROPERTY_CONFIG.equals(change.getPropertyName())
                || !(change.getNewValue() instanceof Configuration configuration)) {
            return;
        }
        try {
            attach(configuration);
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
            configuration.getRootLogger().removeAppender(APPENDER_NAME);
            for (var logger : configuration.getLoggers().values()) {
                if (!logger.isAdditive()) {
                    logger.removeAppender(APPENDER_NAME);
                }
            }
            if (configuration instanceof AbstractConfiguration mutable) {
                mutable.removeAppender(APPENDER_NAME);
            }
            appender.stop();
            throw failure;
        }
    }

    private void addIfAbsent(LoggerConfig logger, ObserverAppender appender) {
        if (logger.getAppenders().get(APPENDER_NAME) != appender) {
            logger.addAppender(appender, Level.ERROR, null);
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
            configuration.getRootLogger().removeAppender(APPENDER_NAME);
            for (var logger : configuration.getLoggers().values()) {
                if (!logger.isAdditive()) {
                    logger.removeAppender(APPENDER_NAME);
                }
            }
            if (configuration instanceof AbstractConfiguration mutable) {
                mutable.removeAppender(APPENDER_NAME);
            }
            entry.getValue().stop();
        }
        context.updateLoggers();
    }

    public record CaptureStats(
            long delivered,
            long recursiveSuppressed,
            long sinkFailures,
            long installationFailures) {
    }

    private static final class Counters {
        private final AtomicLong delivered = new AtomicLong();
        private final AtomicLong recursive = new AtomicLong();
        private final AtomicLong sinkFailures = new AtomicLong();
    }

    private static final class ObserverAppender extends AbstractAppender {
        private final Consumer<Throwable> sink;
        private final Counters counters;
        private final ThreadLocal<Boolean> active = ThreadLocal.withInitial(() -> false);

        private ObserverAppender(Consumer<Throwable> sink, Counters counters) {
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
                sink.accept(throwable);
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
