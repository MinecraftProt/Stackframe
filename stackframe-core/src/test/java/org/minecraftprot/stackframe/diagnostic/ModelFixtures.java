package org.minecraftprot.stackframe.diagnostic;

import java.util.List;
import java.util.Optional;

final class ModelFixtures {
    static final EvidenceId EVIDENCE_ID = new EvidenceId("fact");

    private ModelFixtures() {
    }

    static DisplayText text(String value) {
        return DisplayText.visible(value, TextOrigin.GENERATED);
    }

    static EvidenceReference evidence() {
        return new EvidenceReference(
                EVIDENCE_ID,
                EvidenceKind.VALIDATED_CONTENT,
                text("validated configuration value"),
                Optional.of(text("approved configuration source")));
    }

    static Diagnostic minimalDiagnostic() {
        return diagnostic(
                Severity.ERROR,
                BoundedList.empty(),
                BoundedList.empty(),
                BoundedList.empty(),
                TraceSummary.notApplicable(),
                BoundedList.empty(),
                ConfidenceReference.unassessed(),
                BoundedList.empty(),
                BoundedList.empty());
    }

    static Diagnostic diagnostic(
            Severity severity,
            BoundedList<Location> locations,
            BoundedList<Note> notes,
            BoundedList<Help> help,
            TraceSummary trace,
            BoundedList<EvidenceReference> evidence,
            ConfidenceReference confidence,
            BoundedList<RelatedDiagnostic> children,
            BoundedList<Omission> omissions) {
        return new Diagnostic(
                severity,
                new DiagnosticCode("SF0001"),
                new CatalogText("diagnostic.minimum", "operation failed"),
                locations,
                notes,
                help,
                trace,
                evidence,
                confidence,
                children,
                omissions);
    }

    static DiagnosticDocument document(Diagnostic root) {
        return new DiagnosticDocument(
                SchemaVersion.CURRENT,
                new DiagnosticId("diag0001"),
                new CorrelationId("ABC123"),
                root,
                BoundedList.empty(),
                BoundedList.empty());
    }

    static DiagnosticDocument fullDocument() {
        var evidence = evidence();
        var range = new SourceRange(
                new SourcePosition(10, 8),
                new SourcePosition(10, 13));
        var label = new Label(
                range,
                LabelStyle.PRIMARY,
                text("port value"),
                BoundedList.of(List.of(EVIDENCE_ID)));
        var excerpt = new Excerpt(
                10,
                BoundedList.of(List.of(new ExcerptLine(10, text("port = 25565")))),
                BoundedList.of(List.of(label)));
        var location = new Location(
                new LocationId("server-port"),
                LocationKind.CONFIGURATION,
                DisplayText.generalized(
                        "server configuration key",
                        TextOrigin.EXTERNAL,
                        Sensitivity.SERVER_SENSITIVE,
                        new RedactionMarker("PATH")),
                Optional.of(range),
                Optional.of(excerpt),
                BoundedList.of(List.of(EVIDENCE_ID)));
        var note = new Note(
                NoteKind.CAUSE,
                text("the configured endpoint is unavailable"),
                BoundedList.of(List.of(EVIDENCE_ID)));
        var help = new Help(
                text("inspect the configured endpoint"),
                HelpKind.INSPECT,
                BoundedList.of(List.of(EVIDENCE_ID)));
        var confidence = new ConfidenceReference(
                Optional.of(new OpaqueIdentifier("high")),
                Optional.of(new OpaqueIdentifier("network.bind")),
                BoundedList.of(List.of(EVIDENCE_ID)),
                Optional.of(new OpaqueIdentifier("arbiter-1")));
        var child = diagnostic(
                Severity.NOTE,
                BoundedList.empty(),
                BoundedList.empty(),
                BoundedList.empty(),
                TraceSummary.notApplicable(),
                BoundedList.empty(),
                ConfidenceReference.unassessed(),
                BoundedList.empty(),
                BoundedList.empty());
        var root = diagnostic(
                Severity.ERROR,
                BoundedList.of(List.of(location)),
                BoundedList.of(List.of(note)),
                BoundedList.of(List.of(help)),
                new TraceSummary(
                        TraceState.PRESERVED,
                        Optional.of(3),
                        1,
                        2,
                        1,
                        Optional.of(text("local diagnostic store")),
                        Optional.of(new DiagnosticId("record01"))),
                BoundedList.of(List.of(evidence)),
                confidence,
                BoundedList.of(List.of(new RelatedDiagnostic(Relation.CAUSE, child))),
                BoundedList.empty());
        return new DiagnosticDocument(
                SchemaVersion.CURRENT,
                new DiagnosticId("diag0001"),
                new CorrelationId("ABC123"),
                root,
                BoundedList.of(List.of(new RedactionNotice(
                        new RedactionMarker("PATH"),
                        TextDisposition.GENERALIZED,
                        1))),
                BoundedList.empty());
    }
}
