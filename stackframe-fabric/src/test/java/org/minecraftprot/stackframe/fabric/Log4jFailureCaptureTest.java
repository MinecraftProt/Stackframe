package org.minecraftprot.stackframe.fabric;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.DefaultConfiguration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.junit.jupiter.api.Test;

class Log4jFailureCaptureTest {
    @Test
    void earlyAndRuntimeFailuresReachSinkOnceWhileOriginalLogsStayIntact() {
        var original = new CollectingAppender("original");
        try (var context = configuredContext("early-runtime", original)) {
            var captured = new ArrayList<Throwable>();
            try (var observer = Log4jFailureCapture.install(context, captured::add)) {
                var logger = context.getLogger("fixture.server");
                var startup = new IllegalStateException("startup");
                var runtime = new IllegalArgumentException("runtime");
                logger.info("ordinary");
                logger.error("failed to start", startup);
                logger.error("runtime failure", runtime);
                logger.warn("warning with throwable", new Exception("not severe"));

                assertEquals(List.of(startup, runtime), captured);
                assertEquals(4, original.events.size());
                assertEquals("ordinary", original.events.get(0).getMessage().getFormattedMessage());
                assertSame(startup, original.events.get(1).getThrown());
                assertSame(runtime, original.events.get(2).getThrown());
                assertEquals(2, observer.stats().delivered());
            }
            context.getLogger("fixture.server").error("after shutdown", new Exception());
            assertEquals(5, original.events.size());
            assertEquals(2, captured.size());
        }
    }

    @Test
    void sinkFailureAndRecursiveLoggingNeverHideOriginalEvents() {
        var original = new CollectingAppender("original");
        try (var context = configuredContext("failure-recursion", original)) {
            var calls = new AtomicInteger();
            try (var observer = Log4jFailureCapture.install(context, throwable -> {
                calls.incrementAndGet();
                context.getLogger("fixture.nested")
                        .error("nested", new IllegalStateException("nested"));
                throw new IllegalStateException("observer failed");
            })) {
                var outer = new IllegalStateException("outer");
                context.getLogger("fixture.outer").error("original", outer);
                assertEquals(1, calls.get());
                assertEquals(2, original.events.size());
                assertTrue(original.events.stream().anyMatch(event -> event.getThrown() == outer));
                // Log4j's AppenderControl guards the nested call before the observer is invoked.
                assertEquals(0, observer.stats().recursiveSuppressed());
                assertEquals(1, observer.stats().sinkFailures());
            }
        }
    }

    @Test
    void reconfigurationAndNonAdditiveLoggerRetainOtherAppenders() {
        var first = new CollectingAppender("first");
        try (var context = configuredContext("reconfigure", first)) {
            var captured = new ArrayList<Throwable>();
            try (var observer = Log4jFailureCapture.install(context, captured::add)) {
                var second = new CollectingAppender("second");
                var replacement = configuration(second);
                var isolated = new CollectingAppender("isolated-original");
                isolated.start();
                replacement.addAppender(isolated);
                var isolatedLogger = new LoggerConfig("fixture.isolated", Level.INFO, false);
                isolatedLogger.addAppender(isolated, null, null);
                replacement.addLogger("fixture.isolated", isolatedLogger);
                context.start(replacement);
                assertTrue(replacement.getAppender("StackframeFailureObserver").isStarted());

                var rootFailure = new Exception("root");
                var isolatedFailure = new Exception("isolated");
                context.getLogger("fixture.other").error("root", rootFailure);
                context.getLogger("fixture.isolated").error("isolated", isolatedFailure);
                assertEquals(List.of(rootFailure, isolatedFailure), captured);
                assertEquals(1, second.events.size());
                assertEquals(1, isolated.events.size());
                assertEquals(2, observer.stats().delivered());
            }
        }
    }

    private static LoggerContext configuredContext(String name, CollectingAppender original) {
        var context = new LoggerContext(name);
        context.start(configuration(original));
        return context;
    }

    private static DefaultConfiguration configuration(CollectingAppender original) {
        var configuration = new DefaultConfiguration();
        var root = configuration.getRootLogger();
        for (var name : List.copyOf(root.getAppenders().keySet())) {
            root.removeAppender(name);
        }
        original.start();
        configuration.addAppender(original);
        root.addAppender(original, null, null);
        root.setLevel(Level.INFO);
        return configuration;
    }

    private static final class CollectingAppender extends AbstractAppender {
        private final List<LogEvent> events = new ArrayList<>();

        private CollectingAppender(String name) {
            super(name, null, null, true);
        }

        @Override
        public void append(LogEvent event) {
            events.add(event.toImmutable());
        }
    }
}
