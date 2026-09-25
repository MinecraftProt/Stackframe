package org.minecraftprot.stackframe.fabric;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.OutputStreamAppender;
import org.apache.logging.log4j.core.config.DefaultConfiguration;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.junit.jupiter.api.Test;

class OperationalLog4jCompatibilityTest {
    @Test
    void customPatternAndTwoOutputAppendersKeepTypedErrorsAndFullOriginalTraces() {
        var file = new ByteArrayOutputStream();
        var secondary = new ByteArrayOutputStream();
        var configuration = configuration(file, secondary);
        try (var context = new LoggerContext("operational-compatibility")) {
            context.start(configuration);
            var captured = new ArrayList<Throwable>();
            try (var observer = Log4jFailureCapture.install(context, captured::add)) {
                var logger = context.getLogger("fixture.server");
                logger.error("java.lang.IllegalStateException: rendered text only");

                var original = new IllegalStateException("original low-level detail");
                original.setStackTrace(new StackTraceElement[] {
                    new StackTraceElement("example.Server", "run", "Server.java", 42)
                });
                var crashReportBefore = stackTrace(original);
                logger.error("typed failure", original);
                assertEquals(crashReportBefore, stackTrace(original),
                        "observing Log4j must not alter the throwable used by crash reports");

                assertEquals(1, captured.size());
                assertSame(original, captured.getFirst());
                assertEquals(1, observer.stats().delivered());
                assertEquals(0, observer.stats().sinkFailures());
            }
            context.getLogger("fixture.server")
                    .error("after observer closed", new IllegalArgumentException("still logged"));
            assertEquals(1, captured.size(), "closed observer must not receive later events");
            var fileText = file.toString(StandardCharsets.UTF_8);
            var secondaryText = secondary.toString(StandardCharsets.UTF_8);
            assertEquals(fileText, secondaryText, "both pre-existing destinations receive events");
            assertTrue(fileText.contains("rendered text only"));
            assertTrue(fileText.contains("ERROR typed failure"));
            assertTrue(fileText.contains("java.lang.IllegalStateException: original low-level detail"));
            assertTrue(fileText.contains("example.Server.run(Server.java:42)"));
            assertTrue(fileText.contains("after observer closed"));
            assertFalse(fileText.contains("\u001B"));
            assertEquals(1, fileText.split("ERROR typed failure", -1).length - 1);
            assertTrue(configuration.getAppender("primary-stream").isStarted());
            assertTrue(configuration.getAppender("secondary-stream").isStarted());
        }
    }

    private static DefaultConfiguration configuration(
            ByteArrayOutputStream file, ByteArrayOutputStream secondary) {
        var configuration = new DefaultConfiguration();
        var root = configuration.getRootLogger();
        for (var name : List.copyOf(root.getAppenders().keySet())) {
            root.removeAppender(name);
        }
        addOutput(configuration, "primary-stream", file);
        addOutput(configuration, "secondary-stream", secondary);
        root.setLevel(Level.INFO);
        return configuration;
    }

    private static void addOutput(
            DefaultConfiguration configuration, String name, ByteArrayOutputStream target) {
        var layout = PatternLayout.newBuilder()
                .withConfiguration(configuration)
                .withPattern("%level %msg%n%throwable{full}")
                .build();
        var appender = OutputStreamAppender.newBuilder()
                .setName(name)
                .setConfiguration(configuration)
                .setLayout(layout)
                .setTarget(target)
                .build();
        appender.start();
        configuration.addAppender(appender);
        configuration.getRootLogger().addAppender(appender, null, null);
    }

    private static String stackTrace(Throwable throwable) {
        var text = new StringWriter();
        throwable.printStackTrace(new PrintWriter(text));
        return text.toString();
    }
}
