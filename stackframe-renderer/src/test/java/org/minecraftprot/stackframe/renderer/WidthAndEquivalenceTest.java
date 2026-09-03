package org.minecraftprot.stackframe.renderer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class WidthAndEquivalenceTest {
    @Test
    void ansiStrippingExactlyMatchesPlainOutput() {
        for (var document : List.of(
                RendererFixtures.minimum(),
                RendererFixtures.full(),
                RendererFixtures.unicodeExcerpt(),
                RendererFixtures.omissionsAndWriteFailure())) {
            for (var width : List.of(
                    RenderWidth.known(39),
                    RenderWidth.known(40),
                    RenderWidth.known(79),
                    RenderWidth.unknown(),
                    RenderWidth.known(100))) {
                var plain = DiagnosticRenderer.renderToString(
                        document, RenderOptions.plain(width));
                var ansi = DiagnosticRenderer.renderToString(
                        document, RenderOptions.ansi(width));

                assertEquals(plain, AnsiText.stripStyling(ansi));
                assertFalse(plain.contains("\u001B"));
                assertTrue(ansi.contains("\u001B[1m"));
            }
        }
    }

    @Test
    void knownUnknownAndMaximumWidthPoliciesAreDeterministic() {
        var document = RendererFixtures.widthFixture();
        var unknown = render(document, RenderWidth.unknown());

        assertEquals(unknown, render(document, RenderWidth.known(80)));
        assertEquals(
                render(document, RenderWidth.known(100)),
                render(document, RenderWidth.known(120)));
        assertEquals(render(document, RenderWidth.known(40)), render(document, RenderWidth.known(40)));
        assertFalse(render(document, RenderWidth.known(39)).isBlank());
        assertFalse(render(document, RenderWidth.known(79)).isBlank());
    }

    @Test
    void narrowLayoutsWrapFactsWithoutSplittingMachineTokens() {
        var output = render(RendererFixtures.widthFixture(), RenderWidth.known(40));

        assertTrue(output.contains("help:\n"));
        assertTrue(output.contains("mods/example-addon.jar"));
        assertTrue(output.contains("record02"));
        assertTrue(output.contains("correlation\n  DEF456"));
    }

    @Test
    void longIndivisibleTokensMayExceedWidthButRemainIntact() {
        var token = "exact-machine-token-" + "x".repeat(90);
        var output = render(RendererFixtures.title(token), RenderWidth.known(40));

        assertTrue(output.contains(token));
        assertTrue(output.lines().anyMatch(line -> line.length() > 40));
    }

    @Test
    void quotedValuesVersionsAndConfigurationKeysRemainIntact() {
        var title = "check \"hello world\" and ('hello beautiful world') "
                + "key='another quoted value' without splitting don't "
                + "version 3.10.2 configuration_key";
        var output = render(RendererFixtures.title(title), RenderWidth.known(24));

        assertTrue(output.contains("\"hello world\""));
        assertTrue(output.contains("'hello beautiful world'"));
        assertTrue(output.contains("'another quoted value'"));
        assertTrue(output.contains("don't"));
        assertTrue(output.contains("3.10.2"));
        assertTrue(output.contains("configuration_key"));
    }

    private static String render(
            org.minecraftprot.stackframe.diagnostic.DiagnosticDocument document,
            RenderWidth width) {
        return DiagnosticRenderer.renderToString(document, RenderOptions.plain(width));
    }
}
