package org.minecraftprot.stackframe.renderer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.minecraftprot.stackframe.testkit.GoldenSnapshots;

@Tag("golden")
class StructuredDiagnosticGoldenTest {
    private static final Instant OBSERVED_AT = Instant.parse("2026-09-25T12:34:56.123Z");

    @Test
    void rendersMinimumAndFullDocuments() throws IOException {
        assertSnapshot("golden/structured/minimum.json",
                StructuredDiagnosticRenderer.toJson(RendererFixtures.minimum(), OBSERVED_AT) + "\n");
        assertSnapshot("golden/structured/full.json",
                StructuredDiagnosticRenderer.toJson(RendererFixtures.full(), OBSERVED_AT) + "\n");
    }

    @Test
    void rendersTwoCompleteNdjsonEvents() throws IOException {
        var records = new ArrayList<String>();
        StructuredDiagnosticRenderer.emitNdjson(RendererFixtures.minimum(), OBSERVED_AT,
                records::add);
        StructuredDiagnosticRenderer.emitNdjson(RendererFixtures.omissionsAndWriteFailure(),
                OBSERVED_AT.plusSeconds(1), records::add);
        assertEquals(2, records.size());
        assertSnapshot("golden/structured/events.ndjson", String.join("", records));
    }

    private static void assertSnapshot(String name, String actual) throws IOException {
        GoldenSnapshots.assertMatches(Path.of("src/test/resources").resolve(name), actual);
    }
}
