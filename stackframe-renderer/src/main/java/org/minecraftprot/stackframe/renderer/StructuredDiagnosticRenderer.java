package org.minecraftprot.stackframe.renderer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import org.minecraftprot.stackframe.diagnostic.BoundedList;
import org.minecraftprot.stackframe.diagnostic.CatalogText;
import org.minecraftprot.stackframe.diagnostic.ConfidenceReference;
import org.minecraftprot.stackframe.diagnostic.Diagnostic;
import org.minecraftprot.stackframe.diagnostic.DiagnosticDocument;
import org.minecraftprot.stackframe.diagnostic.DisplayText;
import org.minecraftprot.stackframe.diagnostic.EvidenceId;
import org.minecraftprot.stackframe.diagnostic.EvidenceReference;
import org.minecraftprot.stackframe.diagnostic.Excerpt;
import org.minecraftprot.stackframe.diagnostic.ExcerptLine;
import org.minecraftprot.stackframe.diagnostic.Help;
import org.minecraftprot.stackframe.diagnostic.Label;
import org.minecraftprot.stackframe.diagnostic.Location;
import org.minecraftprot.stackframe.diagnostic.Note;
import org.minecraftprot.stackframe.diagnostic.Omission;
import org.minecraftprot.stackframe.diagnostic.RedactionMarker;
import org.minecraftprot.stackframe.diagnostic.RedactionNotice;
import org.minecraftprot.stackframe.diagnostic.RelatedDiagnostic;
import org.minecraftprot.stackframe.diagnostic.SourcePosition;
import org.minecraftprot.stackframe.diagnostic.SourceRange;
import org.minecraftprot.stackframe.diagnostic.TraceSummary;

/** Faithful, bounded JSON projection of a completed diagnostic document. */
public final class StructuredDiagnosticRenderer {
    /** Maximum UTF-8 bytes in one serialized event, before the NDJSON LF. */
    public static final int MAX_RECORD_BYTES = 2_097_152;

    private StructuredDiagnosticRenderer() {
    }

    /**
     * Returns one compact JSON object. {@code observedAt} is supplied by the
     * event adapter so rendering never invents capture time or reads a clock.
     */
    public static String toJson(DiagnosticDocument document, Instant observedAt) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(observedAt, "observedAt");
        var root = object(
                "schemaVersion", document.schemaVersion().value(),
                "observedAt", observedAt.toString(),
                "diagnosticId", document.diagnosticId().value(),
                "correlationId", document.correlationId().value(),
                "root", diagnostic(document.root()),
                "redactions", bounded(document.redactions(), StructuredDiagnosticRenderer::redaction),
                "omissions", bounded(document.omissions(), StructuredDiagnosticRenderer::omission));
        var output = new StringBuilder();
        appendJson(output, root);
        var json = output.toString();
        if (json.getBytes(StandardCharsets.UTF_8).length > MAX_RECORD_BYTES) {
            throw new RenderLimitException("structured diagnostic exceeds "
                    + MAX_RECORD_BYTES + " UTF-8 bytes");
        }
        return json;
    }

    /**
     * Stages the complete valid line before making exactly one sink call. No
     * callback occurs if rendering fails. The sink controls publication to a
     * file, logger, or message queue.
     */
    public static void emitNdjson(DiagnosticDocument document, Instant observedAt,
            NdjsonRecordSink sink) throws IOException {
        Objects.requireNonNull(sink, "sink");
        sink.writeRecord(toJson(document, observedAt) + "\n");
    }

    private static Map<String, Object> diagnostic(Diagnostic value) {
        return object(
                "severity", token(value.severity()),
                "code", value.code().value(),
                "title", catalog(value.title()),
                "locations", bounded(value.locations(), StructuredDiagnosticRenderer::location),
                "notes", bounded(value.notes(), StructuredDiagnosticRenderer::note),
                "help", bounded(value.help(), StructuredDiagnosticRenderer::help),
                "trace", trace(value.trace()),
                "evidence", bounded(value.evidence(), StructuredDiagnosticRenderer::evidence),
                "confidence", confidence(value.confidence()),
                "children", bounded(value.children(), StructuredDiagnosticRenderer::child),
                "omissions", bounded(value.omissions(), StructuredDiagnosticRenderer::omission));
    }

    private static Map<String, Object> catalog(CatalogText value) {
        return object("key", value.key(), "value", value.value());
    }

    private static Map<String, Object> display(DisplayText value) {
        return object(
                "value", value.value(),
                "origin", token(value.origin()),
                "sensitivity", token(value.sensitivity()),
                "disposition", token(value.disposition()),
                "marker", value.marker().map(StructuredDiagnosticRenderer::marker).orElse(null));
    }

    private static Map<String, Object> marker(RedactionMarker value) {
        return object("category", value.category());
    }

    private static Map<String, Object> location(Location value) {
        return object(
                "id", value.id().value(),
                "kind", token(value.kind()),
                "display", display(value.display()),
                "position", value.position().map(StructuredDiagnosticRenderer::range).orElse(null),
                "excerpt", value.excerpt().map(StructuredDiagnosticRenderer::excerpt).orElse(null),
                "evidenceIds", bounded(value.evidenceIds(), EvidenceId::value));
    }

    private static Map<String, Object> excerpt(Excerpt value) {
        return object(
                "startLine", value.startLine(),
                "lines", bounded(value.lines(), StructuredDiagnosticRenderer::excerptLine),
                "labels", bounded(value.labels(), StructuredDiagnosticRenderer::label));
    }

    private static Map<String, Object> excerptLine(ExcerptLine value) {
        return object("lineNumber", value.lineNumber(), "text", display(value.text()));
    }

    private static Map<String, Object> label(Label value) {
        return object(
                "range", range(value.range()),
                "style", token(value.style()),
                "message", display(value.message()),
                "evidenceIds", bounded(value.evidenceIds(), EvidenceId::value));
    }

    private static Map<String, Object> range(SourceRange value) {
        return object("start", position(value.start()), "end", position(value.end()));
    }

    private static Map<String, Object> position(SourcePosition value) {
        return object("line", value.line(), "column", value.column());
    }

    private static Map<String, Object> note(Note value) {
        return object(
                "kind", token(value.kind()),
                "text", display(value.text()),
                "evidenceIds", bounded(value.evidenceIds(), EvidenceId::value));
    }

    private static Map<String, Object> help(Help value) {
        return object(
                "text", display(value.text()),
                "kind", token(value.kind()),
                "evidenceIds", bounded(value.evidenceIds(), EvidenceId::value));
    }

    private static Map<String, Object> trace(TraceSummary value) {
        return object(
                "state", token(value.state()),
                "totalFrames", value.totalFrames().orElse(null),
                "shownFrames", value.shownFrames(),
                "omittedFrames", value.omittedFrames(),
                "omittedCauses", value.omittedCauses(),
                "destination", value.destination().map(StructuredDiagnosticRenderer::display)
                        .orElse(null),
                "recordId", value.recordId().map(id -> id.value()).orElse(null));
    }

    private static Map<String, Object> evidence(EvidenceReference value) {
        return object(
                "id", value.id().value(),
                "kind", token(value.kind()),
                "summary", display(value.summary()),
                "source", value.source().map(StructuredDiagnosticRenderer::display).orElse(null));
    }

    private static Map<String, Object> confidence(ConfidenceReference value) {
        return object(
                "assessmentId", value.assessmentId().map(id -> id.value()).orElse(null),
                "classifierId", value.classifierId().map(id -> id.value()).orElse(null),
                "evidenceIds", bounded(value.evidenceIds(), EvidenceId::value),
                "policyId", value.policyId().map(id -> id.value()).orElse(null));
    }

    private static Map<String, Object> child(RelatedDiagnostic value) {
        return object("relation", token(value.relation()), "diagnostic", diagnostic(value.diagnostic()));
    }

    private static Map<String, Object> omission(Omission value) {
        return object(
                "affectedPath", value.affectedPath().value(),
                "omittedCount", value.omittedCount(),
                "reason", token(value.reason()));
    }

    private static Map<String, Object> redaction(RedactionNotice value) {
        return object(
                "marker", marker(value.marker()),
                "transformation", token(value.transformation()),
                "occurrenceCount", value.occurrenceCount());
    }

    private static <T> Map<String, Object> bounded(BoundedList<T> value,
            Function<T, ?> mapper) {
        var items = new ArrayList<Object>(value.items().size());
        for (var item : value.items()) {
            items.add(mapper.apply(item));
        }
        return object("items", items, "omittedCount", value.omittedCount());
    }

    private static String token(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }

    private static Map<String, Object> object(Object... fields) {
        var result = new LinkedHashMap<String, Object>();
        for (var index = 0; index < fields.length; index += 2) {
            result.put((String) fields[index], fields[index + 1]);
        }
        return result;
    }

    private static void appendJson(StringBuilder target, Object value) {
        if (value == null) {
            target.append("null");
        } else if (value instanceof String string) {
            appendString(target, string);
        } else if (value instanceof Number number) {
            target.append(number);
        } else if (value instanceof Map<?, ?> map) {
            target.append('{');
            var first = true;
            for (var entry : map.entrySet()) {
                if (!first) {
                    target.append(',');
                }
                first = false;
                appendString(target, (String) entry.getKey());
                target.append(':');
                appendJson(target, entry.getValue());
            }
            target.append('}');
        } else if (value instanceof List<?> list) {
            target.append('[');
            for (var index = 0; index < list.size(); index++) {
                if (index > 0) {
                    target.append(',');
                }
                appendJson(target, list.get(index));
            }
            target.append(']');
        } else {
            throw new IllegalArgumentException("unsupported structured value type: "
                    + value.getClass().getName());
        }
    }

    private static void appendString(StringBuilder target, String value) {
        target.append('"');
        for (var index = 0; index < value.length(); index++) {
            var character = value.charAt(index);
            switch (character) {
                case '"' -> target.append("\\\"");
                case '\\' -> target.append("\\\\");
                case '\b' -> target.append("\\b");
                case '\f' -> target.append("\\f");
                case '\n' -> target.append("\\n");
                case '\r' -> target.append("\\r");
                case '\t' -> target.append("\\t");
                default -> {
                    if (character < 0x20) {
                        target.append("\\u%04x".formatted((int) character));
                    } else {
                        target.append(character);
                    }
                }
            }
        }
        target.append('"');
    }
}
