package org.minecraftprot.stackframe.bundle;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.minecraftprot.stackframe.diagnostic.DiagnosticDocument;
import org.minecraftprot.stackframe.diagnostic.DisplayText;
import org.minecraftprot.stackframe.diagnostic.TextDisposition;
import org.minecraftprot.stackframe.renderer.StructuredDiagnosticRenderer;

/**
 * Explicit, local export of selected completed diagnostics. Planning creates the
 * exact bytes that will be exported, so an operator can review the file list and
 * redaction counts before a ZIP is written. Raw logs, worlds, and traces cannot
 * enter this format.
 */
public final class SupportBundle {
    public static final int MAX_DIAGNOSTICS = 64;
    public static final int MAX_MODS = 32;
    public static final long MAX_UNCOMPRESSED_BYTES = 2_097_152;
    public static final Duration MAX_TIME_RANGE = Duration.ofDays(1);

    private static final Pattern METADATA_KEY = Pattern.compile("[a-z][a-z0-9_]{0,63}");
    private static final String README = "Stackframe sanitized support bundle\n"
            + "Only selected completed diagnostics and explicitly supplied metadata are included.\n"
            + "Raw logs, full traces, world/player files, configuration contents, and credentials are excluded.\n"
            + "Review this archive before sharing it. Delete the archive when it is no longer needed.\n";

    private SupportBundle() {
    }

    public record Event(DiagnosticDocument document, Instant observedAt) {
        public Event {
            Objects.requireNonNull(document, "document");
            Objects.requireNonNull(observedAt, "observedAt");
        }
    }

    /** Metadata values must already be post-policy DisplayText, never raw strings. */
    public record Mod(DisplayText id, DisplayText version) {
        public Mod {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(version, "version");
        }
    }

    /** Explicitly selected metadata; no server inventory is read automatically. */
    public record Metadata(int configurationSchemaVersion,
            Map<String, DisplayText> environmentVersions, List<Mod> mods) {
        public Metadata {
            if (configurationSchemaVersion < 1) {
                throw new IllegalArgumentException("configuration schema version must be positive");
            }
            Objects.requireNonNull(environmentVersions, "environmentVersions");
            Objects.requireNonNull(mods, "mods");
            if (environmentVersions.size() > 16 || mods.size() > MAX_MODS) {
                throw new IllegalArgumentException("support metadata exceeds limits");
            }
            environmentVersions.forEach((key, value) -> {
                if (key == null || !METADATA_KEY.matcher(key).matches() || value == null) {
                    throw new IllegalArgumentException("invalid environment metadata");
                }
            });
            if (mods.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("null mod metadata");
            }
            environmentVersions = Map.copyOf(environmentVersions);
            mods = List.copyOf(mods);
        }
    }

    public record FilePreview(String name, long uncompressedBytes) {
    }

    /** Contains only names, sizes, counts, and time bounds; no diagnostic values. */
    public record Preview(List<FilePreview> files, int diagnosticCount,
            Instant firstObservedAt, Instant lastObservedAt,
            Map<String, Long> redactions, long totalUncompressedBytes) {
        public Preview {
            files = List.copyOf(files);
            redactions = Map.copyOf(redactions);
        }
    }

    public static Plan plan(List<Event> selected, Metadata metadata) {
        Objects.requireNonNull(selected, "selected");
        Objects.requireNonNull(metadata, "metadata");
        if (selected.isEmpty() || selected.size() > MAX_DIAGNOSTICS
                || selected.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("select 1-64 completed diagnostics");
        }
        selected = List.copyOf(selected);
        var first = selected.stream().map(Event::observedAt).min(Instant::compareTo).orElseThrow();
        var last = selected.stream().map(Event::observedAt).max(Instant::compareTo).orElseThrow();
        if (Duration.between(first, last).compareTo(MAX_TIME_RANGE) > 0) {
            throw new IllegalArgumentException("selected diagnostics span more than one day");
        }

        var notices = new LinkedHashMap<String, Long>();
        var records = new ByteArrayOutputStream();
        for (var event : selected) {
            var encoded = (StructuredDiagnosticRenderer.toJson(
                    event.document(), event.observedAt()) + "\n")
                    .getBytes(StandardCharsets.UTF_8);
            if ((long) records.size() + encoded.length > MAX_UNCOMPRESSED_BYTES) {
                throw new IllegalArgumentException("selected diagnostics exceed the byte limit");
            }
            records.writeBytes(encoded);
            event.document().redactions().items().forEach(notice ->
                    notices.merge(notice.marker().category() + ":"
                                    + notice.transformation().name(),
                            (long) notice.occurrenceCount(), Math::addExact));
        }

        var manifest = "stackframe_support_bundle=1\n"
                + "configuration_schema_version=" + metadata.configurationSchemaVersion() + "\n"
                + "diagnostics=" + selected.size() + "\n"
                + "first_observed_at=" + first + "\n"
                + "last_observed_at=" + last + "\n"
                + "raw_traces=included:false\n";
        var metadataRows = new StringBuilder("kind\tname\tversion\n");
        metadata.environmentVersions().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    metadataRows.append("environment\t")
                            .append(entry.getKey()).append('\t')
                            .append(entry.getValue().value()).append('\n');
                    countDisplay(notices, entry.getValue());
                });
        for (var mod : metadata.mods()) {
            metadataRows.append("mod\t").append(mod.id().value()).append('\t')
                    .append(mod.version().value()).append('\n');
            countDisplay(notices, mod.id());
            countDisplay(notices, mod.version());
        }

        var content = new LinkedHashMap<String, byte[]>();
        content.put("manifest.txt", manifest.getBytes(StandardCharsets.UTF_8));
        content.put("diagnostics.ndjson", records.toByteArray());
        content.put("metadata.tsv", metadataRows.toString().getBytes(StandardCharsets.UTF_8));
        content.put("README.txt", README.getBytes(StandardCharsets.UTF_8));
        long total = 0;
        var files = new ArrayList<FilePreview>();
        for (var entry : content.entrySet()) {
            total = Math.addExact(total, entry.getValue().length);
            files.add(new FilePreview(entry.getKey(), entry.getValue().length));
        }
        if (total > MAX_UNCOMPRESSED_BYTES) {
            throw new IllegalArgumentException("support bundle exceeds the uncompressed byte limit");
        }
        return new Plan(content, new Preview(
                files, selected.size(), first, last, notices, total));
    }

    private static void countDisplay(Map<String, Long> notices, DisplayText text) {
        if (text.disposition() != TextDisposition.VISIBLE) {
            notices.merge(text.marker().orElseThrow().category() + ":"
                    + text.disposition().name(), 1L, Math::addExact);
        }
    }

    public static final class Plan {
        private final Map<String, byte[]> content;
        private final Preview preview;

        private Plan(Map<String, byte[]> content, Preview preview) {
            this.content = Map.copyOf(content);
            this.preview = preview;
        }

        public Preview preview() {
            return preview;
        }

        /** Writes only the bytes represented by this preview; never overwrites a target. */
        public Path export(Path target) throws IOException {
            Objects.requireNonNull(target, "target");
            var absolute = target.toAbsolutePath();
            var parent = absolute.getParent();
            if (parent == null || !Files.isDirectory(parent) || Files.exists(absolute)) {
                throw new IOException("support bundle destination is unavailable");
            }
            var partial = Files.createTempFile(parent, ".stackframe-bundle-", ".partial");
            try {
                try (var stream = new ZipOutputStream(Files.newOutputStream(partial))) {
                    for (var file : preview.files()) {
                        var entry = new ZipEntry(file.name());
                        entry.setTime(0);
                        stream.putNextEntry(entry);
                        stream.write(content.get(file.name()));
                        stream.closeEntry();
                    }
                }
                Files.move(partial, absolute);
                return absolute;
            } finally {
                Files.deleteIfExists(partial);
            }
        }
    }
}
