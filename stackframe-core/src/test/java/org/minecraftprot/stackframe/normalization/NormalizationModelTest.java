package org.minecraftprot.stackframe.normalization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.minecraftprot.stackframe.diagnostic.CandidateText;

class NormalizationModelTest {
    @Test
    void resultDefensivelyCopiesCollectionsAndExposesNoMutableView() {
        var entries = new ArrayList<NormalizedFrameEntry>();
        entries.add(new MalformedStackFrame(0, MalformedStackFrame.Reason.NULL_ELEMENT));
        var node = new NormalizedThrowable(
                0,
                text("example.Failure"),
                NormalizedMessage.absent(),
                NormalizedThrowable.StackTraceState.PRESENT,
                entries,
                0,
                java.util.Optional.empty(),
                java.util.Optional.empty(),
                NormalizedThrowable.SuppressedState.EMPTY,
                List.of(),
                0,
                java.util.Optional.empty());
        entries.add(new MalformedStackFrame(1, MalformedStackFrame.Reason.NULL_ELEMENT));

        assertEquals(1, node.stackFrames().size());
        assertThrows(
                UnsupportedOperationException.class,
                () -> node.stackFrames().add(
                        new MalformedStackFrame(2, MalformedStackFrame.Reason.NULL_ELEMENT)));
    }

    @Test
    void sourceFrameArraysAndElementsAreCopiedAsScalars() {
        var frames = new StackTraceElement[] {
            new StackTraceElement("example.Before", "run", "Before.java", 1)
        };
        var source = new ThrowableNormalizerTest.ArrayBackedThrowable("source", frames);
        var graph = new ThrowableNormalizer().normalize(source);
        frames[0] = new StackTraceElement("example.After", "run", "After.java", 2);

        var retained = (NormalizedStackFrame) graph.root().stackFrames().getFirst();
        assertEquals("example.Before", retained.declaringClass().value().value());
        assertEquals("Before.java", retained.fileName().orElseThrow().value().value());
    }

    @Test
    void publicResultTypeGraphCannotRetainForbiddenSourceOrMutableTypes() {
        assertAllowedResultGraph(NormalizedThrowableGraph.class);
    }

    @Test
    void acceptsAValidDirectlyConstructedGraphWithExactStatistics() {
        var root = node(0, text("X"), Optional.empty(), List.of());
        var statistics = new NormalizationStatistics(
                1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 5);

        var graph = new NormalizedThrowableGraph(
                root, new NormalizationLimits(1, 1, 1, 1, 8), statistics);

        assertEquals(root, graph.root());
    }

    @Test
    void rejectsIncorrectStatisticsAndNonCanonicalOrDuplicateNodeIds() {
        var valid = new ThrowableNormalizer().normalize(new Throwable("valid"));
        assertThrows(
                IllegalArgumentException.class,
                () -> new NormalizedThrowableGraph(
                        valid.root(), valid.limits(), zeroStatistics()));

        var wrongRootId = node(1, text("X"), Optional.empty(), List.of());
        assertThrows(
                IllegalArgumentException.class,
                () -> new NormalizedThrowableGraph(
                        wrongRootId,
                        new NormalizationLimits(2, 2, 1, 1, 8),
                        zeroStatistics()));

        var duplicate = node(0, text("Y"), Optional.empty(), List.of());
        var duplicateRoot = node(0, text("X"), Optional.of(duplicate), List.of());
        assertThrows(
                IllegalArgumentException.class,
                () -> new NormalizedThrowableGraph(
                        duplicateRoot,
                        new NormalizationLimits(2, 2, 1, 1, 8),
                        zeroStatistics()));
    }

    @Test
    void rejectsUnresolvedReferencesAndMarkersOnWrongRelationships() {
        var unresolved = node(
                0,
                text("X"),
                Optional.of(ThrowableGraphMarker.reference(
                        ThrowableGraphMarker.Kind.SHARED_REFERENCE, 99)),
                List.of());
        assertThrows(
                IllegalArgumentException.class,
                () -> new NormalizedThrowableGraph(
                        unresolved,
                        new NormalizationLimits(2, 2, 1, 1, 8),
                        zeroStatistics()));

        var malformedCause = node(
                0,
                text("X"),
                Optional.of(ThrowableGraphMarker.malformedSuppressed()),
                List.of());
        assertThrows(
                IllegalArgumentException.class,
                () -> new NormalizedThrowableGraph(
                        malformedCause,
                        new NormalizationLimits(2, 2, 1, 1, 8),
                        zeroStatistics()));
    }

    @Test
    void rejectsDepthPerNodeAndGlobalTextLimitBypasses() {
        var child = node(1, text("Y"), Optional.empty(), List.of());
        var deep = node(0, text("X"), Optional.of(child), List.of());
        assertThrows(
                IllegalArgumentException.class,
                () -> new NormalizedThrowableGraph(
                        deep,
                        new NormalizationLimits(2, 1, 1, 1, 8),
                        zeroStatistics()));

        var frames = List.<NormalizedFrameEntry>of(
                new MalformedStackFrame(0, MalformedStackFrame.Reason.NULL_ELEMENT),
                new MalformedStackFrame(1, MalformedStackFrame.Reason.NULL_ELEMENT));
        var excessiveFrames = new NormalizedThrowable(
                0,
                text("X"),
                NormalizedMessage.absent(),
                NormalizedThrowable.StackTraceState.PRESENT,
                frames,
                0,
                Optional.empty(),
                Optional.empty(),
                NormalizedThrowable.SuppressedState.EMPTY,
                List.of(),
                0,
                Optional.empty());
        assertThrows(
                IllegalArgumentException.class,
                () -> new NormalizedThrowableGraph(
                        excessiveFrames,
                        new NormalizationLimits(1, 1, 1, 1, 8),
                        zeroStatistics()));

        var excessiveText = node(0, text("XX"), Optional.empty(), List.of());
        var textLimits =
                new NormalizationLimits(1, 1, 1, 1, 8, 1, 1, 8, 32);
        assertThrows(
                IllegalArgumentException.class,
                () -> new NormalizedThrowableGraph(
                        excessiveText, textLimits, zeroStatistics()));
    }

    @Test
    void rejectsMarkersThatDoNotOccurAtTheirDeclaredLimits() {
        var earlyDepth = node(
                0,
                text("X"),
                Optional.of(ThrowableGraphMarker.truncation(
                        ThrowableGraphMarker.Kind.DEPTH_LIMIT)),
                List.of());
        assertThrows(
                IllegalArgumentException.class,
                () -> new NormalizedThrowableGraph(
                        earlyDepth,
                        new NormalizationLimits(4, 4, 1, 1, 8),
                        zeroStatistics()));

        var earlyNode = node(
                0,
                text("X"),
                Optional.of(ThrowableGraphMarker.truncation(
                        ThrowableGraphMarker.Kind.NODE_LIMIT)),
                List.of());
        assertThrows(
                IllegalArgumentException.class,
                () -> new NormalizedThrowableGraph(
                        earlyNode,
                        new NormalizationLimits(4, 4, 1, 1, 8),
                        zeroStatistics()));
    }

    private static void assertAllowedResultGraph(Type rootType) {
        var visited = new HashSet<Type>();
        var pending = new ArrayDeque<Type>();
        pending.add(rootType);

        while (!pending.isEmpty()) {
            var type = pending.removeFirst();
            if (!visited.add(type)) {
                continue;
            }
            if (type instanceof ParameterizedType parameterized) {
                pending.add(parameterized.getRawType());
                for (var argument : parameterized.getActualTypeArguments()) {
                    pending.add(argument);
                }
                continue;
            }
            if (!(type instanceof Class<?> modelType)) {
                continue;
            }
            assertAllowedType(modelType);
            if (modelType.isSealed()) {
                for (var permitted : modelType.getPermittedSubclasses()) {
                    pending.add(permitted);
                }
            }
            if (modelType.isEnum()
                    || modelType.isPrimitive()
                    || modelType == String.class
                    || modelType == Integer.class
                    || modelType == Long.class
                    || modelType == Boolean.class
                    || modelType == List.class
                    || modelType == java.util.Optional.class) {
                continue;
            }
            assertTrue(
                    modelType.isRecord()
                            || modelType.isSealed()
                            || Modifier.isFinal(modelType.getModifiers()));
            if (modelType.isRecord()) {
                for (var component : modelType.getRecordComponents()) {
                    pending.add(component.getGenericType());
                }
            } else {
                for (var field : modelType.getDeclaredFields()) {
                    if (!Modifier.isStatic(field.getModifiers())) {
                        pending.add(field.getGenericType());
                    }
                }
            }
        }
    }

    private static void assertAllowedType(Class<?> type) {
        assertFalse(type.isArray(), () -> "result retains array type " + type);
        assertFalse(Throwable.class.isAssignableFrom(type),
                () -> "result retains throwable type " + type);
        assertFalse(StackTraceElement.class.isAssignableFrom(type),
                () -> "result retains stack frame type " + type);
        assertFalse(type == Class.class, "result retains Class");
        assertFalse(ClassLoader.class.isAssignableFrom(type),
                () -> "result retains class-loader type " + type);
        assertFalse(Path.class.isAssignableFrom(type), () -> "result retains path type " + type);
        assertFalse(
                type == Runnable.class
                        || Callable.class.isAssignableFrom(type)
                        || Supplier.class.isAssignableFrom(type)
                        || type.getPackageName().equals("java.util.function"),
                () -> "result retains callback type " + type);
        assertFalse(
                Collection.class.isAssignableFrom(type) && type != List.class,
                () -> "result retains unsupported mutable collection type " + type);
        if (type.getPackageName().startsWith("java.")) {
            assertTrue(
                    type.isPrimitive()
                            || type == String.class
                            || type == Integer.class
                            || type == Long.class
                            || type == Boolean.class
                            || type == List.class
                            || type == java.util.Optional.class,
                    () -> "result retains unsupported Java type " + type);
        }
    }

    private static NormalizedText text(String value) {
        return new NormalizedText(
                new CandidateText(value), value.length(), 0, 0, 0, java.util.Optional.empty());
    }

    private static NormalizedThrowable node(
            int id,
            NormalizedText className,
            Optional<NormalizedThrowableElement> cause,
            List<NormalizedThrowableElement> suppressed) {
        return new NormalizedThrowable(
                id,
                className,
                NormalizedMessage.absent(),
                NormalizedThrowable.StackTraceState.EMPTY,
                List.of(),
                0,
                Optional.empty(),
                cause,
                suppressed.isEmpty()
                        ? NormalizedThrowable.SuppressedState.EMPTY
                        : NormalizedThrowable.SuppressedState.PRESENT,
                suppressed,
                0,
                Optional.empty());
    }

    private static NormalizationStatistics zeroStatistics() {
        return new NormalizationStatistics(
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }
}
