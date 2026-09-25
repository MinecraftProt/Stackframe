package org.minecraftprot.stackframe.renderer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class StructuredDiagnosticRendererTest {
    private static final Instant OBSERVED_AT = Instant.parse("2026-09-25T12:34:56.123Z");

    @Test
    void projectsEveryDocumentAndDiagnosticFieldWithoutChangingMeaning() {
        var document = RendererFixtures.full();
        var json = parse(StructuredDiagnosticRenderer.toJson(document, OBSERVED_AT));
        assertEquals(Set.of("schemaVersion", "observedAt", "diagnosticId", "correlationId",
                "root", "redactions", "omissions"), json.keySet());
        assertEquals("1.0", json.get("schemaVersion").getAsString());
        assertEquals("2026-09-25T12:34:56.123Z", json.get("observedAt").getAsString());
        assertEquals(document.diagnosticId().value(), json.get("diagnosticId").getAsString());
        assertEquals(document.correlationId().value(), json.get("correlationId").getAsString());

        var root = json.getAsJsonObject("root");
        assertEquals(Set.of("severity", "code", "title", "locations", "notes", "help",
                "trace", "evidence", "confidence", "children", "omissions"), root.keySet());
        assertEquals("error", root.get("severity").getAsString());
        assertEquals(document.root().code().value(), root.get("code").getAsString());
        assertEquals(document.root().title().value(),
                root.getAsJsonObject("title").get("value").getAsString());
        assertEquals("note", root.getAsJsonObject("children").getAsJsonArray("items")
                .get(0).getAsJsonObject().getAsJsonObject("diagnostic")
                .get("severity").getAsString());

        var plain = DiagnosticRenderer.renderToString(document,
                RenderOptions.plain(RenderWidth.known(100)));
        assertTrue(plain.contains(root.get("code").getAsString()));
        assertTrue(plain.contains(root.getAsJsonObject("title").get("value").getAsString()));
        assertTrue(plain.contains("world/datapacks/example/data/example/item.json"));
    }

    @Test
    void usesTheCompletedRedactedModelAndPreservesOmissionCounts() {
        var full = parse(StructuredDiagnosticRenderer.toJson(RendererFixtures.full(), OBSERVED_AT));
        var location = full.getAsJsonObject("root").getAsJsonObject("locations")
                .getAsJsonArray("items").get(0).getAsJsonObject()
                .getAsJsonObject("display");
        assertEquals("generalized", location.get("disposition").getAsString());
        assertEquals("server_sensitive", location.get("sensitivity").getAsString());
        assertEquals("PATH", location.getAsJsonObject("marker").get("category").getAsString());
        assertEquals(location.get("value").getAsString(),
                RendererFixtures.full().root().locations().items().get(0).display().value());
        assertEquals(1, full.getAsJsonObject("redactions").getAsJsonArray("items")
                .get(0).getAsJsonObject().get("occurrenceCount").getAsInt());

        var redacted = parse(StructuredDiagnosticRenderer.toJson(
                RendererFixtures.redactedLocation(), OBSERVED_AT));
        var protectedDisplay = redacted.getAsJsonObject("root")
                .getAsJsonObject("locations").getAsJsonArray("items").get(0)
                .getAsJsonObject().getAsJsonObject("display");
        assertEquals("<redacted:token>", protectedDisplay.get("value").getAsString());
        assertEquals("secret", protectedDisplay.get("sensitivity").getAsString());
        assertEquals("redacted", protectedDisplay.get("disposition").getAsString());

        var bounded = parse(StructuredDiagnosticRenderer.toJson(
                RendererFixtures.omissionsAndWriteFailure(), OBSERVED_AT));
        assertEquals(2, bounded.getAsJsonObject("root").getAsJsonObject("locations")
                .get("omittedCount").getAsInt());
        assertEquals("write_failed", bounded.getAsJsonObject("root")
                .getAsJsonObject("trace").get("state").getAsString());
        assertEquals(1, bounded.getAsJsonObject("redactions")
                .get("omittedCount").getAsInt());
    }

    @Test
    void escapesHostileTextAndDeliversExactlyOneCompleteLinePerEvent() throws IOException {
        var hostile = "first\n{\"schemaVersion\":\"999.0\"} \\ last";
        var document = RendererFixtures.location(hostile);
        var calls = new ArrayList<String>();
        StructuredDiagnosticRenderer.emitNdjson(document, OBSERVED_AT, calls::add);
        assertEquals(1, calls.size());
        var record = calls.get(0);
        assertTrue(record.endsWith("\n"));
        assertEquals(1, record.chars().filter(character -> character == '\n').count());
        assertEquals(hostile, parse(record).getAsJsonObject("root")
                .getAsJsonObject("locations").getAsJsonArray("items")
                .get(0).getAsJsonObject().getAsJsonObject("display")
                .get("value").getAsString());

        var callbacks = new AtomicInteger();
        assertThrows(NullPointerException.class,
                () -> StructuredDiagnosticRenderer.emitNdjson(document, null,
                        line -> callbacks.incrementAndGet()));
        assertEquals(0, callbacks.get());
    }

    @Test
    void forwardMinorFixtureKeepsKnownFieldsAndIgnoresUnknownOptionalFields()
            throws IOException {
        var fixture = Files.readString(Path.of("src/test/resources/golden/structured/"
                + "future-minor-unknown-fields.json"), StandardCharsets.UTF_8);
        var json = parse(fixture);
        assertEquals("1.1", json.get("schemaVersion").getAsString());
        assertEquals("diag0001", json.get("diagnosticId").getAsString());
        assertEquals("error", json.getAsJsonObject("root").get("severity").getAsString());
        assertTrue(json.has("futureEventMetadata"));
        assertTrue(json.getAsJsonObject("root").has("futureOptionalField"));
        assertFalse(json.getAsJsonObject("root").getAsJsonObject("title")
                .get("value").getAsString().isBlank());
    }

    private static JsonObject parse(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }
}
