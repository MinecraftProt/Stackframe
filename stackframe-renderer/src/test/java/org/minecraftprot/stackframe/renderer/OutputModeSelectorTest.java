package org.minecraftprot.stackframe.renderer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OutputModeSelectorTest {
    @Test
    void autoRequiresAnInteractiveSupportedDestination() {
        assertMode(OutputMode.PLAIN, OutputPreference.AUTO, false, Map.of("TERM", "xterm-256color"));
        assertMode(OutputMode.ANSI, OutputPreference.AUTO, true, Map.of("TERM", "xterm-256color"));
        assertMode(OutputMode.PLAIN, OutputPreference.AUTO, true, Map.of());
        assertMode(OutputMode.PLAIN, OutputPreference.AUTO, true, Map.of("TERM", "unrecognized"));
        assertMode(OutputMode.PLAIN, OutputPreference.AUTO, true, Map.of("TERM", "dumb"));
        assertMode(OutputMode.PLAIN, OutputPreference.AUTO, true, Map.of("TERM", "unknown"));
    }

    @Test
    void noColorAndCiDisableAutomaticStyling() {
        assertMode(OutputMode.PLAIN, OutputPreference.AUTO, true,
                Map.of("TERM", "xterm", "NO_COLOR", "1"));
        assertMode(OutputMode.PLAIN, OutputPreference.AUTO, true,
                Map.of("TERM", "xterm", "NO_COLOR", "0"));
        assertMode(OutputMode.PLAIN, OutputPreference.AUTO, true,
                Map.of("TERM", "xterm", "CI", "true"));
        assertMode(OutputMode.PLAIN, OutputPreference.AUTO, true,
                Map.of("TERM", "xterm", "GITHUB_ACTIONS", "true"));
        assertMode(OutputMode.PLAIN, OutputPreference.AUTO, true,
                Map.of("TERM", "xterm", "CLICOLOR", "0"));
        assertMode(OutputMode.ANSI, OutputPreference.AUTO, true,
                Map.of("TERM", "xterm", "NO_COLOR", "", "CI", "false"));
    }

    @Test
    void knownWindowsSignalsEnableStylingUnlessTerminalExplicitlyDisablesIt() {
        assertMode(OutputMode.ANSI, OutputPreference.AUTO, true, Map.of("WT_SESSION", "id"));
        assertMode(OutputMode.ANSI, OutputPreference.AUTO, true, Map.of("ANSICON", "1"));
        assertMode(OutputMode.ANSI, OutputPreference.AUTO, true, Map.of("ConEmuANSI", "ON"));
        assertMode(OutputMode.PLAIN, OutputPreference.AUTO, true,
                Map.of("WT_SESSION", "id", "TERM", "dumb"));
        assertMode(OutputMode.PLAIN, OutputPreference.AUTO, false, Map.of("WT_SESSION", "id"));
    }

    @Test
    void explicitModesOverrideEveryAutomaticSignal() {
        var unsupported = Map.of("TERM", "dumb", "NO_COLOR", "1", "CI", "true");
        assertMode(OutputMode.ANSI, OutputPreference.ANSI, false, unsupported);
        assertMode(OutputMode.PLAIN, OutputPreference.PLAIN, true,
                Map.of("TERM", "xterm-256color"));
    }

    @Test
    void selectedModesPreserveRendererMeaning() {
        var document = RendererFixtures.minimum();
        var terminal = new TerminalCapabilities(true, Map.of("TERM", "xterm"));
        var redirected = new TerminalCapabilities(false, Map.of("TERM", "xterm"));
        var ansi = DiagnosticRenderer.renderToString(document,
                options(OutputModeSelector.select(OutputPreference.AUTO, terminal)));
        var plain = DiagnosticRenderer.renderToString(document,
                options(OutputModeSelector.select(OutputPreference.AUTO, redirected)));

        assertEquals(plain, AnsiText.stripStyling(ansi));
        assertTrue(ansi.contains("\u001B[1m"));
        assertFalse(plain.contains("\u001B"));
    }

    @Test
    void capabilityEvidenceIsAnImmutableSnapshot() {
        var environment = new HashMap<String, String>();
        environment.put("TERM", "xterm");
        environment.put("PRIVATE_TOKEN", "never-retain");
        var capabilities = new TerminalCapabilities(true, environment);
        environment.put("NO_COLOR", "1");

        assertMode(OutputMode.ANSI, OutputPreference.AUTO, capabilities);
        assertFalse(capabilities.environment().containsKey("PRIVATE_TOKEN"));
        assertThrows(UnsupportedOperationException.class,
                () -> capabilities.environment().put("NO_COLOR", "1"));
        assertThrows(IllegalArgumentException.class,
                () -> OutputModeSelector.select(null, capabilities));
        assertThrows(IllegalArgumentException.class,
                () -> OutputModeSelector.select(OutputPreference.AUTO, null));
    }

    private static RenderOptions options(OutputMode mode) {
        return new RenderOptions(
                mode, RenderWidth.unknown(), AmbiguousWidth.NARROW, RenderLimits.DEFAULT);
    }

    private static void assertMode(
            OutputMode expected,
            OutputPreference preference,
            boolean outputIsTerminal,
            Map<String, String> environment) {
        assertMode(expected, preference, new TerminalCapabilities(outputIsTerminal, environment));
    }

    private static void assertMode(
            OutputMode expected,
            OutputPreference preference,
            TerminalCapabilities capabilities) {
        assertEquals(expected, OutputModeSelector.select(preference, capabilities));
    }
}
