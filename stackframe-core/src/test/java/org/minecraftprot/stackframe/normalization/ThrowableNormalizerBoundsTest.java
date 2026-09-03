package org.minecraftprot.stackframe.normalization;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import org.junit.jupiter.api.Test;

class ThrowableNormalizerBoundsTest {
    @Test
    void rejectsNonPositiveAndOverBoundaryLimits() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new NormalizationLimits(0, 1, 1, 1, 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> new NormalizationLimits(1, -1, 1, 1, 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> new NormalizationLimits(1, 1, 0, 1, 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> new NormalizationLimits(1, 1, 1, 0, 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> new NormalizationLimits(1, 1, 1, 1, 4_097));
    }

    @Test
    void marksDepthTruncationAndDoesNotRecurseOnVeryDeepChains() {
        var chain = chain(20_000);
        var normalizer = new ThrowableNormalizer(new NormalizationLimits(100, 32, 1, 1, 16));

        var graph = assertDoesNotThrow(() -> normalizer.normalize(chain));
        var cursor = graph.root();
        for (var depth = 1; depth < 32; depth++) {
            cursor = assertInstanceOf(NormalizedThrowable.class, cursor.cause().orElseThrow());
        }
        var marker =
                assertInstanceOf(ThrowableGraphMarker.class, cursor.cause().orElseThrow());

        assertEquals(ThrowableGraphMarker.Kind.DEPTH_LIMIT, marker.kind());
        assertEquals(32, graph.statistics().retainedNodes());
        assertEquals(1, graph.statistics().depthTruncations());
    }

    @Test
    void appliesGlobalNodeLimitToWideGraphsInOriginalOrder() {
        var root = new Throwable("root");
        for (var index = 0; index < 10; index++) {
            root.addSuppressed(new Throwable("child-" + index));
        }
        var normalizer = new ThrowableNormalizer(new NormalizationLimits(3, 8, 1, 10, 16));

        var graph = normalizer.normalize(root);

        assertEquals(3, graph.statistics().retainedNodes());
        assertEquals(8, graph.statistics().nodeLimitTruncations());
        assertEquals(
                "child-0",
                ((NormalizedThrowable) graph.root().suppressed().get(0))
                        .message()
                        .text()
                        .orElseThrow()
                        .value()
                        .value());
        assertEquals(
                "child-1",
                ((NormalizedThrowable) graph.root().suppressed().get(1))
                        .message()
                        .text()
                        .orElseThrow()
                        .value()
                        .value());
        for (var index = 2; index < 10; index++) {
            var marker = assertInstanceOf(
                    ThrowableGraphMarker.class, graph.root().suppressed().get(index));
            assertEquals(ThrowableGraphMarker.Kind.NODE_LIMIT, marker.kind());
            assertEquals(1, marker.omittedDirectNodes());
        }
    }

    @Test
    void reportsExactFrameAndSuppressedChildOmissions() {
        var root = new Throwable("root");
        var frames = new StackTraceElement[20];
        for (var index = 0; index < frames.length; index++) {
            frames[index] =
                    new StackTraceElement("example.Frame" + index, "run", "Frame.java", index);
        }
        root.setStackTrace(frames);
        for (var index = 0; index < 12; index++) {
            var child = new Throwable("child-" + index);
            child.setStackTrace(new StackTraceElement[0]);
            root.addSuppressed(child);
        }
        var normalizer = new ThrowableNormalizer(new NormalizationLimits(20, 8, 3, 4, 32));

        var graph = normalizer.normalize(root);

        assertEquals(3, graph.root().stackFrames().size());
        assertEquals(17, graph.root().omittedFrameCount());
        assertEquals(4, graph.root().suppressed().size());
        assertEquals(8, graph.root().omittedSuppressedCount());
        assertEquals(17, graph.statistics().omittedFrames());
        assertEquals(8, graph.statistics().omittedSuppressedEdges());
        assertEquals(4, graph.statistics().retainedSuppressedEdges());
    }

    @Test
    void hostileWidthFramesAndMessageRemainWithinConfiguredOutputBounds() {
        var root = new Throwable("x".repeat(1_000_000));
        var frames = new StackTraceElement[10_000];
        for (var index = 0; index < frames.length; index++) {
            frames[index] = new StackTraceElement(
                    "example." + "C".repeat(1_000), "method", "File.java", index);
        }
        root.setStackTrace(frames);
        for (var index = 0; index < 1_000; index++) {
            root.addSuppressed(new Throwable("child-" + index));
        }
        var normalizer = new ThrowableNormalizer(new NormalizationLimits(4, 4, 3, 3, 16));

        var graph = assertDoesNotThrow(() -> normalizer.normalize(root));

        assertEquals(4, graph.statistics().retainedNodes());
        assertEquals(3, graph.root().stackFrames().size());
        assertEquals(9_997, graph.root().omittedFrameCount());
        assertEquals(3, graph.root().suppressed().size());
        assertEquals(997, graph.root().omittedSuppressedCount());
        assertEquals(
                16,
                graph.root().message().text().orElseThrow().value().value().length());
        assertEquals(
                999_984,
                graph.root().message().text().orElseThrow().omittedUtf16Units());
    }

    @Test
    void eachRetainedSourceAccessorIsReadAtMostOnce() {
        var nodes = new ArrayList<CountingThrowable>();
        for (var index = 0; index < 1_000; index++) {
            nodes.add(new CountingThrowable("node-" + index));
            if (index > 0) {
                nodes.get(index - 1).cause = nodes.get(index);
            }
        }
        var normalizer = new ThrowableNormalizer(new NormalizationLimits(10, 10, 1, 1, 16));

        var graph = normalizer.normalize(nodes.getFirst());

        assertEquals(10, graph.statistics().retainedNodes());
        for (var index = 0; index < 10; index++) {
            assertEquals(1, nodes.get(index).messageReads);
            assertEquals(1, nodes.get(index).stackReads);
            assertEquals(1, nodes.get(index).causeReads);
        }
        assertEquals(0, nodes.get(10).messageReads);
        assertEquals(0, nodes.get(10).stackReads);
        assertEquals(0, nodes.get(10).causeReads);
    }

    private static ThrowableNormalizerTest.MutableCauseThrowable chain(int nodes) {
        var root = new ThrowableNormalizerTest.MutableCauseThrowable("node-0");
        var cursor = root;
        for (var index = 1; index < nodes; index++) {
            var next = new ThrowableNormalizerTest.MutableCauseThrowable("node-" + index);
            cursor.cause = next;
            cursor = next;
        }
        return root;
    }

    private static final class CountingThrowable extends Throwable {
        private Throwable cause;
        private int messageReads;
        private int stackReads;
        private int causeReads;

        private CountingThrowable(String message) {
            super(message, null, true, false);
        }

        @Override
        public String getMessage() {
            messageReads++;
            return super.getMessage();
        }

        @Override
        public StackTraceElement[] getStackTrace() {
            stackReads++;
            return super.getStackTrace();
        }

        @Override
        public synchronized Throwable getCause() {
            causeReads++;
            return cause;
        }
    }
}
