package org.minecraftprot.stackframe.diagnostic;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class DocumentValidator {
    private final IdentityHashMap<Diagnostic, String> diagnosticIdentities = new IdentityHashMap<>();
    private final Map<String, RepeatedField> repeatedFields = new LinkedHashMap<>();
    private final Map<String, String> scalarOmissionTargets = new LinkedHashMap<>();
    private final List<OmissionAtPath> omissionRecords = new ArrayList<>();
    private final Map<RedactionKey, Integer> transformedValues = new LinkedHashMap<>();
    private final Map<RedactionKey, Integer> redactionNotices = new LinkedHashMap<>();
    private final DiagnosticId rootDiagnosticId;
    private long stringBytes;
    private int nodeCount;

    private DocumentValidator(DiagnosticId rootDiagnosticId) {
        this.rootDiagnosticId = rootDiagnosticId;
    }

    static void validate(
            SchemaVersion schemaVersion,
            DiagnosticId diagnosticId,
            CorrelationId correlationId,
            Diagnostic root,
            BoundedList<RedactionNotice> redactions,
            BoundedList<Omission> omissions) {
        var validator = new DocumentValidator(diagnosticId);
        validator.string(schemaVersion.value());
        validator.string(diagnosticId.value());
        validator.string(correlationId.value());
        validator.bounded("$.redactions", redactions, "$.omissions");
        for (var notice : redactions.items()) {
            validator.redaction(notice);
        }
        validator.bounded("$.omissions", omissions, "$.omissions");
        validator.omissions(omissions, "$.omissions", "$.omissions");
        validator.diagnostic(root, "$.root", 1);
        validator.validateOmissions();
        validator.validateRedactionNotices();
        if (validator.stringBytes > ModelLimits.DOCUMENT_UTF8_BYTES) {
            throw new LimitValidationException(
                    "$",
                    "string data uses " + validator.stringBytes + " UTF-8 bytes; maximum is "
                            + ModelLimits.DOCUMENT_UTF8_BYTES);
        }
    }

    private void diagnostic(Diagnostic diagnostic, String path, int depth) {
        if (depth > ModelLimits.DIAGNOSTIC_DEPTH) {
            throw new RelationValidationException(
                    path, "nesting depth exceeds " + ModelLimits.DIAGNOSTIC_DEPTH);
        }
        var priorPath = diagnosticIdentities.put(diagnostic, path);
        if (priorPath != null) {
            throw new RelationValidationException(
                    path, "diagnostic node instance is already retained at " + priorPath);
        }
        nodeCount++;
        if (nodeCount > ModelLimits.DIAGNOSTIC_NODES) {
            throw new LimitValidationException(
                    path, "document contains more than " + ModelLimits.DIAGNOSTIC_NODES + " nodes");
        }

        var omissionScope = path + ".omissions";
        string(diagnostic.severity().name());
        string(diagnostic.code().value());
        catalog(diagnostic.title(), path + ".title", omissionScope);

        bounded(path + ".locations", diagnostic.locations(), omissionScope);
        for (var index = 0; index < diagnostic.locations().items().size(); index++) {
            location(diagnostic.locations().items().get(index),
                    path + ".locations.items[" + index + "]", omissionScope);
        }
        bounded(path + ".notes", diagnostic.notes(), omissionScope);
        for (var index = 0; index < diagnostic.notes().items().size(); index++) {
            note(
                    diagnostic.notes().items().get(index),
                    path + ".notes.items[" + index + "]",
                    omissionScope);
        }
        bounded(path + ".help", diagnostic.help(), omissionScope);
        for (var index = 0; index < diagnostic.help().items().size(); index++) {
            help(
                    diagnostic.help().items().get(index),
                    path + ".help.items[" + index + "]",
                    omissionScope);
        }
        trace(diagnostic.trace(), path + ".trace", omissionScope);
        bounded(path + ".evidence", diagnostic.evidence(), omissionScope);
        for (var index = 0; index < diagnostic.evidence().items().size(); index++) {
            evidence(
                    diagnostic.evidence().items().get(index),
                    path + ".evidence.items[" + index + "]",
                    omissionScope);
        }
        confidence(diagnostic.confidence(), path + ".confidence", omissionScope);
        bounded(path + ".children", diagnostic.children(), omissionScope);
        bounded(path + ".omissions", diagnostic.omissions(), omissionScope);
        omissions(diagnostic.omissions(), path + ".omissions", omissionScope);

        for (var index = 0; index < diagnostic.children().items().size(); index++) {
            var child = diagnostic.children().items().get(index);
            string(child.relation().name());
            diagnostic(
                    child.diagnostic(),
                    path + ".children.items[" + index + "].diagnostic",
                    depth + 1);
        }
    }

    private void location(Location location, String path, String omissionScope) {
        string(location.id().value());
        string(location.kind().name());
        display(location.display(), path + ".display", omissionScope);
        location.position().ifPresent(this::range);
        scalarOmissionTarget(path + ".position", omissionScope);
        scalarOmissionTarget(path + ".excerpt", omissionScope);
        location.excerpt().ifPresent(excerpt -> excerpt(
                excerpt, path + ".excerpt", omissionScope));
        bounded(path + ".evidenceIds", location.evidenceIds(), omissionScope);
        location.evidenceIds().items().forEach(id -> string(id.value()));
    }

    private void excerpt(Excerpt excerpt, String path, String omissionScope) {
        bounded(path + ".lines", excerpt.lines(), omissionScope);
        for (var index = 0; index < excerpt.lines().items().size(); index++) {
            display(
                    excerpt.lines().items().get(index).text(),
                    path + ".lines.items[" + index + "].text",
                    omissionScope);
        }
        bounded(path + ".labels", excerpt.labels(), omissionScope);
        for (var index = 0; index < excerpt.labels().items().size(); index++) {
            var label = excerpt.labels().items().get(index);
            var labelPath = path + ".labels.items[" + index + "]";
            range(label.range());
            string(label.style().name());
            display(label.message(), labelPath + ".message", omissionScope);
            bounded(labelPath + ".evidenceIds", label.evidenceIds(), omissionScope);
            label.evidenceIds().items().forEach(id -> string(id.value()));
        }
    }

    private void note(Note note, String path, String omissionScope) {
        string(note.kind().name());
        display(note.text(), path + ".text", omissionScope);
        bounded(path + ".evidenceIds", note.evidenceIds(), omissionScope);
        note.evidenceIds().items().forEach(id -> string(id.value()));
    }

    private void help(Help help, String path, String omissionScope) {
        display(help.text(), path + ".text", omissionScope);
        string(help.kind().name());
        bounded(path + ".evidenceIds", help.evidenceIds(), omissionScope);
        help.evidenceIds().items().forEach(id -> string(id.value()));
    }

    private void evidence(EvidenceReference evidence, String path, String omissionScope) {
        string(evidence.id().value());
        string(evidence.kind().name());
        display(evidence.summary(), path + ".summary", omissionScope);
        scalarOmissionTarget(path + ".source", omissionScope);
        evidence.source().ifPresent(value -> display(
                value, path + ".source", omissionScope));
    }

    private void confidence(ConfidenceReference confidence, String path, String omissionScope) {
        scalarOmissionTarget(path + ".assessmentId", omissionScope);
        scalarOmissionTarget(path + ".classifierId", omissionScope);
        scalarOmissionTarget(path + ".policyId", omissionScope);
        confidence.assessmentId().ifPresent(id -> string(id.value()));
        confidence.classifierId().ifPresent(id -> string(id.value()));
        bounded(path + ".evidenceIds", confidence.evidenceIds(), omissionScope);
        confidence.evidenceIds().items().forEach(id -> string(id.value()));
        confidence.policyId().ifPresent(id -> string(id.value()));
    }

    private void trace(TraceSummary trace, String path, String omissionScope) {
        string(trace.state().name());
        scalarOmissionTarget(path + ".destination", omissionScope);
        scalarOmissionTarget(path + ".recordId", omissionScope);
        trace.destination().ifPresent(value -> display(
                value, path + ".destination", omissionScope));
        trace.recordId().ifPresent(id -> {
            if (id.equals(rootDiagnosticId)) {
                throw new TraceValidationException(
                        path + ".recordId", "must be absent when it equals the root diagnostic ID");
            }
            string(id.value());
        });
    }

    private void redaction(RedactionNotice notice) {
        marker(notice.marker());
        string(notice.transformation().name());
        redactionNotices.merge(
                new RedactionKey(notice.marker(), notice.transformation()),
                notice.occurrenceCount(),
                DocumentValidator::addCounts);
    }

    private void omissions(BoundedList<Omission> omissions, String path, String omissionScope) {
        for (var index = 0; index < omissions.items().size(); index++) {
            var omission = omissions.items().get(index);
            string(omission.affectedPath().value());
            string(omission.reason().name());
            omissionRecords.add(new OmissionAtPath(
                    path + ".items[" + index + "]", omission, omissionScope));
        }
    }

    private void catalog(CatalogText text, String path, String omissionScope) {
        scalarOmissionTarget(path, omissionScope);
        string(text.key());
        string(text.value());
    }

    private void display(DisplayText text, String path, String omissionScope) {
        scalarOmissionTarget(path, omissionScope);
        string(text.value());
        string(text.origin().name());
        string(text.sensitivity().name());
        string(text.disposition().name());
        text.marker().ifPresent(this::marker);
        if (text.disposition() != TextDisposition.VISIBLE) {
            transformedValues.merge(
                    new RedactionKey(text.marker().orElseThrow(), text.disposition()),
                    1,
                    DocumentValidator::addCounts);
        }
    }

    private void marker(RedactionMarker marker) {
        string(marker.category());
    }

    private void range(SourceRange range) {
        // Numeric positions do not contribute to the string budget.
    }

    private void bounded(String path, BoundedList<?> values, String omissionScope) {
        if (repeatedFields.put(
                path, new RepeatedField(values.omittedCount(), omissionScope)) != null) {
            throw new OmissionValidationException(path, "logical repeated-field path is not unique");
        }
    }

    private void scalarOmissionTarget(String path, String omissionScope) {
        scalarOmissionTargets.put(path, omissionScope);
    }

    private void validateOmissions() {
        var matched = new LinkedHashMap<String, Integer>();
        for (var atPath : omissionRecords) {
            var target = atPath.omission().affectedPath().value();
            var repeatedField = repeatedFields.get(target);
            if (repeatedField == null) {
                var scalarScope = scalarOmissionTargets.get(target);
                if (scalarScope == null) {
                    throw new OmissionValidationException(
                            atPath.recordPath(), "affected path is not an omittable model field");
                }
                if (!scalarScope.equals(atPath.omissionScope())) {
                    throw new OmissionValidationException(
                            atPath.recordPath(), "affected field is outside this omission scope");
                }
                if (!Set.of(
                                OmissionReason.TEXT_LIMIT,
                                OmissionReason.BYTE_BUDGET,
                                OmissionReason.REDACTION_POLICY)
                        .contains(atPath.omission().reason())) {
                    throw new OmissionValidationException(
                            atPath.recordPath(),
                            "scalar omission requires TEXT_LIMIT, BYTE_BUDGET, or REDACTION_POLICY");
                }
                if (matched.put(target, atPath.omission().omittedCount()) != null) {
                    throw new OmissionValidationException(
                            atPath.recordPath(), "more than one omission records the same affected field");
                }
                continue;
            }
            if (repeatedField.omittedCount() == 0) {
                throw new OmissionValidationException(
                        atPath.recordPath(), "affected field has no omitted values");
            }
            if (!repeatedField.omissionScope().equals(atPath.omissionScope())) {
                throw new OmissionValidationException(
                        atPath.recordPath(), "affected field is outside this omission scope");
            }
            if (repeatedField.omittedCount() != atPath.omission().omittedCount()) {
                throw new OmissionValidationException(
                        atPath.recordPath(),
                        "count " + atPath.omission().omittedCount()
                                + " does not match bounded-list count "
                                + repeatedField.omittedCount());
            }
            if (matched.put(target, atPath.omission().omittedCount()) != null) {
                throw new OmissionValidationException(
                        atPath.recordPath(), "more than one omission records the same affected field");
            }
        }
        repeatedFields.forEach((path, repeatedField) -> {
            if (repeatedField.omittedCount() > 0 && !matched.containsKey(path)) {
                throw new OmissionValidationException(
                        path, "non-zero omittedCount requires exactly one matching omission");
            }
        });
    }

    private void validateRedactionNotices() {
        transformedValues.forEach((key, observedCount) -> {
            var noticeCount = redactionNotices.getOrDefault(key, 0);
            if (noticeCount < observedCount) {
                throw new RedactionValidationException(
                        "$.redactions",
                        "marker " + key.marker().category() + " and transformation "
                                + key.disposition() + " records " + noticeCount
                                + " occurrences but the completed graph contains " + observedCount);
            }
        });
    }

    private static int addCounts(int first, int second) {
        var total = (long) first + second;
        if (total > Integer.MAX_VALUE) {
            throw new RedactionValidationException(
                    "$.redactions", "aggregate occurrence count exceeds 2,147,483,647");
        }
        return (int) total;
    }

    private void string(String value) {
        stringBytes += value.getBytes(StandardCharsets.UTF_8).length;
    }

    private record RepeatedField(int omittedCount, String omissionScope) {
    }

    private record RedactionKey(
            RedactionMarker marker, TextDisposition disposition) {
    }

    private record OmissionAtPath(
            String recordPath, Omission omission, String omissionScope) {
    }
}
