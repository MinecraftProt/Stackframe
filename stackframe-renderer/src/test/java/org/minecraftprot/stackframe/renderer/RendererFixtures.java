package org.minecraftprot.stackframe.renderer;

import java.util.List;
import java.util.Optional;
import org.minecraftprot.stackframe.diagnostic.BoundedList;
import org.minecraftprot.stackframe.diagnostic.CatalogText;
import org.minecraftprot.stackframe.diagnostic.ConfidenceReference;
import org.minecraftprot.stackframe.diagnostic.CorrelationId;
import org.minecraftprot.stackframe.diagnostic.Diagnostic;
import org.minecraftprot.stackframe.diagnostic.DiagnosticCode;
import org.minecraftprot.stackframe.diagnostic.DiagnosticDocument;
import org.minecraftprot.stackframe.diagnostic.DiagnosticId;
import org.minecraftprot.stackframe.diagnostic.DisplayText;
import org.minecraftprot.stackframe.diagnostic.EvidenceId;
import org.minecraftprot.stackframe.diagnostic.EvidenceKind;
import org.minecraftprot.stackframe.diagnostic.EvidenceReference;
import org.minecraftprot.stackframe.diagnostic.Excerpt;
import org.minecraftprot.stackframe.diagnostic.ExcerptLine;
import org.minecraftprot.stackframe.diagnostic.Help;
import org.minecraftprot.stackframe.diagnostic.HelpKind;
import org.minecraftprot.stackframe.diagnostic.Label;
import org.minecraftprot.stackframe.diagnostic.LabelStyle;
import org.minecraftprot.stackframe.diagnostic.Location;
import org.minecraftprot.stackframe.diagnostic.LocationId;
import org.minecraftprot.stackframe.diagnostic.LocationKind;
import org.minecraftprot.stackframe.diagnostic.ModelPath;
import org.minecraftprot.stackframe.diagnostic.Note;
import org.minecraftprot.stackframe.diagnostic.NoteKind;
import org.minecraftprot.stackframe.diagnostic.Omission;
import org.minecraftprot.stackframe.diagnostic.OmissionReason;
import org.minecraftprot.stackframe.diagnostic.RedactionMarker;
import org.minecraftprot.stackframe.diagnostic.RedactionNotice;
import org.minecraftprot.stackframe.diagnostic.RelatedDiagnostic;
import org.minecraftprot.stackframe.diagnostic.Relation;
import org.minecraftprot.stackframe.diagnostic.SchemaVersion;
import org.minecraftprot.stackframe.diagnostic.Sensitivity;
import org.minecraftprot.stackframe.diagnostic.Severity;
import org.minecraftprot.stackframe.diagnostic.SourcePosition;
import org.minecraftprot.stackframe.diagnostic.SourceRange;
import org.minecraftprot.stackframe.diagnostic.TextDisposition;
import org.minecraftprot.stackframe.diagnostic.TextOrigin;
import org.minecraftprot.stackframe.diagnostic.TraceState;
import org.minecraftprot.stackframe.diagnostic.TraceSummary;

final class RendererFixtures {
    private static final EvidenceId EVIDENCE_ID = new EvidenceId("fact");

    private RendererFixtures() {
    }

    static DiagnosticDocument minimum() {
        return document(minimumDiagnostic());
    }

    static DiagnosticDocument full() {
        var evidence = evidence("validated registry content", "approved datapack source");
        var line = "\"type\": \"minecraft:unknown\"";
        var range = new SourceRange(new SourcePosition(12, 9), new SourcePosition(12, 28));
        var excerpt = new Excerpt(
                12,
                BoundedList.of(List.of(new ExcerptLine(12, text(line)))),
                BoundedList.of(List.of(new Label(
                        range,
                        LabelStyle.PRIMARY,
                        text("unknown registry value"),
                        BoundedList.of(List.of(EVIDENCE_ID))))));
        var firstLocation = new Location(
                new LocationId("datapack"),
                LocationKind.RESOURCE,
                DisplayText.generalized(
                        "world/datapacks/example/data/example/item.json",
                        TextOrigin.EXTERNAL,
                        Sensitivity.SERVER_SENSITIVE,
                        new RedactionMarker("PATH")),
                Optional.of(range),
                Optional.of(excerpt),
                BoundedList.of(List.of(EVIDENCE_ID)));
        var secondLocation = new Location(
                new LocationId("registry"),
                LocationKind.COMPONENT,
                text("item registry"),
                Optional.empty(),
                Optional.empty(),
                BoundedList.of(List.of(EVIDENCE_ID)));
        var child = new Diagnostic(
                Severity.NOTE,
                new DiagnosticCode("SF0001"),
                new CatalogText("diagnostic.decode", "registry lookup failed"),
                BoundedList.empty(),
                BoundedList.of(List.of(new Note(
                        NoteKind.NOTE,
                        text("the identifier was preserved in the debug record"),
                        BoundedList.empty()))),
                BoundedList.empty(),
                TraceSummary.notApplicable(),
                BoundedList.empty(),
                ConfidenceReference.unassessed(),
                BoundedList.empty(),
                BoundedList.empty());
        var root = new Diagnostic(
                Severity.ERROR,
                new DiagnosticCode("SF2003"),
                new CatalogText("diagnostic.datapack", "datapack validation failed"),
                BoundedList.of(List.of(firstLocation, secondLocation)),
                BoundedList.of(List.of(
                        new Note(
                                NoteKind.CAUSE,
                                text("no registry entry matches \"minecraft:unknown\""),
                                BoundedList.of(List.of(EVIDENCE_ID))),
                        new Note(
                                NoteKind.CONTEXT,
                                text("validation ran during world loading"),
                                BoundedList.of(List.of(EVIDENCE_ID))),
                        new Note(
                                NoteKind.NOTE,
                                text("the datapack was not applied"),
                                BoundedList.of(List.of(EVIDENCE_ID))))),
                BoundedList.of(List.of(new Help(
                        text("check the identifier and whether its providing mod is installed"),
                        HelpKind.ACTION,
                        BoundedList.of(List.of(EVIDENCE_ID))))),
                new TraceSummary(
                        TraceState.PRESERVED,
                        Optional.of(22),
                        2,
                        20,
                        1,
                        Optional.of(text("local diagnostic store")),
                        Optional.of(new DiagnosticId("record01"))),
                BoundedList.of(List.of(evidence)),
                ConfidenceReference.unassessed(),
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

    static DiagnosticDocument widthFixture() {
        var evidence = evidence("validated dependency metadata", null);
        var root = new Diagnostic(
                Severity.ERROR,
                new DiagnosticCode("SF3004"),
                new CatalogText("diagnostic.dependency", "mod dependency is missing"),
                BoundedList.of(List.of(new Location(
                        new LocationId("addon"),
                        LocationKind.FILE,
                        text("mods/example-addon.jar"),
                        Optional.empty(),
                        Optional.empty(),
                        BoundedList.of(List.of(EVIDENCE_ID))))),
                BoundedList.of(List.of(new Note(
                        NoteKind.CAUSE,
                        text("example-core version 3.0.0 is not installed"),
                        BoundedList.of(List.of(EVIDENCE_ID))))),
                BoundedList.of(List.of(new Help(
                        text("install a compatible example-core release, then restart the server"),
                        HelpKind.ACTION,
                        BoundedList.of(List.of(EVIDENCE_ID))))),
                new TraceSummary(
                        TraceState.PRESERVED,
                        Optional.of(31),
                        0,
                        31,
                        0,
                        Optional.empty(),
                        Optional.of(new DiagnosticId("record02"))),
                BoundedList.of(List.of(evidence)),
                ConfidenceReference.unassessed(),
                BoundedList.empty(),
                BoundedList.empty());
        return new DiagnosticDocument(
                SchemaVersion.CURRENT,
                new DiagnosticId("diag0002"),
                new CorrelationId("DEF456"),
                root,
                BoundedList.empty(),
                BoundedList.empty());
    }

    static DiagnosticDocument unicodeExcerpt() {
        var evidence = evidence("validated Unicode source", null);
        var source = "e\u0301 = 界 + 👩🏽‍💻";
        var label = new Label(
                new SourceRange(new SourcePosition(7, 6), new SourcePosition(7, 7)),
                LabelStyle.SECONDARY,
                text("wide"),
                BoundedList.of(List.of(EVIDENCE_ID)));
        var location = new Location(
                new LocationId("unicode"),
                LocationKind.SOURCE,
                text("unicode/example.txt"),
                Optional.empty(),
                Optional.of(new Excerpt(
                        7,
                        BoundedList.of(List.of(new ExcerptLine(7, text(source)))),
                        BoundedList.of(List.of(label)))),
                BoundedList.of(List.of(EVIDENCE_ID)));
        return document(diagnostic(
                Severity.WARNING,
                "SF2008",
                "unicode source needs review",
                BoundedList.of(List.of(location)),
                BoundedList.empty(),
                BoundedList.empty(),
                TraceSummary.notApplicable(),
                BoundedList.of(List.of(evidence)),
                BoundedList.empty(),
                BoundedList.empty()));
    }

    static DiagnosticDocument multilineExcerpt() {
        var evidence = evidence("validated multiline source", null);
        var label = new Label(
                new SourceRange(new SourcePosition(20, 3), new SourcePosition(21, 5)),
                LabelStyle.PRIMARY,
                text("value spans two lines"),
                BoundedList.of(List.of(EVIDENCE_ID)));
        var location = new Location(
                new LocationId("multiline"),
                LocationKind.SOURCE,
                text("data/example.json"),
                Optional.empty(),
                Optional.of(new Excerpt(
                        20,
                        BoundedList.of(List.of(
                                new ExcerptLine(20, text("\"first\": value,")),
                                new ExcerptLine(21, text("\"second\": value")))),
                        BoundedList.of(List.of(label)))),
                BoundedList.of(List.of(EVIDENCE_ID)));
        return document(diagnostic(
                Severity.ERROR,
                "SF2003",
                "multiline value is invalid",
                BoundedList.of(List.of(location)),
                BoundedList.empty(),
                BoundedList.empty(),
                TraceSummary.notApplicable(),
                BoundedList.of(List.of(evidence)),
                BoundedList.empty(),
                BoundedList.empty()));
    }

    static DiagnosticDocument omissionsAndWriteFailure() {
        var omittedLocations = BoundedList.<Location>withOmitted(List.of(), 2);
        var localOmission = new Omission(
                new ModelPath("$.root.locations"), 2, OmissionReason.COUNT_LIMIT);
        var root = diagnostic(
                Severity.ERROR,
                "SF0001",
                "generic diagnostic is bounded",
                omittedLocations,
                BoundedList.of(List.of(new Note(
                        NoteKind.NOTE,
                        text("the complete trace could not be written"),
                        BoundedList.empty()))),
                BoundedList.empty(),
                new TraceSummary(
                        TraceState.WRITE_FAILED,
                        Optional.of(4),
                        1,
                        3,
                        2,
                        Optional.empty(),
                        Optional.empty()),
                BoundedList.empty(),
                BoundedList.empty(),
                BoundedList.of(List.of(localOmission)));
        var notices = BoundedList.withOmitted(
                List.of(new RedactionNotice(
                        new RedactionMarker("TOKEN"),
                        TextDisposition.REDACTED,
                        3)),
                1);
        return new DiagnosticDocument(
                SchemaVersion.CURRENT,
                new DiagnosticId("diag0003"),
                new CorrelationId("G7H8J9"),
                root,
                notices,
                BoundedList.of(List.of(new Omission(
                        new ModelPath("$.redactions"), 1, OmissionReason.BYTE_BUDGET))));
    }

    static DiagnosticDocument pathological() {
        var notes = new java.util.ArrayList<Note>();
        for (var index = 0; index < 32; index++) {
            notes.add(new Note(
                    NoteKind.NOTE,
                    text(("note-" + index + " ").repeat(450)),
                    BoundedList.empty()));
        }
        return document(diagnostic(
                Severity.ERROR,
                "SF0001",
                "bounded pathological diagnostic",
                BoundedList.empty(),
                BoundedList.of(notes),
                BoundedList.empty(),
                TraceSummary.notApplicable(),
                BoundedList.empty(),
                BoundedList.empty(),
                BoundedList.empty()));
    }

    static DiagnosticDocument title(String title) {
        return document(diagnostic(
                Severity.ERROR,
                "SF0001",
                title,
                BoundedList.empty(),
                BoundedList.empty(),
                BoundedList.empty(),
                TraceSummary.notApplicable(),
                BoundedList.empty(),
                BoundedList.empty(),
                BoundedList.empty()));
    }

    static DiagnosticDocument location(String display) {
        var location = new Location(
                new LocationId("location"),
                LocationKind.OTHER,
                text(display),
                Optional.empty(),
                Optional.empty(),
                BoundedList.empty());
        return document(diagnostic(
                Severity.ERROR,
                "SF0001",
                "location rendering failed",
                BoundedList.of(List.of(location)),
                BoundedList.empty(),
                BoundedList.empty(),
                TraceSummary.notApplicable(),
                BoundedList.empty(),
                BoundedList.empty(),
                BoundedList.empty()));
    }

    static DiagnosticDocument uncertainExcerpt() {
        var evidence = evidence("validated uncertain source", null);
        var label = new Label(
                new SourceRange(new SourcePosition(3, 1), new SourcePosition(3, 2)),
                LabelStyle.PRIMARY,
                text("uncertain cluster"),
                BoundedList.of(List.of(EVIDENCE_ID)));
        var location = new Location(
                new LocationId("uncertain"),
                LocationKind.SOURCE,
                text("uncertain.txt"),
                Optional.empty(),
                Optional.of(new Excerpt(
                        3,
                        BoundedList.of(List.of(new ExcerptLine(3, text("a\u200Db")))),
                        BoundedList.of(List.of(label)))),
                BoundedList.of(List.of(EVIDENCE_ID)));
        return document(diagnostic(
                Severity.ERROR,
                "SF2003",
                "source alignment is uncertain",
                BoundedList.of(List.of(location)),
                BoundedList.empty(),
                BoundedList.empty(),
                TraceSummary.notApplicable(),
                BoundedList.of(List.of(evidence)),
                BoundedList.empty(),
                BoundedList.empty()));
    }

    static DiagnosticDocument blankExcerpt() {
        var location = new Location(
                new LocationId("blank"),
                LocationKind.SOURCE,
                text("blank.txt"),
                Optional.empty(),
                Optional.of(new Excerpt(
                        1,
                        BoundedList.of(List.of(
                                new ExcerptLine(1, text("")),
                                new ExcerptLine(2, text("value ")),
                                new ExcerptLine(3, text(" ".repeat(50))))),
                        BoundedList.empty())),
                BoundedList.empty());
        return document(diagnostic(
                Severity.ERROR,
                "SF2003",
                "blank source is invalid",
                BoundedList.of(List.of(location)),
                BoundedList.empty(),
                BoundedList.empty(),
                TraceSummary.notApplicable(),
                BoundedList.empty(),
                BoundedList.empty(),
                BoundedList.empty()));
    }

    static DiagnosticDocument splitGraphemeLabel() {
        var source = "e\u0301x";
        var label = new Label(
                new SourceRange(new SourcePosition(4, 2), new SourcePosition(4, 3)),
                LabelStyle.PRIMARY,
                text("combining mark boundary"),
                BoundedList.empty());
        var location = new Location(
                new LocationId("grapheme"),
                LocationKind.SOURCE,
                text("grapheme.txt"),
                Optional.empty(),
                Optional.of(new Excerpt(
                        4,
                        BoundedList.of(List.of(new ExcerptLine(4, text(source)))),
                        BoundedList.of(List.of(label)))),
                BoundedList.empty());
        return document(diagnostic(
                Severity.ERROR,
                "SF2003",
                "grapheme boundary is uncertain",
                BoundedList.of(List.of(location)),
                BoundedList.empty(),
                BoundedList.empty(),
                TraceSummary.notApplicable(),
                BoundedList.empty(),
                BoundedList.empty(),
                BoundedList.empty()));
    }

    private static DiagnosticDocument document(Diagnostic root) {
        return new DiagnosticDocument(
                SchemaVersion.CURRENT,
                new DiagnosticId("diag0001"),
                new CorrelationId("ABC123"),
                root,
                BoundedList.empty(),
                BoundedList.empty());
    }

    private static Diagnostic minimumDiagnostic() {
        return diagnostic(
                Severity.ERROR,
                "SF0001",
                "operation failed",
                BoundedList.empty(),
                BoundedList.empty(),
                BoundedList.empty(),
                TraceSummary.notApplicable(),
                BoundedList.empty(),
                BoundedList.empty(),
                BoundedList.empty());
    }

    private static Diagnostic diagnostic(
            Severity severity,
            String code,
            String title,
            BoundedList<Location> locations,
            BoundedList<Note> notes,
            BoundedList<Help> help,
            TraceSummary trace,
            BoundedList<EvidenceReference> evidence,
            BoundedList<RelatedDiagnostic> children,
            BoundedList<Omission> omissions) {
        return new Diagnostic(
                severity,
                new DiagnosticCode(code),
                new CatalogText("diagnostic.fixture", title),
                locations,
                notes,
                help,
                trace,
                evidence,
                ConfidenceReference.unassessed(),
                children,
                omissions);
    }

    private static EvidenceReference evidence(String summary, String source) {
        return new EvidenceReference(
                EVIDENCE_ID,
                EvidenceKind.VALIDATED_CONTENT,
                text(summary),
                source == null ? Optional.empty() : Optional.of(text(source)));
    }

    private static DisplayText text(String value) {
        return DisplayText.visible(value, TextOrigin.GENERATED);
    }
}
