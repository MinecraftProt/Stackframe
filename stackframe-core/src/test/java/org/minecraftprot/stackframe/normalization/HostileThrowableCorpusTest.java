package org.minecraftprot.stackframe.normalization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.minecraftprot.stackframe.testkit.HostileInputCorpus;

class HostileThrowableCorpusTest {
    private static final NormalizationLimits LIMITS = new NormalizationLimits(
            32, 12, 64, 8, 256, 256, 8_192, 32_768, 32_768);

    @Test
    void seededGraphsTerminateDeterministicallyWithinEveryGlobalBudget() {
        var baseSeed = HostileInputCorpus.baseSeed();
        var cases = HostileInputCorpus.caseCount();
        var start = HostileInputCorpus.caseStart();
        long cycles = 0;
        long malformedFrames = 0;
        long unreadableValues = 0;
        long omittedFrames = 0;
        long omittedText = 0;
        for (var index = start; index < start + cases; index++) {
            var seed = HostileInputCorpus.caseSeed(baseSeed, index);
            try {
                var first = assertTimeoutPreemptively(Duration.ofSeconds(2),
                        () -> new ThrowableNormalizer(LIMITS).normalize(
                                HostileInputCorpus.throwable(seed)));
                var second = assertTimeoutPreemptively(Duration.ofSeconds(2),
                        () -> new ThrowableNormalizer(LIMITS).normalize(
                                HostileInputCorpus.throwable(seed)));
                assertEquals(first, second, "normalization must be deterministic");
                var statistics = first.statistics();
                assertTrue(statistics.retainedNodes() <= LIMITS.maxNodes());
                assertTrue(statistics.retainedFrames() <= LIMITS.maxTotalFrames());
                assertTrue(statistics.retainedTextCodePoints()
                        <= LIMITS.maxTotalTextCodePoints());
                assertTrue(statistics.retainedTextUtf8Bytes()
                        <= LIMITS.maxTotalTextUtf8Bytes());
                assertTrue(statistics.scalarWorkUnits() <= LIMITS.maxScalarWorkUnits());
                cycles += statistics.cycleReferences();
                malformedFrames += statistics.malformedFrames();
                unreadableValues += statistics.unreadableValues();
                omittedFrames += statistics.omittedFrames();
                omittedText += statistics.omittedTextUtf16Units();
            } catch (Throwable failure) {
                throw new AssertionError(HostileInputCorpus.failureContext(baseSeed, index),
                        failure);
            }
        }
        if (start == 0 && cases == HostileInputCorpus.DEFAULT_CASES
                && baseSeed == HostileInputCorpus.DEFAULT_SEED) {
            assertTrue(cycles > 0, "default corpus must visit a cycle");
            assertTrue(malformedFrames > 0, "default corpus must visit a malformed frame");
            assertTrue(unreadableValues > 0, "default corpus must visit unreadable accessors");
            assertTrue(omittedFrames > 0, "default corpus must truncate frames");
            assertTrue(omittedText > 0, "default corpus must truncate text");
        }
    }
}
