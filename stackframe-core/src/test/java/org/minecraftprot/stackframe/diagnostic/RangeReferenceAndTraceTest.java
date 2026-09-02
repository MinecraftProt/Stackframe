package org.minecraftprot.stackframe.diagnostic;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class RangeReferenceAndTraceTest {
    @Test
    void sourceRangesAreOneBasedOrderedAndEndExclusive() {
        assertDoesNotThrow(() -> new SourceRange(
                new SourcePosition(1, 1), new SourcePosition(1, 2)));
        assertDoesNotThrow(() -> new SourceRange(
                new SourcePosition(1, 2), new SourcePosition(2, 1)));
        assertThrows(RangeValidationException.class, () -> new SourcePosition(0, 1));
        assertThrows(RangeValidationException.class, () -> new SourcePosition(1, 0));
        assertThrows(RangeValidationException.class, () -> new SourceRange(
                new SourcePosition(1, 1), new SourcePosition(1, 1)));
        assertThrows(RangeValidationException.class, () -> new SourceRange(
                new SourcePosition(2, 1), new SourcePosition(1, 2)));
    }

    @Test
    void excerptRequiresContiguousAbsoluteLineNumbers() {
        assertDoesNotThrow(() -> excerpt(List.of(
                new ExcerptLine(10, ModelFixtures.text("one")),
                new ExcerptLine(11, ModelFixtures.text("two"))), List.of()));
        assertThrows(RangeValidationException.class, () -> excerpt(List.of(
                new ExcerptLine(11, ModelFixtures.text("one"))), List.of()));
        assertThrows(RangeValidationException.class, () -> excerpt(List.of(
                new ExcerptLine(10, ModelFixtures.text("one")),
                new ExcerptLine(12, ModelFixtures.text("two"))), List.of()));
    }

    @Test
    void labelRangesResolveAgainstFinalPostRedactionText() {
        var valid = label(new SourceRange(
                new SourcePosition(10, 1), new SourcePosition(10, 4)));
        assertDoesNotThrow(() -> excerpt(
                List.of(new ExcerptLine(10, ModelFixtures.text("abc"))),
                List.of(valid)));
        assertThrows(RangeValidationException.class, () -> excerpt(
                List.of(new ExcerptLine(10, ModelFixtures.text("abc"))),
                List.of(label(new SourceRange(
                        new SourcePosition(9, 1), new SourcePosition(10, 1))))));
        assertThrows(RangeValidationException.class, () -> excerpt(
                List.of(new ExcerptLine(10, ModelFixtures.text("abc"))),
                List.of(label(new SourceRange(
                        new SourcePosition(10, 3), new SourcePosition(10, 5))))));
    }

    @Test
    void labelColumnsCountUnicodeCodePointsInsteadOfUtf16Units() {
        var line = new ExcerptLine(10, ModelFixtures.text("😀x"));
        var emoji = label(new SourceRange(
                new SourcePosition(10, 1), new SourcePosition(10, 2)));
        var finalBoundary = label(new SourceRange(
                new SourcePosition(10, 2), new SourcePosition(10, 3)));

        assertDoesNotThrow(() -> excerpt(List.of(line), List.of(emoji, finalBoundary)));
        assertThrows(RangeValidationException.class, () -> excerpt(
                List.of(line),
                List.of(label(new SourceRange(
                        new SourcePosition(10, 2), new SourcePosition(10, 4))))));
    }

    @Test
    void overlappingLabelsRemainInProducerOrder() {
        var primary = label(new SourceRange(
                new SourcePosition(10, 1), new SourcePosition(10, 4)));
        var secondary = new Label(
                new SourceRange(new SourcePosition(10, 2), new SourcePosition(10, 3)),
                LabelStyle.SECONDARY,
                ModelFixtures.text("secondary"),
                BoundedList.of(List.of(ModelFixtures.EVIDENCE_ID)));

        var excerpt = excerpt(
                List.of(new ExcerptLine(10, ModelFixtures.text("abc"))),
                List.of(primary, secondary));

        assertEquals(List.of(LabelStyle.PRIMARY, LabelStyle.SECONDARY),
                excerpt.labels().items().stream().map(Label::style).toList());
    }

    @Test
    void duplicateLocalDefinitionsAreRejected() {
        var duplicateEvidence = new EvidenceReference(
                ModelFixtures.EVIDENCE_ID,
                EvidenceKind.OTHER,
                ModelFixtures.text("duplicate"),
                Optional.empty());
        assertThrows(ReferenceValidationException.class, () -> ModelFixtures.diagnostic(
                Severity.ERROR,
                BoundedList.empty(),
                BoundedList.empty(),
                BoundedList.empty(),
                TraceSummary.notApplicable(),
                BoundedList.of(List.of(ModelFixtures.evidence(), duplicateEvidence)),
                ConfidenceReference.unassessed(),
                BoundedList.empty(),
                BoundedList.empty()));

        var first = location("same", BoundedList.empty(), Optional.empty());
        var second = location("same", BoundedList.empty(), Optional.empty());
        assertThrows(ReferenceValidationException.class, () -> ModelFixtures.diagnostic(
                Severity.ERROR,
                BoundedList.of(List.of(first, second)),
                BoundedList.empty(),
                BoundedList.empty(),
                TraceSummary.notApplicable(),
                BoundedList.empty(),
                ConfidenceReference.unassessed(),
                BoundedList.empty(),
                BoundedList.empty()));
    }

    @ParameterizedTest
    @MethodSource("unresolvedReferenceMutations")
    void everyNodeLocalEvidenceReferenceMustResolve(
            Function<DiagnosticParts, DiagnosticParts> mutation) {
        var parts = DiagnosticParts.valid();
        var invalid = mutation.apply(parts);

        assertThrows(ReferenceValidationException.class, invalid::build);
    }

    @Test
    void helpAlwaysRetainsEvidence() {
        assertThrows(ReferenceValidationException.class, () -> new Help(
                ModelFixtures.text("inspect"),
                HelpKind.INSPECT,
                BoundedList.empty()));
    }

    @Test
    void traceAccountingAndPreservationStateAreValidated() {
        assertDoesNotThrow(() -> new TraceSummary(
                TraceState.PRESERVED,
                Optional.of(3),
                1,
                2,
                Integer.MAX_VALUE,
                Optional.of(ModelFixtures.text("local store")),
                Optional.empty()));
        assertThrows(TraceValidationException.class, () -> new TraceSummary(
                TraceState.PRESERVED,
                Optional.of(4),
                1,
                2,
                0,
                Optional.of(ModelFixtures.text("local store")),
                Optional.empty()));
        assertThrows(TraceValidationException.class, () -> new TraceSummary(
                TraceState.PRESERVED,
                Optional.empty(),
                0,
                0,
                0,
                Optional.empty(),
                Optional.empty()));
        assertThrows(TraceValidationException.class, () -> new TraceSummary(
                TraceState.WRITE_FAILED,
                Optional.empty(),
                0,
                0,
                0,
                Optional.of(ModelFixtures.text("not available")),
                Optional.empty()));
        assertThrows(TraceValidationException.class, () -> new TraceSummary(
                TraceState.NOT_APPLICABLE,
                Optional.of(1),
                1,
                0,
                0,
                Optional.empty(),
                Optional.empty()));
        assertThrows(TraceValidationException.class, () -> new TraceSummary(
                TraceState.PRESERVED,
                Optional.empty(),
                -1,
                0,
                0,
                Optional.of(ModelFixtures.text("local store")),
                Optional.empty()));
    }

    @Test
    void traceRecordIdIsPresentOnlyWhenDifferentFromRootId() {
        var trace = new TraceSummary(
                TraceState.PRESERVED,
                Optional.empty(),
                0,
                0,
                0,
                Optional.empty(),
                Optional.of(new DiagnosticId("diag0001")));
        var root = ModelFixtures.diagnostic(
                Severity.ERROR,
                BoundedList.empty(),
                BoundedList.empty(),
                BoundedList.empty(),
                trace,
                BoundedList.empty(),
                ConfidenceReference.unassessed(),
                BoundedList.empty(),
                BoundedList.empty());

        assertThrows(TraceValidationException.class, () -> ModelFixtures.document(root));
    }

    @Test
    void writeFailureRequiresNodeLocalExplanatoryNote() {
        var trace = new TraceSummary(
                TraceState.WRITE_FAILED,
                Optional.empty(),
                0,
                0,
                0,
                Optional.empty(),
                Optional.empty());
        assertThrows(TraceValidationException.class, () -> ModelFixtures.diagnostic(
                Severity.ERROR,
                BoundedList.empty(),
                BoundedList.empty(),
                BoundedList.empty(),
                trace,
                BoundedList.empty(),
                ConfidenceReference.unassessed(),
                BoundedList.empty(),
                BoundedList.empty()));
        assertDoesNotThrow(() -> ModelFixtures.diagnostic(
                Severity.ERROR,
                BoundedList.empty(),
                BoundedList.of(List.of(new Note(
                        NoteKind.NOTE,
                        ModelFixtures.text("the complete trace could not be written"),
                        BoundedList.empty()))),
                BoundedList.empty(),
                trace,
                BoundedList.empty(),
                ConfidenceReference.unassessed(),
                BoundedList.empty(),
                BoundedList.empty()));
    }

    private static Stream<Function<DiagnosticParts, DiagnosticParts>> unresolvedReferenceMutations() {
        var missing = new EvidenceId("missing");
        return Stream.of(
                parts -> parts.withLocation(location(
                        "location", BoundedList.of(List.of(missing)), Optional.empty())),
                parts -> parts.withLocation(location(
                        "location",
                        BoundedList.empty(),
                        Optional.of(excerpt(
                                List.of(new ExcerptLine(10, ModelFixtures.text("abc"))),
                                List.of(new Label(
                                        new SourceRange(
                                                new SourcePosition(10, 1),
                                                new SourcePosition(10, 2)),
                                        LabelStyle.PRIMARY,
                                        ModelFixtures.text("label"),
                                        BoundedList.of(List.of(missing)))))))),
                parts -> parts.withNote(new Note(
                        NoteKind.NOTE, ModelFixtures.text("note"), BoundedList.of(List.of(missing)))),
                parts -> parts.withHelp(new Help(
                        ModelFixtures.text("inspect"),
                        HelpKind.INSPECT,
                        BoundedList.of(List.of(missing)))),
                parts -> parts.withConfidence(new ConfidenceReference(
                        Optional.empty(),
                        Optional.empty(),
                        BoundedList.of(List.of(missing)),
                        Optional.empty())));
    }

    private static Excerpt excerpt(List<ExcerptLine> lines, List<Label> labels) {
        return new Excerpt(10, BoundedList.of(lines), BoundedList.of(labels));
    }

    private static Label label(SourceRange range) {
        return new Label(
                range,
                LabelStyle.PRIMARY,
                ModelFixtures.text("primary"),
                BoundedList.of(List.of(ModelFixtures.EVIDENCE_ID)));
    }

    private static Location location(
            String id, BoundedList<EvidenceId> evidenceIds, Optional<Excerpt> excerpt) {
        return new Location(
                new LocationId(id),
                LocationKind.OTHER,
                ModelFixtures.text(id),
                Optional.empty(),
                excerpt,
                evidenceIds);
    }

    private record DiagnosticParts(
            Location location,
            Note note,
            Help help,
            ConfidenceReference confidence) {
        static DiagnosticParts valid() {
            return new DiagnosticParts(
                    RangeReferenceAndTraceTest.location(
                            "location",
                            BoundedList.of(List.of(ModelFixtures.EVIDENCE_ID)),
                            Optional.<Excerpt>empty()),
                    new Note(
                            NoteKind.NOTE,
                            ModelFixtures.text("note"),
                            BoundedList.of(List.of(ModelFixtures.EVIDENCE_ID))),
                    new Help(
                            ModelFixtures.text("inspect"),
                            HelpKind.INSPECT,
                            BoundedList.of(List.of(ModelFixtures.EVIDENCE_ID))),
                    new ConfidenceReference(
                            Optional.empty(),
                            Optional.empty(),
                            BoundedList.of(List.of(ModelFixtures.EVIDENCE_ID)),
                            Optional.empty()));
        }

        DiagnosticParts withLocation(Location replacement) {
            return new DiagnosticParts(replacement, note, help, confidence);
        }

        DiagnosticParts withNote(Note replacement) {
            return new DiagnosticParts(location, replacement, help, confidence);
        }

        DiagnosticParts withHelp(Help replacement) {
            return new DiagnosticParts(location, note, replacement, confidence);
        }

        DiagnosticParts withConfidence(ConfidenceReference replacement) {
            return new DiagnosticParts(location, note, help, replacement);
        }

        Diagnostic build() {
            return ModelFixtures.diagnostic(
                    Severity.ERROR,
                    BoundedList.of(List.of(location)),
                    BoundedList.of(List.of(note)),
                    BoundedList.of(List.of(help)),
                    TraceSummary.notApplicable(),
                    BoundedList.of(List.of(ModelFixtures.evidence())),
                    confidence,
                    BoundedList.empty(),
                    BoundedList.empty());
        }
    }
}
