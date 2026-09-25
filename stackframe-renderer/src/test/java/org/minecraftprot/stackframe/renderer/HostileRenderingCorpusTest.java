package org.minecraftprot.stackframe.renderer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import org.minecraftprot.stackframe.diagnostic.DiagnosticValidationException;
import org.minecraftprot.stackframe.testkit.HostileInputCorpus;

class HostileRenderingCorpusTest {
    private static final Instant OBSERVED_AT = Instant.parse("2026-09-25T12:34:56Z");
    private static final int[] WIDTHS = {1, 2, 5, 39, 40, 79, 80, 100, 101, 256};

    @Test
    void seededDocumentsStayBoundedAndCannotInjectTerminalOrNdjsonRecords() {
        var baseSeed = HostileInputCorpus.baseSeed();
        var start = HostileInputCorpus.caseStart();
        var end = start + HostileInputCorpus.caseCount();
        for (var index = start; index < end; index++) {
            var seed = HostileInputCorpus.caseSeed(baseSeed, index);
            try {
                assertTimeoutPreemptively(Duration.ofSeconds(5), () -> checkCase(seed));
            } catch (Throwable failure) {
                throw new AssertionError(HostileInputCorpus.failureContext(baseSeed, index),
                        failure);
            }
        }
    }

    @Test
    void minimalMalformedControlAndOversizedValuesAreRejectedBeforeRendering() {
        for (var invalid : new String[] {
                "x\u001B[2J", "x\r", "x\t", "x\u202E", "x\uD800", "x\uDC00"
        }) {
            assertThrows(DiagnosticValidationException.class,
                    () -> RendererFixtures.hostileLocationExcerptAndLabel(
                            invalid, "x", "label"),
                    () -> "path=" + invalid.replace("\u001B", "\\u001B"));
        }
        assertThrows(DiagnosticValidationException.class,
                () -> RendererFixtures.hostileLocationExcerptAndLabel(
                        "x".repeat(1_025), "x", "label"));
        assertThrows(DiagnosticValidationException.class,
                () -> RendererFixtures.hostileLocationExcerptAndLabel(
                        "path", "x\nforged", "label"));
    }

    private static void checkCase(long seed) throws Exception {
        var path = HostileInputCorpus.modelText(seed, 1_024, true);
        var source = HostileInputCorpus.modelText(seed ^ 0x51A7E, 2_048, false);
        var label = HostileInputCorpus.modelText(seed ^ 0x1ABE1, 256, true);
        var document = RendererFixtures.hostileLocationExcerptAndLabel(path, source, label);
        var width = WIDTHS[(int) Long.remainderUnsigned(seed, WIDTHS.length)];
        var plainOptions = RenderOptions.plain(RenderWidth.known(width));
        var ansiOptions = RenderOptions.ansi(RenderWidth.known(width));
        var plain = DiagnosticRenderer.renderToString(document, plainOptions);
        var ansi = DiagnosticRenderer.renderToString(document, ansiOptions);

        assertEquals(plain, DiagnosticRenderer.renderToString(document, plainOptions));
        assertEquals(plain, AnsiText.stripStyling(ansi));
        assertTrue(plain.getBytes(StandardCharsets.UTF_8).length < 1_000_000);
        assertTrue(plain.lines().count() < 20_000);
        assertTrue(plain.endsWith("\n"));
        assertFalse(plain.chars().anyMatch(character -> character == '\u001B'
                || character == '\r' || character == '\t' || character == 0));

        var json = StructuredDiagnosticRenderer.toJson(document, OBSERVED_AT);
        assertTrue(json.getBytes(StandardCharsets.UTF_8).length
                <= StructuredDiagnosticRenderer.MAX_RECORD_BYTES);
        assertFalse(json.contains("\n"));
        var parsed = JsonParser.parseString(json).getAsJsonObject();
        var location = parsed.getAsJsonObject("root").getAsJsonObject("locations")
                .getAsJsonArray("items").get(0).getAsJsonObject();
        assertEquals(path, location.getAsJsonObject("display").get("value").getAsString());
        assertEquals(source, location.getAsJsonObject("excerpt").getAsJsonObject("lines")
                .getAsJsonArray("items").get(0).getAsJsonObject()
                .getAsJsonObject("text").get("value").getAsString());

        var lines = new ArrayList<String>();
        StructuredDiagnosticRenderer.emitNdjson(document, OBSERVED_AT, lines::add);
        assertEquals(1, lines.size());
        assertEquals(json + "\n", lines.getFirst());
        assertEquals(1, lines.getFirst().chars().filter(character -> character == '\n').count());
        JsonParser.parseString(lines.getFirst()).getAsJsonObject();
    }
}
