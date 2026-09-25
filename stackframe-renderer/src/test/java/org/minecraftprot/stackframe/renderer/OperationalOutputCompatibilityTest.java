package org.minecraftprot.stackframe.renderer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OperationalOutputCompatibilityTest {
    @Test
    void redirectedServiceContainerAndPanelDestinationsReceivePlainRecords()
            throws IOException {
        var document = RendererFixtures.title("failure\nerror[SF9999]: forged");
        var destinations = List.of(
                new Destination("redirected stdout", false, Map.of("TERM", "xterm-256color")),
                new Destination("systemd journal", false, Map.of("TERM", "dumb")),
                new Destination("Docker log stream", false, Map.of("TERM", "xterm")),
                new Destination("hosting panel stream", false, Map.of("TERM", "xterm")),
                new Destination("CI log", true, Map.of("TERM", "xterm", "CI", "true")),
                new Destination("NO_COLOR console", true,
                        Map.of("TERM", "xterm", "NO_COLOR", "1")));

        for (var destination : destinations) {
            var mode = OutputModeSelector.select(OutputPreference.AUTO,
                    new TerminalCapabilities(destination.terminal(), destination.environment()));
            assertEquals(OutputMode.PLAIN, mode, destination.name());
            var bytes = new ByteArrayOutputStream();
            try (var stream = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
                DiagnosticRenderer.render(document, stream,
                        new RenderOptions(mode, RenderWidth.known(80),
                                AmbiguousWidth.NARROW, RenderLimits.DEFAULT));
            }
            var output = bytes.toString(StandardCharsets.UTF_8);
            assertFalse(output.contains("\u001B"), destination.name());
            assertFalse(output.contains("\r"), destination.name());
            assertTrue(output.startsWith("error[SF0001]: failure\\nerror[SF9999]: forged\n"),
                    destination.name());
            assertEquals(1, output.lines().filter(line -> line.startsWith("error[")).count(),
                    destination.name());
        }
    }

    private record Destination(String name, boolean terminal, Map<String, String> environment) {
    }
}
