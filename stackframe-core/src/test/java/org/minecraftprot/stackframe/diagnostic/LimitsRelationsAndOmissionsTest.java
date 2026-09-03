package org.minecraftprot.stackframe.diagnostic;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.IntFunction;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class LimitsRelationsAndOmissionsTest {
    @Test
    void acceptsEveryExactCollectionBoundary() {
        var evidence = evidence(ModelLimits.EVIDENCE_PER_NODE, index -> "e" + index);
        var evidenceIds = evidence.stream().map(EvidenceReference::id).toList();
        var locations = IntStream.range(0, ModelLimits.LOCATIONS_PER_NODE)
                .mapToObj(index -> new Location(
                        new LocationId("l" + index),
                        LocationKind.OTHER,
                        ModelFixtures.text("location " + index),
                        Optional.empty(),
                        Optional.empty(),
                        BoundedList.empty()))
                .toList();
        var notes = repeated(
                ModelLimits.NOTES_PER_NODE,
                index -> new Note(NoteKind.NOTE, ModelFixtures.text("note"), BoundedList.empty()));
        var help = repeated(
                ModelLimits.HELP_PER_NODE,
                index -> new Help(
                        ModelFixtures.text("inspect"),
                        HelpKind.INSPECT,
                        BoundedList.of(List.of(evidenceIds.getFirst()))));

        var root = ModelFixtures.diagnostic(
                Severity.ERROR,
                BoundedList.of(locations),
                BoundedList.of(notes),
                BoundedList.of(help),
                TraceSummary.notApplicable(),
                BoundedList.of(evidence),
                ConfidenceReference.unassessed(),
                BoundedList.empty(),
                BoundedList.empty());
        var notices = repeated(
                ModelLimits.REDACTION_NOTICES,
                index -> new RedactionNotice(
                        new RedactionMarker("PATH"),
                        TextDisposition.REDACTED,
                        1));

        assertDoesNotThrow(() -> new DiagnosticDocument(
                SchemaVersion.CURRENT,
                new DiagnosticId("diag0001"),
                new CorrelationId("ABC123"),
                root,
                BoundedList.of(notices),
                BoundedList.empty()));

        var lines = IntStream.range(0, ModelLimits.EXCERPT_LINES)
                .mapToObj(index -> new ExcerptLine(index + 1, ModelFixtures.text("x")))
                .toList();
        var labels = repeated(
                ModelLimits.LABELS_PER_EXCERPT,
                index -> new Label(
                        new SourceRange(new SourcePosition(1, 1), new SourcePosition(1, 2)),
                        LabelStyle.PRIMARY,
                        ModelFixtures.text("label"),
                        BoundedList.empty()));
        assertDoesNotThrow(() -> new Excerpt(
                1, BoundedList.of(lines), BoundedList.of(labels)));

        var omissionRecords = repeated(
                ModelLimits.OMISSIONS_PER_SCOPE,
                index -> new Omission(
                        new ModelPath("$.root.locations"),
                        1,
                        OmissionReason.COUNT_LIMIT));
        assertDoesNotThrow(() -> ModelFixtures.diagnostic(
                Severity.ERROR,
                BoundedList.empty(),
                BoundedList.empty(),
                BoundedList.empty(),
                TraceSummary.notApplicable(),
                BoundedList.empty(),
                ConfidenceReference.unassessed(),
                BoundedList.empty(),
                BoundedList.of(omissionRecords)));
    }

    @Test
    void rejectsEachCollectionOneAboveItsSchemaMaximum() {
        assertThrows(LimitValidationException.class, () -> ModelFixtures.diagnostic(
                Severity.ERROR,
                BoundedList.of(repeated(ModelLimits.LOCATIONS_PER_NODE + 1, index -> new Location(
                        new LocationId("l" + index),
                        LocationKind.OTHER,
                        ModelFixtures.text("location"),
                        Optional.empty(),
                        Optional.empty(),
                        BoundedList.empty()))),
                BoundedList.empty(),
                BoundedList.empty(),
                TraceSummary.notApplicable(),
                BoundedList.empty(),
                ConfidenceReference.unassessed(),
                BoundedList.empty(),
                BoundedList.empty()));
        assertThrows(LimitValidationException.class, () -> new Excerpt(
                1,
                BoundedList.of(repeated(
                        ModelLimits.EXCERPT_LINES + 1,
                        index -> new ExcerptLine(index + 1, ModelFixtures.text("x")))),
                BoundedList.empty()));
        assertThrows(LimitValidationException.class, () -> new Excerpt(
                1,
                BoundedList.of(List.of(new ExcerptLine(1, ModelFixtures.text("x")))),
                BoundedList.of(repeated(ModelLimits.LABELS_PER_EXCERPT + 1, index -> new Label(
                        new SourceRange(new SourcePosition(1, 1), new SourcePosition(1, 2)),
                        LabelStyle.PRIMARY,
                        ModelFixtures.text("label"),
                        BoundedList.empty())))));
        assertThrows(LimitValidationException.class, () -> ModelFixtures.diagnostic(
                Severity.ERROR,
                BoundedList.empty(),
                BoundedList.of(repeated(
                        ModelLimits.NOTES_PER_NODE + 1,
                        index -> new Note(
                                NoteKind.NOTE, ModelFixtures.text("note"), BoundedList.empty()))),
                BoundedList.empty(),
                TraceSummary.notApplicable(),
                BoundedList.empty(),
                ConfidenceReference.unassessed(),
                BoundedList.empty(),
                BoundedList.empty()));
        assertThrows(LimitValidationException.class, () -> ModelFixtures.diagnostic(
                Severity.ERROR,
                BoundedList.empty(),
                BoundedList.empty(),
                BoundedList.of(repeated(
                        ModelLimits.HELP_PER_NODE + 1,
                        index -> new Help(
                                ModelFixtures.text("help"),
                                HelpKind.INSPECT,
                                BoundedList.of(List.of(ModelFixtures.EVIDENCE_ID))))),
                TraceSummary.notApplicable(),
                BoundedList.of(List.of(ModelFixtures.evidence())),
                ConfidenceReference.unassessed(),
                BoundedList.empty(),
                BoundedList.empty()));
        assertThrows(LimitValidationException.class, () -> ModelFixtures.diagnostic(
                Severity.ERROR,
                BoundedList.empty(),
                BoundedList.empty(),
                BoundedList.empty(),
                TraceSummary.notApplicable(),
                BoundedList.of(evidence(ModelLimits.EVIDENCE_PER_NODE + 1, index -> "e" + index)),
                ConfidenceReference.unassessed(),
                BoundedList.empty(),
                BoundedList.empty()));
        assertThrows(LimitValidationException.class, () -> new DiagnosticDocument(
                SchemaVersion.CURRENT,
                new DiagnosticId("diag0001"),
                new CorrelationId("ABC123"),
                ModelFixtures.minimalDiagnostic(),
                BoundedList.of(repeated(
                        ModelLimits.REDACTION_NOTICES + 1,
                        index -> new RedactionNotice(
                                new RedactionMarker("PATH"),
                                TextDisposition.REDACTED,
                                1))),
                BoundedList.empty()));
        assertThrows(LimitValidationException.class, () -> ModelFixtures.diagnostic(
                Severity.ERROR,
                BoundedList.empty(),
                BoundedList.empty(),
                BoundedList.empty(),
                TraceSummary.notApplicable(),
                BoundedList.empty(),
                ConfidenceReference.unassessed(),
                BoundedList.empty(),
                BoundedList.of(repeated(
                        ModelLimits.OMISSIONS_PER_SCOPE + 1,
                        index -> new Omission(
                                new ModelPath("$.root.locations"),
                                1,
                                OmissionReason.COUNT_LIMIT)))));
    }

    @Test
    void acceptsDepthEightAndRejectsDepthNine() {
        assertDoesNotThrow(() -> ModelFixtures.document(chain(ModelLimits.DIAGNOSTIC_DEPTH)));
        assertThrows(RelationValidationException.class,
                () -> ModelFixtures.document(chain(ModelLimits.DIAGNOSTIC_DEPTH + 1)));
    }

    @Test
    void acceptsSixtyFourNodesAndRejectsSixtyFive() {
        assertDoesNotThrow(() -> ModelFixtures.document(rootWithLeafChildren(
                ModelLimits.DIAGNOSTIC_NODES - 1)));
        assertThrows(LimitValidationException.class, () -> ModelFixtures.document(
                rootWithLeafChildren(ModelLimits.DIAGNOSTIC_NODES)));
    }

    @Test
    void rejectsSharedChildInstancesEvenWhenAcyclic() {
        var shared = ModelFixtures.minimalDiagnostic();
        var root = ModelFixtures.diagnostic(
                Severity.ERROR,
                BoundedList.empty(),
                BoundedList.empty(),
                BoundedList.empty(),
                TraceSummary.notApplicable(),
                BoundedList.empty(),
                ConfidenceReference.unassessed(),
                BoundedList.of(List.of(
                        new RelatedDiagnostic(Relation.RELATED, shared),
                        new RelatedDiagnostic(Relation.SUPPRESSED, shared))),
                BoundedList.empty());

        assertThrows(RelationValidationException.class, () -> ModelFixtures.document(root));
    }

    @Test
    void exactOmissionAccountingAcceptsMaximumIntegerCount() {
        var root = ModelFixtures.diagnostic(
                Severity.ERROR,
                BoundedList.withOmitted(List.of(), Integer.MAX_VALUE),
                BoundedList.empty(),
                BoundedList.empty(),
                TraceSummary.notApplicable(),
                BoundedList.empty(),
                ConfidenceReference.unassessed(),
                BoundedList.empty(),
                BoundedList.of(List.of(new Omission(
                        new ModelPath("$.root.locations"),
                        Integer.MAX_VALUE,
                        OmissionReason.COUNT_LIMIT))));

        var document = ModelFixtures.document(root);

        assertEquals(Integer.MAX_VALUE, document.root().locations().omittedCount());
    }

    @Test
    void rejectsMissingMismatchedDuplicateUnresolvedAndZeroOmissions() {
        assertThrows(OmissionValidationException.class, () -> ModelFixtures.document(
                rootWithLocationOmission(2, List.of())));
        assertThrows(OmissionValidationException.class, () -> ModelFixtures.document(
                rootWithLocationOmission(2, List.of(omission("$.root.locations", 1)))));
        assertThrows(OmissionValidationException.class, () -> ModelFixtures.document(
                rootWithLocationOmission(2, List.of(
                        omission("$.root.locations", 2),
                        omission("$.root.locations", 2)))));
        assertThrows(OmissionValidationException.class, () -> ModelFixtures.document(
                rootWithLocationOmission(2, List.of(omission("$.root.notes", 2)))));
        assertThrows(OmissionValidationException.class, () -> ModelFixtures.document(
                rootWithLocationOmission(0, List.of(omission("$.root.locations", 1)))));
        assertThrows(OmissionValidationException.class,
                () -> new Omission(new ModelPath("$.root.locations"), 0, OmissionReason.COUNT_LIMIT));
    }

    @Test
    void nodeLocalOmissionsCannotAccountForAnotherNode() {
        var child = ModelFixtures.diagnostic(
                Severity.NOTE,
                BoundedList.empty(),
                BoundedList.empty(),
                BoundedList.empty(),
                TraceSummary.notApplicable(),
                BoundedList.empty(),
                ConfidenceReference.unassessed(),
                BoundedList.empty(),
                BoundedList.of(List.of(omission("$.root.locations", 1))));
        var root = ModelFixtures.diagnostic(
                Severity.ERROR,
                BoundedList.withOmitted(List.of(), 1),
                BoundedList.empty(),
                BoundedList.empty(),
                TraceSummary.notApplicable(),
                BoundedList.empty(),
                ConfidenceReference.unassessed(),
                BoundedList.of(List.of(new RelatedDiagnostic(Relation.RELATED, child))),
                BoundedList.empty());

        assertThrows(OmissionValidationException.class, () -> ModelFixtures.document(root));
    }

    @Test
    void scalarTextAndRedactionOmissionsResolveWithoutBoundedListCounts() {
        var root = ModelFixtures.diagnostic(
                Severity.ERROR,
                BoundedList.empty(),
                BoundedList.empty(),
                BoundedList.empty(),
                TraceSummary.notApplicable(),
                BoundedList.empty(),
                ConfidenceReference.unassessed(),
                BoundedList.empty(),
                BoundedList.of(List.of(
                        new Omission(
                                new ModelPath("$.root.title"),
                                12,
                                OmissionReason.TEXT_LIMIT),
                        new Omission(
                                new ModelPath("$.root.trace.destination"),
                                1,
                                OmissionReason.REDACTION_POLICY))));

        assertDoesNotThrow(() -> ModelFixtures.document(root));
        assertThrows(OmissionValidationException.class, () -> ModelFixtures.document(
                ModelFixtures.diagnostic(
                        Severity.ERROR,
                        BoundedList.empty(),
                        BoundedList.empty(),
                        BoundedList.empty(),
                        TraceSummary.notApplicable(),
                        BoundedList.empty(),
                        ConfidenceReference.unassessed(),
                        BoundedList.empty(),
                        BoundedList.of(List.of(new Omission(
                                new ModelPath("$.root.title"),
                                1,
                                OmissionReason.COUNT_LIMIT))))));
    }

    @Test
    void atomicOptionalOmissionsRequireAbsenceExactCountAndApplicableReason() {
        var position = new SourceRange(
                new SourcePosition(1, 1), new SourcePosition(1, 2));
        var locationWithPosition = new Location(
                new LocationId("location"),
                LocationKind.SOURCE,
                ModelFixtures.text("source location"),
                Optional.of(position),
                Optional.empty(),
                BoundedList.empty());
        var presentPositionRoot = ModelFixtures.diagnostic(
                Severity.ERROR,
                BoundedList.of(List.of(locationWithPosition)),
                BoundedList.empty(),
                BoundedList.empty(),
                TraceSummary.notApplicable(),
                BoundedList.empty(),
                ConfidenceReference.unassessed(),
                BoundedList.empty(),
                BoundedList.of(List.of(new Omission(
                        new ModelPath("$.root.locations.items[0].position"),
                        1,
                        OmissionReason.REDACTION_POLICY))));

        assertThrows(
                OmissionValidationException.class,
                () -> ModelFixtures.document(presentPositionRoot));

        var locationWithoutPosition = new Location(
                new LocationId("location"),
                LocationKind.SOURCE,
                ModelFixtures.text("source location"),
                Optional.empty(),
                Optional.empty(),
                BoundedList.empty());
        var extremeCountRoot = ModelFixtures.diagnostic(
                Severity.ERROR,
                BoundedList.of(List.of(locationWithoutPosition)),
                BoundedList.empty(),
                BoundedList.empty(),
                TraceSummary.notApplicable(),
                BoundedList.empty(),
                ConfidenceReference.unassessed(),
                BoundedList.empty(),
                BoundedList.of(List.of(new Omission(
                        new ModelPath("$.root.locations.items[0].position"),
                        Integer.MAX_VALUE,
                        OmissionReason.REDACTION_POLICY))));

        assertThrows(
                OmissionValidationException.class,
                () -> ModelFixtures.document(extremeCountRoot));
    }

    @Test
    void textOmissionsRequireTextOrByteLimitsButMayCountMultipleCodePoints() {
        var root = ModelFixtures.diagnostic(
                Severity.ERROR,
                BoundedList.empty(),
                BoundedList.empty(),
                BoundedList.empty(),
                TraceSummary.notApplicable(),
                BoundedList.empty(),
                ConfidenceReference.unassessed(),
                BoundedList.empty(),
                BoundedList.of(List.of(new Omission(
                        new ModelPath("$.root.title"),
                        Integer.MAX_VALUE,
                        OmissionReason.TEXT_LIMIT))));

        assertDoesNotThrow(() -> ModelFixtures.document(root));
        assertThrows(OmissionValidationException.class, () -> ModelFixtures.document(
                ModelFixtures.diagnostic(
                        Severity.ERROR,
                        BoundedList.empty(),
                        BoundedList.empty(),
                        BoundedList.empty(),
                        TraceSummary.notApplicable(),
                        BoundedList.empty(),
                        ConfidenceReference.unassessed(),
                        BoundedList.empty(),
                        BoundedList.of(List.of(new Omission(
                                new ModelPath("$.root.title"),
                                1,
                                OmissionReason.REDACTION_POLICY))))));
    }

    @Test
    void acceptsExactUtf8BudgetAndRejectsOneAdditionalByte() {
        var fixedStrings = new ArrayList<String>();
        fixedStrings.addAll(List.of(
                "1.0",
                "diag0001",
                "ABC123",
                "ERROR",
                "SF0001",
                "diagnostic.minimum",
                "operation failed",
                "NOT_APPLICABLE"));
        var ids = IntStream.range(0, ModelLimits.EVIDENCE_PER_NODE)
                .mapToObj(index -> "e" + index)
                .toList();
        for (var id : ids) {
            fixedStrings.addAll(List.of(id, "OTHER", "GENERATED", "PUBLIC", "VISIBLE"));
        }

        var fixedBytes = fixedStrings.stream().mapToInt(LimitsRelationsAndOmissionsTest::utf8).sum();
        var fullValues = ModelLimits.EVIDENCE_PER_NODE - 1;
        var finalValueLength = ModelLimits.DOCUMENT_UTF8_BYTES
                - fixedBytes
                - fullValues * ModelLimits.TEXT_CODE_POINTS;
        var exact = budgetDocument(ids, finalValueLength);

        assertDoesNotThrow(() -> ModelFixtures.document(exact));
        assertThrows(LimitValidationException.class,
                () -> ModelFixtures.document(budgetDocument(ids, finalValueLength + 1)));
    }

    @Test
    void utf8BudgetCountsMultibyteTextBytes() {
        var ids = IntStream.range(0, 33).mapToObj(index -> "m" + index).toList();
        var fixedStrings = new ArrayList<String>();
        fixedStrings.addAll(List.of(
                "1.0",
                "diag0001",
                "ABC123",
                "ERROR",
                "SF0001",
                "diagnostic.minimum",
                "operation failed",
                "NOT_APPLICABLE"));
        for (var id : ids) {
            fixedStrings.addAll(List.of(id, "OTHER", "GENERATED", "PUBLIC", "VISIBLE"));
        }
        var fixedBytes = fixedStrings.stream().mapToInt(LimitsRelationsAndOmissionsTest::utf8).sum();
        var values = new ArrayList<String>();
        IntStream.range(0, 31).forEach(index -> values.add(
                "é".repeat(ModelLimits.TEXT_CODE_POINTS)));
        values.add("x".repeat(ModelLimits.TEXT_CODE_POINTS));
        var usedBytes = fixedBytes + values.stream()
                .mapToInt(LimitsRelationsAndOmissionsTest::utf8)
                .sum();
        var finalBytes = ModelLimits.DOCUMENT_UTF8_BYTES - usedBytes;
        values.add("x".repeat(finalBytes));

        assertDoesNotThrow(() -> ModelFixtures.document(budgetDocument(ids, values)));
        values.set(values.size() - 1, "x".repeat(finalBytes + 1));
        assertThrows(LimitValidationException.class,
                () -> ModelFixtures.document(budgetDocument(ids, values)));
    }

    @Test
    void utf8BudgetStopsBeforeTraversingAReusedOversizedNodeAgain() {
        var evidence = IntStream.range(0, ModelLimits.EVIDENCE_PER_NODE)
                .mapToObj(index -> new EvidenceReference(
                        new EvidenceId("z" + index),
                        EvidenceKind.OTHER,
                        ModelFixtures.text("x".repeat(ModelLimits.TEXT_CODE_POINTS)),
                        Optional.empty()))
                .toList();
        var oversized = ModelFixtures.diagnostic(
                Severity.NOTE,
                BoundedList.empty(),
                BoundedList.empty(),
                BoundedList.empty(),
                TraceSummary.notApplicable(),
                BoundedList.of(evidence),
                ConfidenceReference.unassessed(),
                BoundedList.empty(),
                BoundedList.empty());
        var root = ModelFixtures.diagnostic(
                Severity.ERROR,
                BoundedList.empty(),
                BoundedList.empty(),
                BoundedList.empty(),
                TraceSummary.notApplicable(),
                BoundedList.empty(),
                ConfidenceReference.unassessed(),
                BoundedList.of(List.of(
                        new RelatedDiagnostic(Relation.RELATED, oversized),
                        new RelatedDiagnostic(Relation.RELATED, oversized))),
                BoundedList.empty());

        assertThrows(LimitValidationException.class, () -> ModelFixtures.document(root));
    }

    private static Diagnostic budgetDocument(List<String> ids, int finalValueLength) {
        var values = new ArrayList<EvidenceReference>();
        for (var index = 0; index < ids.size(); index++) {
            var length = index == ids.size() - 1
                    ? finalValueLength
                    : ModelLimits.TEXT_CODE_POINTS;
            values.add(new EvidenceReference(
                    new EvidenceId(ids.get(index)),
                    EvidenceKind.OTHER,
                    ModelFixtures.text("x".repeat(length)),
                    Optional.empty()));
        }

        return ModelFixtures.diagnostic(
                Severity.ERROR,
                BoundedList.empty(),
                BoundedList.empty(),
                BoundedList.empty(),
                TraceSummary.notApplicable(),
                BoundedList.of(values),
                ConfidenceReference.unassessed(),
                BoundedList.empty(),
                BoundedList.empty());
    }

    private static Diagnostic budgetDocument(List<String> ids, List<String> values) {
        var evidence = IntStream.range(0, ids.size())
                .mapToObj(index -> new EvidenceReference(
                        new EvidenceId(ids.get(index)),
                        EvidenceKind.OTHER,
                        ModelFixtures.text(values.get(index)),
                        Optional.empty()))
                .toList();
        return ModelFixtures.diagnostic(
                Severity.ERROR,
                BoundedList.empty(),
                BoundedList.empty(),
                BoundedList.empty(),
                TraceSummary.notApplicable(),
                BoundedList.of(evidence),
                ConfidenceReference.unassessed(),
                BoundedList.empty(),
                BoundedList.empty());
    }

    private static int utf8(String value) {
        return value.getBytes(StandardCharsets.UTF_8).length;
    }

    private static Diagnostic rootWithLocationOmission(
            int omittedCount, List<Omission> omissionRecords) {
        return ModelFixtures.diagnostic(
                Severity.ERROR,
                BoundedList.withOmitted(List.of(), omittedCount),
                BoundedList.empty(),
                BoundedList.empty(),
                TraceSummary.notApplicable(),
                BoundedList.empty(),
                ConfidenceReference.unassessed(),
                BoundedList.empty(),
                BoundedList.of(omissionRecords));
    }

    private static Omission omission(String path, int count) {
        return new Omission(new ModelPath(path), count, OmissionReason.COUNT_LIMIT);
    }

    private static Diagnostic chain(int depth) {
        var current = ModelFixtures.minimalDiagnostic();
        for (var currentDepth = 1; currentDepth < depth; currentDepth++) {
            current = ModelFixtures.diagnostic(
                    Severity.ERROR,
                    BoundedList.empty(),
                    BoundedList.empty(),
                    BoundedList.empty(),
                    TraceSummary.notApplicable(),
                    BoundedList.empty(),
                    ConfidenceReference.unassessed(),
                    BoundedList.of(List.of(new RelatedDiagnostic(Relation.CAUSE, current))),
                    BoundedList.empty());
        }
        return current;
    }

    private static Diagnostic rootWithLeafChildren(int childCount) {
        var children = repeated(
                childCount,
                index -> new RelatedDiagnostic(Relation.AGGREGATE_ITEM, ModelFixtures.minimalDiagnostic()));
        return ModelFixtures.diagnostic(
                Severity.ERROR,
                BoundedList.empty(),
                BoundedList.empty(),
                BoundedList.empty(),
                TraceSummary.notApplicable(),
                BoundedList.empty(),
                ConfidenceReference.unassessed(),
                BoundedList.of(children),
                BoundedList.empty());
    }

    private static List<EvidenceReference> evidence(int count, IntFunction<String> idFactory) {
        return repeated(count, index -> new EvidenceReference(
                new EvidenceId(idFactory.apply(index)),
                EvidenceKind.OTHER,
                ModelFixtures.text("evidence"),
                Optional.empty()));
    }

    private static <T> List<T> repeated(int count, IntFunction<T> factory) {
        return IntStream.range(0, count).mapToObj(factory).toList();
    }
}
