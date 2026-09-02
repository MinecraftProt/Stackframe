package org.minecraftprot.stackframe.diagnostic;

import java.util.HashSet;

/**
 * Immutable operator-facing diagnostic node. Every collection preserves producer
 * order; local IDs resolve only within this node, and no renderer-facing field
 * can retain pre-redaction candidate text.
 */
public record Diagnostic(
        Severity severity,
        DiagnosticCode code,
        CatalogText title,
        BoundedList<Location> locations,
        BoundedList<Note> notes,
        BoundedList<Help> help,
        TraceSummary trace,
        BoundedList<EvidenceReference> evidence,
        ConfidenceReference confidence,
        BoundedList<RelatedDiagnostic> children,
        BoundedList<Omission> omissions) {

    public Diagnostic {
        severity = Validation.required(severity, "$.diagnostic.severity");
        code = Validation.required(code, "$.diagnostic.code");
        title = Validation.required(title, "$.diagnostic.title");
        var titleLength = title.value().codePointCount(0, title.value().length());
        if (titleLength > ModelLimits.TITLE_CODE_POINTS) {
            throw new LimitValidationException(
                    "$.diagnostic.title",
                    "contains " + titleLength + " code points; maximum is "
                            + ModelLimits.TITLE_CODE_POINTS);
        }
        locations = Validation.required(locations, "$.diagnostic.locations");
        notes = Validation.required(notes, "$.diagnostic.notes");
        help = Validation.required(help, "$.diagnostic.help");
        trace = Validation.required(trace, "$.diagnostic.trace");
        evidence = Validation.required(evidence, "$.diagnostic.evidence");
        confidence = Validation.required(confidence, "$.diagnostic.confidence");
        children = Validation.required(children, "$.diagnostic.children");
        omissions = Validation.required(omissions, "$.diagnostic.omissions");

        Validation.size(locations.items(), ModelLimits.LOCATIONS_PER_NODE, "$.diagnostic.locations");
        Validation.size(notes.items(), ModelLimits.NOTES_PER_NODE, "$.diagnostic.notes");
        Validation.size(help.items(), ModelLimits.HELP_PER_NODE, "$.diagnostic.help");
        Validation.size(evidence.items(), ModelLimits.EVIDENCE_PER_NODE, "$.diagnostic.evidence");
        Validation.size(omissions.items(), ModelLimits.OMISSIONS_PER_SCOPE, "$.diagnostic.omissions");

        var evidenceIds = new HashSet<EvidenceId>();
        for (var index = 0; index < evidence.items().size(); index++) {
            var id = evidence.items().get(index).id();
            if (!evidenceIds.add(id)) {
                throw new ReferenceValidationException(
                        "$.diagnostic.evidence.items[" + index + "].id",
                        "duplicates node-local evidence ID '" + id.value() + "'");
            }
        }

        var locationIds = new HashSet<LocationId>();
        for (var index = 0; index < locations.items().size(); index++) {
            var location = locations.items().get(index);
            if (!locationIds.add(location.id())) {
                throw new ReferenceValidationException(
                        "$.diagnostic.locations.items[" + index + "].id",
                        "duplicates node-local location ID '" + location.id().value() + "'");
            }
            resolve(location.evidenceIds(), evidenceIds,
                    "$.diagnostic.locations.items[" + index + "].evidenceIds");
            if (location.excerpt().isPresent()) {
                var excerpt = location.excerpt().orElseThrow();
                for (var labelIndex = 0; labelIndex < excerpt.labels().items().size(); labelIndex++) {
                    resolve(
                            excerpt.labels().items().get(labelIndex).evidenceIds(),
                            evidenceIds,
                            "$.diagnostic.locations.items[" + index
                                    + "].excerpt.labels.items[" + labelIndex + "].evidenceIds");
                }
            }
        }
        for (var index = 0; index < notes.items().size(); index++) {
            resolve(notes.items().get(index).evidenceIds(), evidenceIds,
                    "$.diagnostic.notes.items[" + index + "].evidenceIds");
        }
        for (var index = 0; index < help.items().size(); index++) {
            resolve(help.items().get(index).evidenceIds(), evidenceIds,
                    "$.diagnostic.help.items[" + index + "].evidenceIds");
        }
        resolve(confidence.evidenceIds(), evidenceIds, "$.diagnostic.confidence.evidenceIds");

        if (trace.state() == TraceState.WRITE_FAILED && notes.items().isEmpty()) {
            throw new TraceValidationException(
                    "$.diagnostic.notes", "WRITE_FAILED requires a node-local explanatory note");
        }
    }

    private static void resolve(
            BoundedList<EvidenceId> references, HashSet<EvidenceId> knownIds, String path) {
        for (var index = 0; index < references.items().size(); index++) {
            var reference = references.items().get(index);
            if (!knownIds.contains(reference)) {
                throw new ReferenceValidationException(
                        path + ".items[" + index + "]",
                        "does not resolve node-local evidence ID '" + reference.value() + "'");
            }
        }
    }
}
