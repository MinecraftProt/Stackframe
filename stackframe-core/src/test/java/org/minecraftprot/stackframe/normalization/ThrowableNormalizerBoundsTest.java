package org.minecraftprot.stackframe.normalization;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void totalFrameBudgetAppliesAcrossNodesWithExactPerNodeAccounting() {
        var frames = frames(256, "example.Shared");
        var root = new SharedDataThrowable("root", frames);
        root.cause = new SharedDataThrowable("cause", frames);
        var limits = new NormalizationLimits(
                3, 3, 256, 1, 64, 300, 100_000, 400_000, 500_000);

        var graph = new ThrowableNormalizer(limits).normalize(root);
        var cause =
                assertInstanceOf(NormalizedThrowable.class, graph.root().cause().orElseThrow());

        assertEquals(256, graph.root().stackFrames().size());
        assertEquals(44, cause.stackFrames().size());
        assertEquals(212, cause.omittedFrameCount());
        assertEquals(
                NormalizedThrowable.FrameTruncationReason.TOTAL_FRAME_LIMIT,
                cause.frameTruncationReason().orElseThrow());
        assertEquals(300, graph.statistics().retainedFrames());
        assertEquals(212, graph.statistics().omittedFrames());
    }

    @Test
    void totalCodePointAndUtf8BudgetsProduceExactTextOmissions() {
        var supplementary = "😀".repeat(20_000);
        var codePointRoot = new SharedDataThrowable(supplementary, new StackTraceElement[0]);
        codePointRoot.cause =
                new SharedDataThrowable(supplementary, new StackTraceElement[0]);
        var codePointLimits = new NormalizationLimits(
                2, 2, 1, 1, 4_096, 2, 5_000, 100_000, 100_000);

        var codePointGraph = new ThrowableNormalizer(codePointLimits).normalize(codePointRoot);
        var codePointCause = (NormalizedThrowable) codePointGraph.root().cause().orElseThrow();
        var codePointText = codePointCause.message().text().orElseThrow();

        assertEquals(
                NormalizedText.TruncationReason.TOTAL_CODE_POINT_LIMIT,
                codePointText.truncationReason().orElseThrow());
        assertEquals(
                supplementary.length(),
                codePointText.value().value().length() + codePointText.omittedUtf16Units());
        assertEquals(5_000, codePointGraph.statistics().retainedTextCodePoints());

        var utf8Limits = new NormalizationLimits(
                1, 1, 1, 1, 4_096, 1, 10_000, 5_000, 100_000);
        var utf8Graph = new ThrowableNormalizer(utf8Limits)
                .normalize(new SharedDataThrowable(supplementary, new StackTraceElement[0]));
        var utf8Text = utf8Graph.root().message().text().orElseThrow();

        assertEquals(
                NormalizedText.TruncationReason.TOTAL_UTF8_BYTE_LIMIT,
                utf8Text.truncationReason().orElseThrow());
        assertEquals(
                supplementary.length(),
                utf8Text.value().value().length() + utf8Text.omittedUtf16Units());
        assertTrue(utf8Graph.statistics().retainedTextUtf8Bytes() <= 5_000);
        assertTrue(5_000 - utf8Graph.statistics().retainedTextUtf8Bytes() < 4);
    }

    @Test
    void scalarWorkBudgetStopsBeforeVisitingAnotherNode() {
        var root = new SharedDataThrowable("", new StackTraceElement[0]);
        root.cause = new SharedDataThrowable("", new StackTraceElement[0]);
        var rootWork = 4 + root.getClass().getName().length();
        var limits = new NormalizationLimits(
                2, 2, 1, 1, 128, 2, 1_000, 4_000, rootWork);

        var graph = new ThrowableNormalizer(limits).normalize(root);
        var marker =
                assertInstanceOf(ThrowableGraphMarker.class, graph.root().cause().orElseThrow());

        assertEquals(ThrowableGraphMarker.Kind.SCALAR_WORK_LIMIT, marker.kind());
        assertEquals(1, marker.omittedDirectNodes());
        assertEquals(rootWork, graph.statistics().scalarWorkUnits());
        assertEquals(1, graph.statistics().scalarWorkTruncations());
    }

    @Test
    void malformedSurrogateLookaheadIsChargedExactly() {
        var failure = new SharedDataThrowable("\uD800A", new StackTraceElement[0]);
        var limits = new NormalizationLimits(
                1, 1, 1, 1, 1, 1, 100, 100, 100);

        var graph = new ThrowableNormalizer(limits).normalize(failure);
        var message = graph.root().message().text().orElseThrow();

        assertEquals("�", message.value().value());
        assertEquals(1, message.extraInspectedUtf16Units());
        assertEquals(
                4 + 1 + 1 + 1,
                graph.statistics().scalarWorkUnits());
    }

    @Test
    void exhaustedWorkDoesNotPreallocateForAHostileSuppressedWidth() {
        var root = new SharedDataThrowable("root", new StackTraceElement[0]);
        var shared = new SharedDataThrowable("shared", new StackTraceElement[0]);
        for (var index = 0; index < 100_000; index++) {
            root.addSuppressed(shared);
        }
        var limits = new NormalizationLimits(
                2, 2, 1, Integer.MAX_VALUE, 8, 1, 100, 400, 4);

        var graph = assertDoesNotThrow(() -> new ThrowableNormalizer(limits).normalize(root));

        assertEquals(0, graph.root().suppressed().size());
        assertEquals(100_000, graph.root().omittedSuppressedCount());
        assertEquals(
                NormalizedThrowable.SuppressedTruncationReason.SCALAR_WORK_LIMIT,
                graph.root().suppressedTruncationReason().orElseThrow());
        assertEquals(4, graph.statistics().scalarWorkUnits());
    }

    @Test
    void defaultsBoundSharedFramesAndHugeSupplementaryTextWithoutAmplification() {
        var huge = "😀".repeat(100_000);
        var sharedFrames = frames(256, huge);
        var root = new SharedDataThrowable(huge, sharedFrames);
        var cursor = root;
        for (var index = 1; index < 256; index++) {
            var next = new SharedDataThrowable(huge, sharedFrames);
            cursor.cause = next;
            cursor = next;
        }

        var graph = assertDoesNotThrow(() -> new ThrowableNormalizer().normalize(root));
        var defaults = NormalizationLimits.DEFAULTS;

        assertTrue(graph.statistics().retainedFrames() <= defaults.maxTotalFrames());
        assertTrue(
                graph.statistics().retainedTextCodePoints()
                        <= defaults.maxTotalTextCodePoints());
        assertTrue(
                graph.statistics().retainedTextUtf8Bytes()
                        <= defaults.maxTotalTextUtf8Bytes());
        assertTrue(graph.statistics().scalarWorkUnits() <= defaults.maxScalarWorkUnits());
        assertTrue(graph.statistics().omittedFrames() > 0);
        assertTrue(graph.statistics().omittedTextUtf16Units() > 0);
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

    private static StackTraceElement[] frames(int count, String declaringClass) {
        var frames = new StackTraceElement[count];
        for (var index = 0; index < count; index++) {
            frames[index] =
                    new StackTraceElement(declaringClass, "run", "Shared.java", index);
        }
        return frames;
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

    private static final class SharedDataThrowable extends Throwable {
        private final String message;
        private final StackTraceElement[] frames;
        private Throwable cause;

        private SharedDataThrowable(String message, StackTraceElement[] frames) {
            super(null, null, true, false);
            this.message = message;
            this.frames = frames;
        }

        @Override
        public String getMessage() {
            return message;
        }

        @Override
        public StackTraceElement[] getStackTrace() {
            return frames;
        }

        @Override
        public synchronized Throwable getCause() {
            return cause;
        }
    }
}
