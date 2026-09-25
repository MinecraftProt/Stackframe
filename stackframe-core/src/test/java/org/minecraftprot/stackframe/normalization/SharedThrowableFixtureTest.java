package org.minecraftprot.stackframe.normalization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.minecraftprot.stackframe.testkit.ThrowableFixtures;

class SharedThrowableFixtureTest {
    private final ThrowableNormalizer normalizer = new ThrowableNormalizer();

    @Test
    void wrapperSuppressedAndReferenceShapesSurviveNormalization() {
        var wrapped = normalizer.normalize(ThrowableFixtures.wrapperWithSuppressed());
        assertInstanceOf(NormalizedThrowable.class, wrapped.root().cause().orElseThrow());
        assertEquals(2, wrapped.root().suppressed().size());

        var cycle = normalizer.normalize(ThrowableFixtures.causeAndSuppressedCycle());
        assertEquals(1, cycle.statistics().cycleReferences());
        var shared = normalizer.normalize(ThrowableFixtures.sharedCauseAndSuppressed());
        assertEquals(1, shared.statistics().sharedReferences());
    }

    @Test
    void deepAndLargeFixturesExerciseIndependentLimits() {
        var deep = normalizer.normalize(ThrowableFixtures.deepCauseChain(80));
        assertEquals(1, deep.statistics().depthTruncations());

        var large = normalizer.normalize(ThrowableFixtures.largeStack(300));
        assertEquals(256, large.root().stackFrames().size());
        assertEquals(44, large.root().omittedFrameCount());
    }

    @Test
    void unicodeMissingMetadataAndSyntheticSecretsRemainDistinctInputs() {
        var unicode = normalizer.normalize(ThrowableFixtures.unusualUnicode());
        var message = unicode.root().message().text().orElseThrow().value().value();
        assertTrue(message.contains("界"));
        assertTrue(message.contains("�"));

        var missing = normalizer.normalize(ThrowableFixtures.missingMetadata());
        assertEquals(NormalizedMessage.State.ABSENT, missing.root().message().state());
        assertEquals(NormalizedThrowable.StackTraceState.EMPTY, missing.root().stackTraceState());

        var secret = normalizer.normalize(ThrowableFixtures.secretBearing());
        assertTrue(secret.root().message().text().orElseThrow().value().value()
                .contains(ThrowableFixtures.SYNTHETIC_SECRET));
    }

    @Test
    void commonServerFailureShapesProvideTypedCauses() {
        for (var failure : List.of(
                ThrowableFixtures.missingMod(),
                ThrowableFixtures.mixinFailure(),
                ThrowableFixtures.registryDecodeFailure(),
                ThrowableFixtures.datapackLoadFailure())) {
            var graph = normalizer.normalize(failure);
            assertInstanceOf(NormalizedThrowable.class, graph.root().cause().orElseThrow());
            assertEquals(2, graph.statistics().retainedNodes());
            assertEquals(2, graph.statistics().retainedFrames());
        }
    }
}
