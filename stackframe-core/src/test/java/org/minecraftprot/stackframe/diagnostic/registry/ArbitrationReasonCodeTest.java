package org.minecraftprot.stackframe.diagnostic.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class ArbitrationReasonCodeTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("decisionMatrix")
    void everyNonSelectedCandidateHasExactlyOneTruthfulReason(DecisionCase decision) {
        for (var reasons : decision.nonSelectedCandidateReasons()) {
            assertEquals(1, reasons.size());
            assertEquals(ArbitrationReasonScope.NON_SELECTED_CANDIDATE, reasons.getFirst().scope());
        }
        for (var reason : decision.selectedCandidateReasons()) {
            assertEquals(ArbitrationReasonScope.SELECTED_CANDIDATE, reason.scope());
        }
        decision.fallbackReason().ifPresent(reason ->
                assertEquals(ArbitrationReasonScope.FALLBACK_SELECTION, reason.scope()));
    }

    private static Stream<DecisionCase> decisionMatrix() {
        return Stream.of(
                new DecisionCase(
                        "high plus low",
                        List.of(ArbitrationReasonCode.SELECTED),
                        List.of(List.of(ArbitrationReasonCode.EXCLUDED_LOW_CONFIDENCE)),
                        Optional.empty()),
                new DecisionCase(
                        "only low uses fallback",
                        List.of(),
                        List.of(List.of(ArbitrationReasonCode.EXCLUDED_LOW_CONFIDENCE)),
                        Optional.of(ArbitrationReasonCode.FALLBACK_NO_ELIGIBLE)),
                new DecisionCase(
                        "tied eligible candidates use fallback",
                        List.of(),
                        List.of(
                                List.of(ArbitrationReasonCode.EXCLUDED_CONFLICT),
                                List.of(ArbitrationReasonCode.EXCLUDED_CONFLICT)),
                        Optional.of(ArbitrationReasonCode.FALLBACK_CONFLICT)),
                new DecisionCase(
                        "malformed candidate uses fallback",
                        List.of(),
                        List.of(List.of(ArbitrationReasonCode.REJECTED_MALFORMED)),
                        Optional.of(ArbitrationReasonCode.FALLBACK_NO_ELIGIBLE)),
                new DecisionCase(
                        "unsupported candidate uses fallback",
                        List.of(),
                        List.of(List.of(ArbitrationReasonCode.REJECTED_UNSUPPORTED_CLAIM)),
                        Optional.of(ArbitrationReasonCode.FALLBACK_NO_ELIGIBLE)));
    }

    private record DecisionCase(
            String name,
            List<ArbitrationReasonCode> selectedCandidateReasons,
            List<List<ArbitrationReasonCode>> nonSelectedCandidateReasons,
            Optional<ArbitrationReasonCode> fallbackReason) {
        @Override
        public String toString() {
            return name;
        }
    }
}
