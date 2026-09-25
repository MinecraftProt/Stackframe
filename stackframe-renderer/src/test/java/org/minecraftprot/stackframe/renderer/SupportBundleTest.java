package org.minecraftprot.stackframe.renderer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.minecraftprot.stackframe.bundle.SupportBundle;
import org.minecraftprot.stackframe.diagnostic.DisplayText;
import org.minecraftprot.stackframe.diagnostic.RedactionMarker;
import org.minecraftprot.stackframe.diagnostic.Sensitivity;
import org.minecraftprot.stackframe.diagnostic.TextOrigin;

final class SupportBundleTest {
    @TempDir Path directory;

    @Test
    void previewExactlyMatchesExportedSanitizedFilesAndCounts() throws IOException {
        var observed = Instant.parse("2026-09-25T17:00:00Z");
        var events = List.of(
                new SupportBundle.Event(RendererFixtures.redactedLocation(), observed),
                new SupportBundle.Event(RendererFixtures.minimum(), observed.plusSeconds(30)));
        var metadata = new SupportBundle.Metadata(1,
                Map.of("minecraft", DisplayText.visible("26.2", TextOrigin.GENERATED)),
                List.of(new SupportBundle.Mod(
                        DisplayText.redacted(TextOrigin.EXTERNAL, Sensitivity.SECRET,
                                new RedactionMarker("CONFIGURED_IDENTIFIER")),
                        DisplayText.visible("1.0.0", TextOrigin.GENERATED))));
        var plan = SupportBundle.plan(events, metadata);
        var preview = plan.preview();
        assertEquals(2, preview.diagnosticCount());
        assertEquals(observed, preview.firstObservedAt());
        assertEquals(observed.plusSeconds(30), preview.lastObservedAt());
        assertEquals(List.of("manifest.txt", "diagnostics.ndjson", "metadata.tsv", "README.txt"),
                preview.files().stream().map(SupportBundle.FilePreview::name).toList());
        assertEquals(1L, preview.redactions().get("TOKEN:REDACTED"));
        assertEquals(1L, preview.redactions().get("CONFIGURED_IDENTIFIER:REDACTED"));
        assertEquals(preview.files().stream()
                .mapToLong(SupportBundle.FilePreview::uncompressedBytes).sum(),
                preview.totalUncompressedBytes());

        var target = directory.resolve("support.zip");
        assertEquals(target.toAbsolutePath(), plan.export(target));
        try (var zip = new ZipFile(target.toFile())) {
            assertEquals(preview.files().stream().map(SupportBundle.FilePreview::name).toList(),
                    zip.stream().map(java.util.zip.ZipEntry::getName).toList());
            for (var file : preview.files()) {
                try (var input = zip.getInputStream(zip.getEntry(file.name()))) {
                    assertEquals(file.uncompressedBytes(), input.readAllBytes().length);
                }
            }
            var records = read(zip, "diagnostics.ndjson").split("\n");
            assertEquals(2, records.length);
            assertEquals("diag0004", JsonParser.parseString(records[0])
                    .getAsJsonObject().get("diagnosticId").getAsString());
            assertTrue(records[0].contains("<redacted:token>"));
            assertFalse(read(zip, "manifest.txt").contains(directory.toString()));
            assertTrue(read(zip, "manifest.txt").contains("raw_traces=included:false"));
            assertTrue(read(zip, "metadata.tsv").contains("mod\t<redacted:configured_identifier>\t1.0.0"));
            assertTrue(read(zip, "README.txt").contains("Delete the archive"));
        }
    }

    @Test
    void refusesOverwriteAndLeavesNoPartialArchive() throws IOException {
        var plan = SupportBundle.plan(
                List.of(new SupportBundle.Event(RendererFixtures.minimum(),
                        Instant.parse("2026-09-25T17:00:00Z"))),
                new SupportBundle.Metadata(1, Map.of(), List.of()));
        var target = directory.resolve("existing.zip");
        Files.writeString(target, "operator data");
        assertThrows(IOException.class, () -> plan.export(target));
        assertEquals("operator data", Files.readString(target));
        try (var entries = Files.list(directory)) {
            assertEquals(List.of(target), entries.toList());
        }
    }

    @Test
    void rejectsUnboundedSelectionBeforeExport() {
        var at = Instant.parse("2026-09-25T17:00:00Z");
        var metadata = new SupportBundle.Metadata(1, Map.of(), List.of());
        var event = new SupportBundle.Event(RendererFixtures.minimum(), at);
        assertThrows(IllegalArgumentException.class,
                () -> SupportBundle.plan(java.util.Collections.nCopies(65, event), metadata));
        assertThrows(IllegalArgumentException.class,
                () -> SupportBundle.plan(List.of(event,
                        new SupportBundle.Event(RendererFixtures.minimum(),
                                at.plus(SupportBundle.MAX_TIME_RANGE).plusSeconds(1))),
                        metadata));
        var oversized = new ArrayList<SupportBundle.Event>();
        var pathological = RendererFixtures.pathological();
        for (var index = 0; index < 64; index++) {
            oversized.add(new SupportBundle.Event(pathological, at));
        }
        assertThrows(IllegalArgumentException.class,
                () -> SupportBundle.plan(oversized, metadata));
    }

    private static String read(ZipFile zip, String name) throws IOException {
        try (var input = zip.getInputStream(zip.getEntry(name))) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
