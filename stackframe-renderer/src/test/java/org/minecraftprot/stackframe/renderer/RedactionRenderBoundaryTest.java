package org.minecraftprot.stackframe.renderer;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.minecraftprot.stackframe.diagnostic.BoundedList;
import org.minecraftprot.stackframe.diagnostic.CandidateText;
import org.minecraftprot.stackframe.diagnostic.CatalogText;
import org.minecraftprot.stackframe.diagnostic.ConfidenceReference;
import org.minecraftprot.stackframe.diagnostic.CorrelationId;
import org.minecraftprot.stackframe.diagnostic.Diagnostic;
import org.minecraftprot.stackframe.diagnostic.DiagnosticCode;
import org.minecraftprot.stackframe.diagnostic.DiagnosticDocument;
import org.minecraftprot.stackframe.diagnostic.DiagnosticId;
import org.minecraftprot.stackframe.diagnostic.Excerpt;
import org.minecraftprot.stackframe.diagnostic.ExcerptLine;
import org.minecraftprot.stackframe.diagnostic.Label;
import org.minecraftprot.stackframe.diagnostic.LabelStyle;
import org.minecraftprot.stackframe.diagnostic.Location;
import org.minecraftprot.stackframe.diagnostic.LocationId;
import org.minecraftprot.stackframe.diagnostic.LocationKind;
import org.minecraftprot.stackframe.diagnostic.Note;
import org.minecraftprot.stackframe.diagnostic.NoteKind;
import org.minecraftprot.stackframe.diagnostic.RelatedDiagnostic;
import org.minecraftprot.stackframe.diagnostic.Relation;
import org.minecraftprot.stackframe.diagnostic.SchemaVersion;
import org.minecraftprot.stackframe.diagnostic.Severity;
import org.minecraftprot.stackframe.diagnostic.SourcePosition;
import org.minecraftprot.stackframe.diagnostic.SourceRange;
import org.minecraftprot.stackframe.diagnostic.TextOrigin;
import org.minecraftprot.stackframe.diagnostic.TraceSummary;
import org.minecraftprot.stackframe.redaction.RedactionPolicy;

final class RedactionRenderBoundaryTest {
    @TempDir Path root;

    @Test
    void nestedCausesLabelsAndExcerptsStayRedactedInPlainAndJson() throws IOException {
        var policy = new RedactionPolicy(root, Set.of("private-user"));
        var session = policy.newSession();
        var token = "Authorization: Bearer abc.def.ghi";
        var address = "alice@example.invalid";
        var excerpt = "password=world-save-secret";
        var childSecret = "private-user";
        var range = new SourceRange(new SourcePosition(1, 1),
                new SourcePosition(1, 2));
        var location = new Location(
                new LocationId("source"), LocationKind.SOURCE,
                session.transform(new CandidateText(root.resolve("server.properties").toString()),
                        RedactionPolicy.FieldKind.PATH, TextOrigin.EXTERNAL),
                Optional.empty(),
                Optional.of(new Excerpt(1,
                        BoundedList.of(List.of(new ExcerptLine(1,
                                session.transform(new CandidateText(excerpt),
                                        RedactionPolicy.FieldKind.EXCERPT,
                                        TextOrigin.EXTERNAL)))),
                        BoundedList.of(List.of(new Label(range, LabelStyle.PRIMARY,
                                session.transform(new CandidateText(address),
                                        RedactionPolicy.FieldKind.LABEL,
                                        TextOrigin.EXTERNAL),
                                BoundedList.empty()))))),
                BoundedList.empty());
        var child = new Diagnostic(Severity.NOTE, new DiagnosticCode("SF0001"),
                new CatalogText("diagnostic.generic", "Further details"),
                BoundedList.empty(),
                BoundedList.of(List.of(
                        new Note(NoteKind.CAUSE,
                                session.transform(new CandidateText(childSecret),
                                        RedactionPolicy.FieldKind.UNTRUSTED_MESSAGE,
                                        TextOrigin.EXTERNAL), BoundedList.empty()),
                        new Note(NoteKind.NOTE,
                                session.transform(new CandidateText("unclassified user text"),
                                        RedactionPolicy.FieldKind.UNTRUSTED_MESSAGE,
                                        TextOrigin.EXTERNAL), BoundedList.empty()))),
                BoundedList.empty(), TraceSummary.notApplicable(), BoundedList.empty(),
                ConfidenceReference.unassessed(), BoundedList.empty(), BoundedList.empty());
        var parent = new Diagnostic(Severity.ERROR, new DiagnosticCode("SF0001"),
                new CatalogText("diagnostic.generic", "Failure details withheld"),
                BoundedList.of(List.of(location)),
                BoundedList.of(List.of(new Note(NoteKind.CAUSE,
                        session.transform(new CandidateText(token),
                                RedactionPolicy.FieldKind.UNTRUSTED_MESSAGE,
                                TextOrigin.EXTERNAL), BoundedList.empty()))),
                BoundedList.empty(), TraceSummary.notApplicable(), BoundedList.empty(),
                ConfidenceReference.unassessed(),
                BoundedList.of(List.of(new RelatedDiagnostic(Relation.CAUSE, child))),
                BoundedList.empty());
        var document = new DiagnosticDocument(SchemaVersion.CURRENT,
                new DiagnosticId("redaction1"), new CorrelationId("REDACT1"),
                parent, session.notices(), BoundedList.empty());

        var plain = DiagnosticRenderer.renderToString(document,
                RenderOptions.plain(RenderWidth.unknown()));
        var json = StructuredDiagnosticRenderer.toJson(document,
                Instant.parse("2026-09-25T00:00:00Z"));
        for (var output : List.of(plain, json)) {
            for (var secret : List.of(token, address, excerpt, childSecret,
                    root.toString())) {
                assertFalse(output.contains(secret), output);
            }
            assertTrue(output.contains("redacted"), output);
            assertTrue(output.contains("omitted"), output);
        }
    }
}
