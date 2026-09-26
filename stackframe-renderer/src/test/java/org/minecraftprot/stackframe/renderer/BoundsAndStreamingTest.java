package org.minecraftprot.stackframe.renderer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.StringWriter;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class BoundsAndStreamingTest {
    @Test
    void streamsToAppendableWithoutARequiredIntermediateString() throws IOException {
        var writer = new StringWriter();
        var options = RenderOptions.plain(RenderWidth.unknown());

        DiagnosticRenderer.render(RendererFixtures.full(), writer, options);

        assertEquals(DiagnosticRenderer.renderToString(RendererFixtures.full(), options), writer.toString());
    }

    @Test
    void failsExplicitlyAtByteLineAndWorkBounds() {
        var document = RendererFixtures.full();

        assertThrows(RenderLimitException.class, () -> renderWithLimits(
                document, new RenderLimits(20, 100, 10_000)));
        assertThrows(RenderLimitException.class, () -> renderWithLimits(
                document, new RenderLimits(100_000, 1, 10_000)));
        assertThrows(RenderLimitException.class, () -> renderWithLimits(
                document, new RenderLimits(100_000, 10_000, 1)));
    }

    @Test
    void failsExplicitlyAtElapsedTimeBoundWithoutWaitingForWallClock() {
        var clock = new AtomicLong();
        var output = new StringBuilder();
        var options = new RenderOptions(OutputMode.PLAIN, RenderWidth.unknown(),
                AmbiguousWidth.NARROW,
                new RenderLimits(100_000, 10_000, 10_000, Duration.ofNanos(1)));

        var failure = assertThrows(RenderLimitException.class,
                () -> DiagnosticRenderer.render(RendererFixtures.minimum(), output,
                        options, () -> clock.getAndAdd(2)));

        assertTrue(failure.getMessage().contains("elapsed time limit"));
    }

    @Test
    void rendersPathologicalMaximumTextFixtureWithinDefaultBoundsDeterministically() {
        var options = RenderOptions.plain(RenderWidth.known(40));
        var first = DiagnosticRenderer.renderToString(RendererFixtures.pathological(), options);
        var second = DiagnosticRenderer.renderToString(RendererFixtures.pathological(), options);

        assertEquals(first, second);
        assertTrue(first.length() > 100_000);
        assertTrue(first.contains("correlation"));
        assertTrue(first.endsWith("ABC123\n"));
    }

    @Test
    void rejectsInvalidOptionsAndAnsiInput() {
        assertThrows(IllegalArgumentException.class, () -> RenderWidth.known(0));
        assertThrows(IllegalArgumentException.class, () -> new RenderLimits(0, 1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new RenderLimits(1, 1, 1, Duration.ofMinutes(6)));
        assertThrows(IllegalArgumentException.class, () -> AnsiText.stripStyling("\u001B]0;title"));
        assertThrows(IllegalArgumentException.class, () -> AnsiText.stripStyling("\u001B[2J"));
    }

    private static String renderWithLimits(
            org.minecraftprot.stackframe.diagnostic.DiagnosticDocument document,
            RenderLimits limits) {
        return DiagnosticRenderer.renderToString(
                document,
                new RenderOptions(
                        OutputMode.PLAIN,
                        RenderWidth.known(80),
                        AmbiguousWidth.NARROW,
                        limits));
    }
}
