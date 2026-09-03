package org.minecraftprot.stackframe.renderer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ibm.icu.lang.UCharacter;
import org.junit.jupiter.api.Test;

class UnicodeAndSafetyTest {
    @Test
    void measuresCombiningWideEmojiAndAmbiguousClusters() {
        assertEquals("unicode-17.0-terminal-width-1", UnicodeWidthPolicy.VERSION);
        assertEquals(17, UCharacter.getUnicodeVersion().getMajor());
        assertEquals(1, UnicodeWidthPolicy.measure("e\u0301", AmbiguousWidth.NARROW).columns());
        assertEquals(2, UnicodeWidthPolicy.measure("界", AmbiguousWidth.NARROW).columns());
        var rgiEmoji = UnicodeWidthPolicy.measure("👩🏽‍💻", AmbiguousWidth.NARROW);
        assertEquals(2, rgiEmoji.columns());
        assertTrue(rgiEmoji.certain());
        var englandFlag = UnicodeWidthPolicy.sanitize(
                "🏴󠁧󠁢󠁥󠁮󠁧󠁿", AmbiguousWidth.NARROW, false, 0);
        assertEquals("🏴󠁧󠁢󠁥󠁮󠁧󠁿", englandFlag.value());
        assertEquals(2, englandFlag.columns());
        assertTrue(englandFlag.certain());
        var invalidModifier = UnicodeWidthPolicy.measure("😀🏻", AmbiguousWidth.NARROW);
        assertEquals(2, invalidModifier.columns());
        assertFalse(invalidModifier.certain());
        var emojiVariation = UnicodeWidthPolicy.measure("1\uFE0F", AmbiguousWidth.NARROW);
        assertEquals(2, emojiVariation.columns());
        assertTrue(emojiVariation.certain());
        var pirateFlag = UnicodeWidthPolicy.sanitize(
                "🏴‍☠️", AmbiguousWidth.NARROW, false, 0);
        assertEquals("🏴‍☠️", pirateFlag.value());
        assertEquals(2, pirateFlag.columns());
        assertTrue(pirateFlag.certain());
        var invalidRegionalVariant = UnicodeWidthPolicy.sanitize(
                "🇦\uFE0F", AmbiguousWidth.NARROW, false, 0);
        assertEquals("🇦\\u{FE0F}", invalidRegionalVariant.value());
        assertFalse(invalidRegionalVariant.certain());
        var prependMark = UnicodeWidthPolicy.sanitize(
                "\u0600界", AmbiguousWidth.NARROW, false, 0);
        assertEquals("\\u{0600}界", prependMark.value());
        assertFalse(prependMark.certain());
        assertTrue(UnicodeWidthPolicy.measure(
                "e\u0301 = 界 + 👩🏽‍💻", AmbiguousWidth.NARROW).certain());
        var source = "e\u0301 = 界 + 👩🏽‍💻";
        assertTrue(UnicodeWidthPolicy.isGraphemeBoundary(source, source.offsetByCodePoints(0, 5)));
        assertTrue(UnicodeWidthPolicy.isGraphemeBoundary(source, source.offsetByCodePoints(0, 6)));
        assertEquals(1, UnicodeWidthPolicy.measure("·", AmbiguousWidth.NARROW).columns());
        assertEquals(2, UnicodeWidthPolicy.measure("·", AmbiguousWidth.WIDE).columns());
        assertEquals(1, UnicodeWidthPolicy.measure("↔\uFE0E", AmbiguousWidth.NARROW).columns());
        var ideographicVariant = UnicodeWidthPolicy.sanitize(
                "邊\uDB40\uDD00", AmbiguousWidth.NARROW, false, 0);
        assertEquals("邊\uDB40\uDD00", ideographicVariant.value());
        assertEquals(2, ideographicVariant.columns());
        assertTrue(ideographicVariant.certain());
        var unsupportedVariant = UnicodeWidthPolicy.sanitize(
                "A\uFE0F", AmbiguousWidth.NARROW, false, 0);
        assertEquals("A\\u{FE0F}", unsupportedVariant.value());
        assertFalse(unsupportedVariant.certain());
        var nonRgiEmoji = UnicodeWidthPolicy.sanitize(
                "😀‍😀", AmbiguousWidth.NARROW, false, 0);
        assertFalse(nonRgiEmoji.certain());
        assertEquals(0, UnicodeWidthPolicy.measure("\u1ACF", AmbiguousWidth.NARROW).columns());
    }

    @Test
    void neutralizesControlsNewlinesTabsBidiAndUnsupportedJoinersBeforeMeasuring() {
        var hostile = "bad\u001B[2J\nerror[SF9999]: forged\t\u202E\u200D";
        var sanitized = UnicodeWidthPolicy.sanitize(
                hostile, AmbiguousWidth.NARROW, false, 0);

        assertEquals(
                "bad\\u{001B}[2J\\nerror[SF9999]: forged\\t\\u{202E}\\u{200D}",
                sanitized.value());
        assertFalse(sanitized.value().contains("\u001B"));
        assertFalse(sanitized.value().contains("\n"));
        assertFalse(sanitized.certain());
    }

    @Test
    void expandsExcerptTabsAtFourColumnStops() {
        var sanitized = UnicodeWidthPolicy.sanitize(
                "a\tb", AmbiguousWidth.NARROW, true, 0);

        assertEquals("a   b", sanitized.value());
        assertEquals(5, sanitized.columns());
    }

    @Test
    void outputUsesLfAndContainsNoForbiddenPlainControlsOrTrailingSpaces() {
        var output = DiagnosticRenderer.renderToString(
                RendererFixtures.full(), RenderOptions.plain(RenderWidth.known(100)));

        assertFalse(output.contains("\r"));
        assertFalse(output.contains("\t"));
        assertFalse(output.contains("\u001B"));
        for (var line : output.split("\n", -1)) {
            assertFalse(line.endsWith(" "));
        }
        assertTrue(output.endsWith("\n"));
    }

    @Test
    void embeddedModelNewlineCannotForgeAnotherDiagnosticRecord() {
        var output = DiagnosticRenderer.renderToString(
                RendererFixtures.title("failed\nerror[SF9999]: forged"),
                RenderOptions.plain(RenderWidth.known(80)));

        assertTrue(output.startsWith("error[SF0001]: failed\\nerror[SF9999]: forged\n"));
        assertEquals(1, output.lines().filter(line -> line.startsWith("error[")).count());
    }

    @Test
    void uncertainClustersUseLinearExcerptAnnotations() {
        var output = DiagnosticRenderer.renderToString(
                RendererFixtures.uncertainExcerpt(),
                RenderOptions.plain(RenderWidth.known(80)));

        assertTrue(output.contains("context line 3: a\\u{200D}b"));
        assertTrue(output.contains("annotation line 3 columns 1-2 primary: uncertain cluster"));
        assertFalse(output.contains("^"));
    }

    @Test
    void blankAndWhitespaceOnlyExcerptLinesRemainRenderableAtTinyWidths() {
        var output = DiagnosticRenderer.renderToString(
                RendererFixtures.blankExcerpt(),
                RenderOptions.plain(RenderWidth.known(5)));

        assertTrue(output.contains("context line 1:\n"));
        assertTrue(output.contains("context line 2:\n  value\\u{0020}\n"));
        assertTrue(output.contains("context line 3:\n"));
    }

    @Test
    void labelsThatSplitGraphemeClustersUseLinearAnnotations() {
        var output = DiagnosticRenderer.renderToString(
                RendererFixtures.splitGraphemeLabel(),
                RenderOptions.plain(RenderWidth.known(80)));

        assertTrue(output.contains("annotation line 4 columns 2-3 primary: combining mark boundary"));
        assertFalse(output.contains("^"));
    }

    @Test
    void trailingWhitespaceInOrdinaryValuesIsVisibleAndCannotCrashRendering() {
        var output = DiagnosticRenderer.renderToString(
                RendererFixtures.location("server startup "),
                RenderOptions.plain(RenderWidth.known(80)));

        assertTrue(output.contains("location: server startup\\u{0020}\n"));
    }
}
