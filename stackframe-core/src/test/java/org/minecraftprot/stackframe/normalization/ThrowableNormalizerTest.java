package org.minecraftprot.stackframe.normalization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ThrowableNormalizerTest {
    private final ThrowableNormalizer normalizer = new ThrowableNormalizer();

    @Test
    void marksSelfCauseAsCycleWithoutDroppingTheWrapperNode() {
        var failure = new MutableCauseThrowable("wrapper");
        failure.cause = failure;

        var graph = normalizer.normalize(failure);

        assertEquals(MutableCauseThrowable.class.getName(), graph.root().className().value().value());
        var marker = assertInstanceOf(
                ThrowableGraphMarker.class, graph.root().cause().orElseThrow());
        assertEquals(ThrowableGraphMarker.Kind.CYCLE_REFERENCE, marker.kind());
        assertEquals(0, marker.referencedNodeId().orElseThrow());
        assertEquals(1, graph.statistics().cycleReferences());
    }

    @Test
    void detectsMutualCauseSuppressedCycleAndPreservesRelationshipOrder() {
        var outer = new MutableCauseThrowable("outer");
        var inner = new MutableCauseThrowable("inner");
        outer.cause = inner;
        inner.addSuppressed(outer);

        var graph = normalizer.normalize(outer);
        var normalizedInner =
                assertInstanceOf(NormalizedThrowable.class, graph.root().cause().orElseThrow());
        var backReference =
                assertInstanceOf(ThrowableGraphMarker.class, normalizedInner.suppressed().getFirst());

        assertEquals(1, normalizedInner.id());
        assertEquals(ThrowableGraphMarker.Kind.CYCLE_REFERENCE, backReference.kind());
        assertEquals(0, backReference.referencedNodeId().orElseThrow());
    }

    @Test
    void distinguishesARepeatedSharedNodeFromAPathCycle() {
        var root = new MutableCauseThrowable("root");
        var shared = new MutableCauseThrowable("shared");
        root.cause = shared;
        root.addSuppressed(shared);

        var graph = normalizer.normalize(root);
        var cause = assertInstanceOf(NormalizedThrowable.class, graph.root().cause().orElseThrow());
        var repeated =
                assertInstanceOf(ThrowableGraphMarker.class, graph.root().suppressed().getFirst());

        assertEquals(1, cause.id());
        assertEquals(ThrowableGraphMarker.Kind.SHARED_REFERENCE, repeated.kind());
        assertEquals(1, repeated.referencedNodeId().orElseThrow());
        assertEquals(0, graph.statistics().cycleReferences());
        assertEquals(1, graph.statistics().sharedReferences());
    }

    @Test
    void assignsStableCauseFirstDepthFirstIdsAndPreservesNestedSuppressedOrder() {
        var root = new MutableCauseThrowable("root");
        var cause = new MutableCauseThrowable("cause");
        var first = new MutableCauseThrowable("first");
        var firstNested = new MutableCauseThrowable("first-nested");
        var secondNested = new MutableCauseThrowable("second-nested");
        var second = new MutableCauseThrowable("second");
        root.cause = cause;
        root.addSuppressed(first);
        root.addSuppressed(second);
        first.addSuppressed(firstNested);
        first.addSuppressed(secondNested);

        var graph = normalizer.normalize(root);
        var normalizedCause =
                assertInstanceOf(NormalizedThrowable.class, graph.root().cause().orElseThrow());
        var normalizedFirst =
                assertInstanceOf(NormalizedThrowable.class, graph.root().suppressed().get(0));
        var normalizedSecond =
                assertInstanceOf(NormalizedThrowable.class, graph.root().suppressed().get(1));

        assertEquals(1, normalizedCause.id());
        assertEquals(2, normalizedFirst.id());
        assertEquals(
                List.of(3, 4),
                normalizedFirst.suppressed().stream()
                        .map(NormalizedThrowable.class::cast)
                        .map(NormalizedThrowable::id)
                        .toList());
        assertEquals(5, normalizedSecond.id());
    }

    @Test
    void copiesEmptyNullAndLargeMessagesAsBoundedCandidateText() {
        var empty = new Throwable("");
        empty.setStackTrace(new StackTraceElement[0]);
        var absent = new Throwable((String) null);
        var limited = new Throwable("A😀B\uD800CDEF");
        var textLimited = new ThrowableNormalizer(new NormalizationLimits(8, 8, 8, 8, 4));

        var emptyGraph = normalizer.normalize(empty);
        var absentGraph = normalizer.normalize(absent);
        var limitedGraph = textLimited.normalize(limited);
        var text = limitedGraph.root().message().text().orElseThrow();

        assertEquals(NormalizedMessage.State.PRESENT, emptyGraph.root().message().state());
        assertEquals("", emptyGraph.root().message().text().orElseThrow().value().value());
        assertEquals(NormalizedThrowable.StackTraceState.EMPTY, emptyGraph.root().stackTraceState());
        assertEquals(NormalizedMessage.State.ABSENT, absentGraph.root().message().state());
        assertEquals("A😀B�", text.value().value());
        assertEquals(9, text.sourceUtf16Length());
        assertEquals(4, text.omittedUtf16Units());
        assertEquals(1, text.malformedUtf16Units());
        assertEquals(1, text.extraInspectedUtf16Units());
        assertTrue(text.truncated());
    }

    @Test
    void copiesFrameScalarsButTreatsForgedJdkNamesAsUnknown() {
        var failure = new Throwable("frames");
        failure.setStackTrace(new StackTraceElement[] {
            new StackTraceElement(
                    null, "java.base", "25", "java.lang.Thread", "run", "Thread.java", 42),
            new StackTraceElement(
                    "app", null, null, "example.Server", "start", "Server.java", 7)
        });

        var graph = normalizer.normalize(failure);
        var jdk = assertInstanceOf(NormalizedStackFrame.class, graph.root().stackFrames().get(0));
        var unknown =
                assertInstanceOf(NormalizedStackFrame.class, graph.root().stackFrames().get(1));

        assertEquals(NormalizedStackFrame.Category.UNKNOWN, jdk.category());
        assertEquals("java.lang.Thread", jdk.declaringClass().value().value());
        assertEquals(42, jdk.lineNumber());
        assertEquals(NormalizedStackFrame.Category.UNKNOWN, unknown.category());
        assertEquals("app", unknown.classLoaderName().orElseThrow().value().value());
    }

    @Test
    void retainsNullFramePositionAsMalformedData() {
        var failure = new ArrayBackedThrowable(
                "malformed",
                new StackTraceElement[] {
                    new StackTraceElement("example.First", "run", "First.java", 1),
                    null,
                    new StackTraceElement("example.Last", "run", "Last.java", 3)
                });

        var graph = normalizer.normalize(failure);
        var malformed =
                assertInstanceOf(MalformedStackFrame.class, graph.root().stackFrames().get(1));

        assertEquals(1, malformed.originalIndex());
        assertEquals(MalformedStackFrame.Reason.NULL_ELEMENT, malformed.reason());
        assertEquals(1, graph.statistics().malformedFrames());
    }

    @Test
    void recordsNullAndThrowingAccessorsWithoutRetainingTheirFailures() {
        var nullStack = normalizer.normalize(new NullStackThrowable());
        var unreadable = normalizer.normalize(new UnreadableThrowable());

        assertEquals(NormalizedThrowable.StackTraceState.NULL_ARRAY, nullStack.root().stackTraceState());
        assertEquals(NormalizedMessage.State.UNREADABLE, unreadable.root().message().state());
        assertEquals(
                NormalizedThrowable.StackTraceState.UNREADABLE,
                unreadable.root().stackTraceState());
        var cause =
                assertInstanceOf(ThrowableGraphMarker.class, unreadable.root().cause().orElseThrow());
        assertEquals(ThrowableGraphMarker.Kind.UNREADABLE_CAUSE, cause.kind());
        assertEquals(3, unreadable.statistics().unreadableValues());
    }

    @Test
    void convertsAssertionErrorsFromEachOverridableThrowableAccessor() {
        var message = normalizer.normalize(
                new FailingAccessorThrowable(FailingAccessor.MESSAGE, new AssertionError()));
        var stack = normalizer.normalize(
                new FailingAccessorThrowable(FailingAccessor.STACK_TRACE, new AssertionError()));
        var cause = normalizer.normalize(
                new FailingAccessorThrowable(FailingAccessor.CAUSE, new AssertionError()));

        assertEquals(NormalizedMessage.State.UNREADABLE, message.root().message().state());
        assertEquals(
                NormalizedThrowable.StackTraceState.UNREADABLE, stack.root().stackTraceState());
        assertEquals(
                ThrowableGraphMarker.Kind.UNREADABLE_CAUSE,
                ((ThrowableGraphMarker) cause.root().cause().orElseThrow()).kind());
    }

    @Test
    void convertsOtherNonfatalErrorsAndStillReadsFinalSuppressedAccessor() {
        var linkage = normalizer.normalize(
                new FailingAccessorThrowable(
                        FailingAccessor.MESSAGE, new NoClassDefFoundError("untrusted")));
        var suppressed = new Throwable("root");
        suppressed.addSuppressed(new Throwable("child"));

        assertEquals(NormalizedMessage.State.UNREADABLE, linkage.root().message().state());
        assertEquals(1, normalizer.normalize(suppressed).root().suppressed().size());
    }

    @Test
    @SuppressWarnings("removal")
    void rethrowsOnlyFatalVmAndThreadFailures() {
        assertThrows(
                InternalError.class,
                () -> normalizer.normalize(new FailingAccessorThrowable(
                        FailingAccessor.MESSAGE, new InternalError("fatal"))));
        assertThrows(
                ThreadDeath.class,
                () -> normalizer.normalize(new FailingAccessorThrowable(
                        FailingAccessor.CAUSE, new ThreadDeath())));
    }

    @Test
    void repeatedNormalizationIsStructurallyDeterministicAndIndependent() {
        var root = new MutableCauseThrowable("same");
        root.cause = new Throwable("cause");
        root.addSuppressed(new Throwable("suppressed"));

        var first = normalizer.normalize(root);
        var second = normalizer.normalize(root);

        assertNotSame(first, second);
        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
    }

    static final class MutableCauseThrowable extends Throwable {
        Throwable cause;

        MutableCauseThrowable(String message) {
            super(message, null, true, false);
        }

        @Override
        public synchronized Throwable getCause() {
            return cause;
        }
    }

    static final class ArrayBackedThrowable extends Throwable {
        private final StackTraceElement[] frames;

        ArrayBackedThrowable(String message, StackTraceElement[] frames) {
            super(message, null, true, false);
            this.frames = frames;
        }

        @Override
        public StackTraceElement[] getStackTrace() {
            return frames;
        }
    }

    static final class NullStackThrowable extends Throwable {
        @Override
        public StackTraceElement[] getStackTrace() {
            return null;
        }
    }

    static final class UnreadableThrowable extends Throwable {
        @Override
        public String getMessage() {
            throw new IllegalStateException("message unavailable");
        }

        @Override
        public StackTraceElement[] getStackTrace() {
            throw new IllegalStateException("trace unavailable");
        }

        @Override
        public synchronized Throwable getCause() {
            throw new IllegalStateException("cause unavailable");
        }
    }

    private enum FailingAccessor {
        MESSAGE,
        STACK_TRACE,
        CAUSE
    }

    private static final class FailingAccessorThrowable extends Throwable {
        private final FailingAccessor accessor;
        private final Error failure;

        private FailingAccessorThrowable(FailingAccessor accessor, Error failure) {
            super("fallback", null, true, false);
            this.accessor = accessor;
            this.failure = failure;
        }

        @Override
        public String getMessage() {
            if (accessor == FailingAccessor.MESSAGE) {
                throw failure;
            }
            return super.getMessage();
        }

        @Override
        public StackTraceElement[] getStackTrace() {
            if (accessor == FailingAccessor.STACK_TRACE) {
                throw failure;
            }
            return super.getStackTrace();
        }

        @Override
        public synchronized Throwable getCause() {
            if (accessor == FailingAccessor.CAUSE) {
                throw failure;
            }
            return null;
        }
    }
}
