package org.minecraftprot.stackframe.diagnostic.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class ArbitrationReasonAssignmentTest {
    @Test
    void assignmentRequiresExactlyOneNonNullScopeMatchingReason() {
        var assignment = ArbitrationReasonAssignment.nonSelectedCandidate(
                ArbitrationReasonCode.EXCLUDED_LOW_CONFIDENCE);

        assertEquals(
                ArbitrationReasonScope.NON_SELECTED_CANDIDATE,
                assignment.scope());
        assertEquals(ArbitrationReasonCode.EXCLUDED_LOW_CONFIDENCE, assignment.reason());
        assertEquals(
                List.of("scope", "reason"),
                Arrays.stream(ArbitrationReasonAssignment.class.getRecordComponents())
                        .map(component -> component.getName())
                        .toList());
        assertThrows(
                RegistryValidationException.class,
                () -> new ArbitrationReasonAssignment(
                        null, ArbitrationReasonCode.EXCLUDED_LOW_CONFIDENCE));
        assertThrows(
                RegistryValidationException.class,
                () -> new ArbitrationReasonAssignment(
                        ArbitrationReasonScope.NON_SELECTED_CANDIDATE, null));
        assertThrows(
                RegistryValidationException.class,
                () -> ArbitrationReasonAssignment.nonSelectedCandidate(
                        ArbitrationReasonCode.SELECTED));
        assertThrows(
                RegistryValidationException.class,
                () -> ArbitrationReasonAssignment.fallbackSelection(
                        ArbitrationReasonCode.EXCLUDED_LOW_CONFIDENCE));
    }

    @Test
    void coversEveryOutcomeSubject() {
        assertEquals(
                ArbitrationReasonScope.SELECTED_CANDIDATE,
                ArbitrationReasonAssignment.selectedCandidate(
                                ArbitrationReasonCode.SELECTED)
                        .scope());
        assertEquals(
                ArbitrationReasonScope.NON_SELECTED_CANDIDATE,
                ArbitrationReasonAssignment.nonSelectedCandidate(
                                ArbitrationReasonCode.SUPPRESSED_LOWER_RANK)
                        .scope());
        assertEquals(
                ArbitrationReasonScope.FALLBACK_SELECTION,
                ArbitrationReasonAssignment.fallbackSelection(
                                ArbitrationReasonCode.FALLBACK_NO_ELIGIBLE)
                        .scope());
        assertEquals(
                ArbitrationReasonScope.CLASSIFIER,
                ArbitrationReasonAssignment.classifierFailure(
                                ArbitrationReasonCode.CLASSIFIER_FAILURE)
                        .scope());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("decisionMatrix")
    void everyNonSelectedCandidateHasExactlyOneTruthfulReason(DecisionCase decision) {
        for (var candidate : decision.nonSelectedCandidates()) {
            assertEquals(
                    ArbitrationReasonScope.NON_SELECTED_CANDIDATE,
                    candidate.reason().scope());
        }
        for (var candidate : decision.selectedCandidates()) {
            assertEquals(
                    ArbitrationReasonScope.SELECTED_CANDIDATE,
                    candidate.reason().scope());
        }
        decision.fallbackReason().ifPresent(reason -> assertEquals(
                ArbitrationReasonScope.FALLBACK_SELECTION, reason.scope()));
    }

    private static Stream<DecisionCase> decisionMatrix() {
        return Stream.of(
                new DecisionCase(
                        "high plus low",
                        List.of(selected("high", ArbitrationReasonCode.SELECTED)),
                        List.of(nonSelected(
                                "low", ArbitrationReasonCode.EXCLUDED_LOW_CONFIDENCE)),
                        Optional.empty()),
                new DecisionCase(
                        "only low uses fallback",
                        List.of(),
                        List.of(nonSelected(
                                "low", ArbitrationReasonCode.EXCLUDED_LOW_CONFIDENCE)),
                        Optional.of(ArbitrationReasonAssignment.fallbackSelection(
                                ArbitrationReasonCode.FALLBACK_NO_ELIGIBLE))),
                new DecisionCase(
                        "tied eligible candidates use fallback",
                        List.of(),
                        List.of(
                                nonSelected("left", ArbitrationReasonCode.EXCLUDED_CONFLICT),
                                nonSelected("right", ArbitrationReasonCode.EXCLUDED_CONFLICT)),
                        Optional.of(ArbitrationReasonAssignment.fallbackSelection(
                                ArbitrationReasonCode.FALLBACK_CONFLICT))),
                new DecisionCase(
                        "malformed candidate uses fallback",
                        List.of(),
                        List.of(nonSelected(
                                "malformed", ArbitrationReasonCode.REJECTED_MALFORMED)),
                        Optional.of(ArbitrationReasonAssignment.fallbackSelection(
                                ArbitrationReasonCode.FALLBACK_NO_ELIGIBLE))),
                new DecisionCase(
                        "unsupported candidate uses fallback",
                        List.of(),
                        List.of(nonSelected(
                                "unsupported",
                                ArbitrationReasonCode.REJECTED_UNSUPPORTED_CLAIM)),
                        Optional.of(ArbitrationReasonAssignment.fallbackSelection(
                                ArbitrationReasonCode.FALLBACK_NO_ELIGIBLE))));
    }

    private static CandidateResult selected(
            String key,
            ArbitrationReasonCode reason) {
        return new CandidateResult(
                key, ArbitrationReasonAssignment.selectedCandidate(reason));
    }

    private static CandidateResult nonSelected(
            String key,
            ArbitrationReasonCode reason) {
        return new CandidateResult(
                key, ArbitrationReasonAssignment.nonSelectedCandidate(reason));
    }

    private record DecisionCase(
            String name,
            List<CandidateResult> selectedCandidates,
            List<CandidateResult> nonSelectedCandidates,
            Optional<ArbitrationReasonAssignment> fallbackReason) {
        @Override
        public String toString() {
            return name;
        }
    }

    private record CandidateResult(
            String candidateKey,
            ArbitrationReasonAssignment reason) {
    }
}
