package org.minecraftprot.stackframe.diagnostic;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class TextAndRedactionTest {
    @Test
    void acceptsVisiblePublicAndAllCompletedTransformations() {
        assertDoesNotThrow(() -> DisplayText.visible("safe", TextOrigin.EXTERNAL));
        var marker = new RedactionMarker("PATH");
        assertEquals(TextDisposition.REDACTED,
                DisplayText.redacted(
                        TextOrigin.EXTERNAL, Sensitivity.SERVER_SENSITIVE, marker).disposition());
        assertEquals(TextDisposition.GENERALIZED,
                DisplayText.generalized(
                        "server path",
                        TextOrigin.EXTERNAL,
                        Sensitivity.SERVER_SENSITIVE,
                        marker).disposition());
        assertEquals(TextDisposition.OMITTED,
                DisplayText.omitted(
                        TextOrigin.EXTERNAL, Sensitivity.SERVER_SENSITIVE, marker).disposition());
    }

    @Test
    void redactedAndOmittedFactoriesCannotAcceptProtectedOriginals() {
        var marker = new RedactionMarker("TOKEN");
        assertEquals(
                "<redacted:token>",
                DisplayText.redacted(
                        TextOrigin.EXTERNAL, Sensitivity.SECRET, marker).value());
        assertEquals(
                "<omitted:token>",
                DisplayText.omitted(
                        TextOrigin.EXTERNAL, Sensitivity.SECRET, marker).value());
        assertThrows(RedactionValidationException.class, () -> DisplayText.generalized(
                " ",
                TextOrigin.EXTERNAL,
                Sensitivity.SECRET,
                marker));
        assertThrows(DiagnosticValidationException.class, () -> DisplayText.redacted(
                TextOrigin.EXTERNAL, Sensitivity.SECRET, null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"\u001B", "\r", "\t", "\u0000", "\u007F", "\u0085", "\u2028",
            "\u2029", "\u202E", "\u2066"})
    void rejectsUnsafeCompletedControlText(String control) {
        assertThrows(TextValidationException.class,
                () -> DisplayText.visible("before" + control + "after", TextOrigin.EXTERNAL));
    }

    @Test
    void rejectsUnpairedSurrogatesButCountsUnicodeCodePoints() {
        assertThrows(TextValidationException.class,
                () -> DisplayText.visible("\uD800", TextOrigin.EXTERNAL));
        assertDoesNotThrow(() -> DisplayText.visible(
                "😀".repeat(ModelLimits.TEXT_CODE_POINTS), TextOrigin.EXTERNAL));
        assertThrows(LimitValidationException.class, () -> DisplayText.visible(
                "😀".repeat(ModelLimits.TEXT_CODE_POINTS + 1), TextOrigin.EXTERNAL));
    }

    @Test
    void acceptsExactTextSpecificLimitsAndRejectsOneMore() {
        assertDoesNotThrow(() -> ModelFixtures.diagnostic(
                Severity.ERROR,
                BoundedList.empty(),
                BoundedList.empty(),
                BoundedList.empty(),
                TraceSummary.notApplicable(),
                BoundedList.empty(),
                ConfidenceReference.unassessed(),
                BoundedList.empty(),
                BoundedList.empty()));
        assertDoesNotThrow(() -> new Diagnostic(
                Severity.ERROR,
                new DiagnosticCode("SF0001"),
                new CatalogText("diagnostic.title", "t".repeat(ModelLimits.TITLE_CODE_POINTS)),
                BoundedList.empty(),
                BoundedList.empty(),
                BoundedList.empty(),
                TraceSummary.notApplicable(),
                BoundedList.empty(),
                ConfidenceReference.unassessed(),
                BoundedList.empty(),
                BoundedList.empty()));
        assertThrows(LimitValidationException.class, () -> new Diagnostic(
                Severity.ERROR,
                new DiagnosticCode("SF0001"),
                new CatalogText("diagnostic.title", "t".repeat(ModelLimits.TITLE_CODE_POINTS + 1)),
                BoundedList.empty(),
                BoundedList.empty(),
                BoundedList.empty(),
                TraceSummary.notApplicable(),
                BoundedList.empty(),
                ConfidenceReference.unassessed(),
                BoundedList.empty(),
                BoundedList.empty()));

        assertDoesNotThrow(() -> locationWithDisplay("l".repeat(ModelLimits.LOCATION_CODE_POINTS)));
        assertThrows(LimitValidationException.class,
                () -> locationWithDisplay("l".repeat(ModelLimits.LOCATION_CODE_POINTS + 1)));
        assertDoesNotThrow(() -> DisplayText.visible(
                "x".repeat(ModelLimits.TEXT_CODE_POINTS), TextOrigin.GENERATED));
        assertThrows(LimitValidationException.class, () -> DisplayText.visible(
                "x".repeat(ModelLimits.TEXT_CODE_POINTS + 1), TextOrigin.GENERATED));
    }

    @ParameterizedTest
    @MethodSource("blankRequiredText")
    void rejectsBlankTextInRequiredSemanticFields(Executable construction) {
        assertThrows(TextValidationException.class, construction);
    }

    @Test
    void excerptLinesMayBeBlankButNeverContainNewline() {
        assertDoesNotThrow(() -> new ExcerptLine(1, ModelFixtures.text(" ")));
        assertDoesNotThrow(() -> new ExcerptLine(1, ModelFixtures.text("")));
        assertThrows(TextValidationException.class,
                () -> new ExcerptLine(1, ModelFixtures.text("one\ntwo")));
    }

    @Test
    void candidateTextRemainsSeparateAndMayCarryUntrustedControls() {
        var candidate = new CandidateText("raw\u001Bvalue");
        assertEquals("raw\u001Bvalue", candidate.value());
        assertThrows(TextValidationException.class, () -> new CandidateText("\uD800"));
        assertThrows(LimitValidationException.class,
                () -> new CandidateText("x".repeat(ModelLimits.TEXT_CODE_POINTS + 1)));
    }

    @Test
    void redactionNoticesRequireCompletedTransformationAndPositiveCount() {
        var marker = new RedactionMarker("TOKEN");
        assertDoesNotThrow(() -> new RedactionNotice(
                marker, TextDisposition.REDACTED, Integer.MAX_VALUE));
        assertThrows(RedactionValidationException.class,
                () -> new RedactionNotice(marker, TextDisposition.VISIBLE, 1));
        assertThrows(RedactionValidationException.class,
                () -> new RedactionNotice(marker, TextDisposition.REDACTED, 0));
    }

    private static Location locationWithDisplay(String value) {
        return new Location(
                new LocationId("location"),
                LocationKind.OTHER,
                ModelFixtures.text(value),
                Optional.empty(),
                Optional.empty(),
                BoundedList.empty());
    }

    private static Stream<Arguments> blankRequiredText() {
        var blank = ModelFixtures.text(" ");
        var evidenceId = ModelFixtures.EVIDENCE_ID;
        return Stream.of(
                Arguments.of((Executable) () -> new CatalogText("catalog.key", " ")),
                Arguments.of((Executable) () -> locationWithDisplay(" ")),
                Arguments.of((Executable) () -> new Label(
                        new SourceRange(new SourcePosition(1, 1), new SourcePosition(1, 2)),
                        LabelStyle.PRIMARY,
                        blank,
                        BoundedList.empty())),
                Arguments.of((Executable) () -> new Note(
                        NoteKind.NOTE, blank, BoundedList.empty())),
                Arguments.of((Executable) () -> new Help(
                        blank, HelpKind.INSPECT, BoundedList.of(List.of(evidenceId)))),
                Arguments.of((Executable) () -> new EvidenceReference(
                        evidenceId, EvidenceKind.OTHER, blank, Optional.empty())),
                Arguments.of((Executable) () -> new EvidenceReference(
                        evidenceId,
                        EvidenceKind.OTHER,
                        ModelFixtures.text("summary"),
                        Optional.of(blank))),
                Arguments.of((Executable) () -> new TraceSummary(
                        TraceState.PRESERVED,
                        Optional.empty(),
                        0,
                        0,
                        0,
                        Optional.of(blank),
                        Optional.empty())));
    }
}
