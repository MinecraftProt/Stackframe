package org.minecraftprot.stackframe.normalization;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.Set;

final class NormalizedGraphValidator {
    private static final int THROWABLE_ACCESSOR_WORK_UNITS = 4;

    private NormalizedGraphValidator() {
    }

    static void validate(
            NormalizedThrowable root,
            NormalizationLimits limits,
            NormalizationStatistics statistics) {
        var state = new ValidationState(limits);
        var tasks = new ArrayDeque<Task>();
        tasks.push(new VisitTask(root, 1, Relation.ROOT));

        while (!tasks.isEmpty()) {
            var task = tasks.pop();
            if (task instanceof ExitTask exit) {
                if (!state.activeIds.remove(exit.node().id())) {
                    throw invalid("node " + exit.node().id() + " was not active");
                }
                state.completedIds.add(exit.node().id());
            } else if (task instanceof VisitTask visit) {
                if (visit.element() instanceof NormalizedThrowable node) {
                    validateNode(node, visit.depth(), state, tasks);
                } else if (visit.element() instanceof ThrowableGraphMarker marker) {
                    validateMarker(marker, visit.depth(), visit.relation(), state);
                } else {
                    throw invalid("unknown normalized throwable element");
                }
            }
        }

        var computed = state.statistics();
        if (!computed.equals(statistics)) {
            throw invalid("statistics do not exactly match graph contents: expected " + computed);
        }
    }

    private static void validateNode(
            NormalizedThrowable node,
            long depth,
            ValidationState state,
            ArrayDeque<Task> tasks) {
        if (depth > state.limits.maxDepth()) {
            throw invalid("retained node exceeds maxDepth");
        }
        if (state.retainedNodes >= state.limits.maxNodes()) {
            throw invalid("retained node exceeds maxNodes");
        }
        if (node.id() != state.retainedNodes) {
            throw invalid("node IDs must be unique contiguous cause-first DFS positions");
        }
        if (state.seenNodes.put(node, Boolean.TRUE) != null) {
            throw invalid("a retained node instance cannot occur more than once");
        }
        if (!state.activeIds.add(node.id())) {
            throw invalid("duplicate active node ID " + node.id());
        }
        state.retainedNodes = Math.incrementExact(state.retainedNodes);
        state.addScalarWork(THROWABLE_ACCESSOR_WORK_UNITS);

        state.text(node.className());
        if (node.message().state() == NormalizedMessage.State.PRESENT) {
            state.text(node.message().text().orElseThrow());
        } else if (node.message().state() == NormalizedMessage.State.UNREADABLE) {
            state.unreadableValues = add(state.unreadableValues, 1);
        }

        validateFrames(node, state);
        validateSuppressed(node, state);

        tasks.push(new ExitTask(node));
        var childDepth = Math.incrementExact(depth);
        for (var index = node.suppressed().size() - 1; index >= 0; index--) {
            tasks.push(new VisitTask(
                    node.suppressed().get(index), childDepth, Relation.SUPPRESSED));
        }
        node.cause().ifPresent(cause ->
                tasks.push(new VisitTask(cause, childDepth, Relation.CAUSE)));
    }

    private static void validateFrames(NormalizedThrowable node, ValidationState state) {
        if (node.stackFrames().size() > state.limits.maxFramesPerThrowable()) {
            throw invalid("stackFrames exceeds maxFramesPerThrowable");
        }
        if ((long) state.retainedFrames + node.stackFrames().size()
                > state.limits.maxTotalFrames()) {
            throw invalid("stackFrames exceeds maxTotalFrames");
        }

        for (var index = 0; index < node.stackFrames().size(); index++) {
            var frame = node.stackFrames().get(index);
            if (frame.originalIndex() != index) {
                throw invalid("retained frame indexes must be contiguous source positions");
            }
            state.addScalarWork(1);
            state.retainedFrames = add(state.retainedFrames, 1);
            if (frame instanceof MalformedStackFrame malformed) {
                state.malformedFrames = add(state.malformedFrames, 1);
                if (malformed.reason() == MalformedStackFrame.Reason.UNREADABLE) {
                    state.unreadableValues = add(state.unreadableValues, 1);
                }
            } else if (frame instanceof NormalizedStackFrame stackFrame) {
                state.text(stackFrame.declaringClass());
                state.text(stackFrame.methodName());
                stackFrame.fileName().ifPresent(state::text);
                stackFrame.classLoaderName().ifPresent(state::text);
                stackFrame.moduleName().ifPresent(state::text);
                stackFrame.moduleVersion().ifPresent(state::text);
                if (stackFrame.category() != NormalizedStackFrame.Category.UNKNOWN) {
                    throw invalid("throwable-provided frame text cannot establish provenance");
                }
            } else {
                throw invalid("unknown normalized frame entry");
            }
        }

        state.omittedFrames = add(state.omittedFrames, node.omittedFrameCount());
        if (node.stackTraceState() == NormalizedThrowable.StackTraceState.NULL_ARRAY
                || node.stackTraceState() == NormalizedThrowable.StackTraceState.UNREADABLE) {
            state.unreadableValues = add(state.unreadableValues, 1);
        }
        node.frameTruncationReason().ifPresent(reason -> {
            switch (reason) {
                case PER_THROWABLE_LIMIT -> {
                    if (node.stackFrames().size() != state.limits.maxFramesPerThrowable()) {
                        throw invalid("per-throwable frame truncation occurred before its limit");
                    }
                }
                case TOTAL_FRAME_LIMIT -> {
                    if (node.stackFrames().size() >= state.limits.maxFramesPerThrowable()
                            || state.retainedFrames != state.limits.maxTotalFrames()) {
                        throw invalid("total frame truncation does not match global budget");
                    }
                }
                case SCALAR_WORK_LIMIT -> {
                    if (node.stackFrames().size() >= state.limits.maxFramesPerThrowable()
                            || state.retainedFrames >= state.limits.maxTotalFrames()
                            || state.remainingScalarWork() != 0) {
                        throw invalid("frame work truncation does not match scalar budget");
                    }
                }
            }
        });
    }

    private static void validateSuppressed(NormalizedThrowable node, ValidationState state) {
        if (node.suppressed().size() > state.limits.maxSuppressedPerThrowable()) {
            throw invalid("suppressed exceeds maxSuppressedPerThrowable");
        }
        for (var ignored : node.suppressed()) {
            state.addScalarWork(1);
            state.retainedSuppressedEdges = add(state.retainedSuppressedEdges, 1);
        }
        state.omittedSuppressedEdges =
                add(state.omittedSuppressedEdges, node.omittedSuppressedCount());
        if (node.suppressedState() == NormalizedThrowable.SuppressedState.NULL_ARRAY
                || node.suppressedState() == NormalizedThrowable.SuppressedState.UNREADABLE) {
            state.unreadableValues = add(state.unreadableValues, 1);
        }
        node.suppressedTruncationReason().ifPresent(reason -> {
            switch (reason) {
                case PER_THROWABLE_LIMIT -> {
                    if (node.suppressed().size() != state.limits.maxSuppressedPerThrowable()) {
                        throw invalid("suppressed truncation occurred before its per-node limit");
                    }
                }
                case SCALAR_WORK_LIMIT -> {
                    if (node.suppressed().size() >= state.limits.maxSuppressedPerThrowable()
                            || state.remainingScalarWork() != 0) {
                        throw invalid("suppressed truncation does not match scalar budget");
                    }
                }
            }
        });
    }

    private static void validateMarker(
            ThrowableGraphMarker marker,
            long depth,
            Relation relation,
            ValidationState state) {
        if (relation == Relation.ROOT) {
            throw invalid("the graph root must be a retained throwable node");
        }
        switch (marker.kind()) {
            case CYCLE_REFERENCE -> {
                var target = marker.referencedNodeId().orElseThrow();
                if (!state.activeIds.contains(target)) {
                    throw invalid("cycle reference must target an active ancestor");
                }
                state.cycleReferences = add(state.cycleReferences, 1);
            }
            case SHARED_REFERENCE -> {
                var target = marker.referencedNodeId().orElseThrow();
                if (!state.completedIds.contains(target)) {
                    throw invalid("shared reference must target a completed earlier node");
                }
                state.sharedReferences = add(state.sharedReferences, 1);
            }
            case DEPTH_LIMIT -> {
                if (depth != (long) state.limits.maxDepth() + 1) {
                    throw invalid("depth marker must occur immediately beyond maxDepth");
                }
                state.depthTruncations = add(state.depthTruncations, 1);
            }
            case NODE_LIMIT -> {
                if (depth > state.limits.maxDepth()
                        || state.retainedNodes != state.limits.maxNodes()) {
                    throw invalid("node marker must occur after maxNodes were retained");
                }
                state.nodeLimitTruncations = add(state.nodeLimitTruncations, 1);
            }
            case SCALAR_WORK_LIMIT -> {
                if (depth > state.limits.maxDepth()
                        || state.retainedNodes >= state.limits.maxNodes()
                        || state.remainingScalarWork() >= THROWABLE_ACCESSOR_WORK_UNITS) {
                    throw invalid("work marker requires insufficient accessor work budget");
                }
                state.scalarWorkTruncations = add(state.scalarWorkTruncations, 1);
            }
            case UNREADABLE_CAUSE -> {
                if (relation != Relation.CAUSE) {
                    throw invalid("unreadable-cause marker may occur only on a cause edge");
                }
                state.unreadableValues = add(state.unreadableValues, 1);
            }
            case MALFORMED_SUPPRESSED -> {
                if (relation != Relation.SUPPRESSED) {
                    throw invalid(
                            "malformed-suppressed marker may occur only on a suppressed edge");
                }
                state.unreadableValues = add(state.unreadableValues, 1);
            }
        }
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException("invalid normalized throwable graph: " + message);
    }

    private static long add(long left, long right) {
        return Math.addExact(left, right);
    }

    private enum Relation {
        ROOT,
        CAUSE,
        SUPPRESSED
    }

    private interface Task {
    }

    private record VisitTask(
            NormalizedThrowableElement element, long depth, Relation relation) implements Task {
        private VisitTask {
            Objects.requireNonNull(element, "element");
            Objects.requireNonNull(relation, "relation");
        }
    }

    private record ExitTask(NormalizedThrowable node) implements Task {
    }

    private static final class ValidationState {
        private final NormalizationLimits limits;
        private final IdentityHashMap<NormalizedThrowable, Boolean> seenNodes =
                new IdentityHashMap<>();
        private final Set<Integer> activeIds = new HashSet<>();
        private final Set<Integer> completedIds = new HashSet<>();
        private int retainedNodes;
        private long retainedFrames;
        private long omittedFrames;
        private long malformedFrames;
        private long retainedSuppressedEdges;
        private long omittedSuppressedEdges;
        private long cycleReferences;
        private long sharedReferences;
        private long depthTruncations;
        private long nodeLimitTruncations;
        private long scalarWorkTruncations;
        private long unreadableValues;
        private long omittedTextUtf16Units;
        private long retainedTextCodePoints;
        private long retainedTextUtf8Bytes;
        private long scalarWorkUnits;

        private ValidationState(NormalizationLimits limits) {
            this.limits = limits;
        }

        private void text(NormalizedText text) {
            var value = text.value().value();
            var codePoints = value.codePointCount(0, value.length());
            var utf8Bytes = value.getBytes(StandardCharsets.UTF_8).length;
            if (codePoints > limits.maxTextCodePoints()) {
                throw invalid("text exceeds maxTextCodePoints");
            }
            retainedTextCodePoints = add(retainedTextCodePoints, codePoints);
            retainedTextUtf8Bytes = add(retainedTextUtf8Bytes, utf8Bytes);
            addScalarWork((long) value.length() + text.extraInspectedUtf16Units());
            omittedTextUtf16Units =
                    add(omittedTextUtf16Units, text.omittedUtf16Units());
            if (retainedTextCodePoints > limits.maxTotalTextCodePoints()) {
                throw invalid("text exceeds maxTotalTextCodePoints");
            }
            if (retainedTextUtf8Bytes > limits.maxTotalTextUtf8Bytes()) {
                throw invalid("text exceeds maxTotalTextUtf8Bytes");
            }

            text.truncationReason().ifPresent(reason -> {
                switch (reason) {
                    case PER_VALUE_CODE_POINT_LIMIT -> {
                        if (codePoints != limits.maxTextCodePoints()) {
                            throw invalid("per-value text truncation occurred before its limit");
                        }
                    }
                    case TOTAL_CODE_POINT_LIMIT -> {
                        if (codePoints >= limits.maxTextCodePoints()
                                || retainedTextCodePoints != limits.maxTotalTextCodePoints()) {
                            throw invalid("total code-point truncation does not match global budget");
                        }
                    }
                    case TOTAL_UTF8_BYTE_LIMIT -> {
                        if (codePoints >= limits.maxTextCodePoints()
                                || retainedTextCodePoints >= limits.maxTotalTextCodePoints()
                                || limits.maxTotalTextUtf8Bytes() - retainedTextUtf8Bytes >= 4) {
                            throw invalid("UTF-8 truncation does not match global budget");
                        }
                    }
                    case SCALAR_WORK_LIMIT -> {
                        var required = Math.min(text.omittedUtf16Units(), 2);
                        if (codePoints >= limits.maxTextCodePoints()
                                || retainedTextCodePoints >= limits.maxTotalTextCodePoints()
                                || limits.maxTotalTextUtf8Bytes() - retainedTextUtf8Bytes < 4
                                || remainingScalarWork() >= required) {
                            throw invalid("text work truncation does not match scalar budget");
                        }
                    }
                }
            });
        }

        private void addScalarWork(long units) {
            scalarWorkUnits = add(scalarWorkUnits, units);
            if (scalarWorkUnits > limits.maxScalarWorkUnits()) {
                throw invalid("graph exceeds maxScalarWorkUnits");
            }
        }

        private long remainingScalarWork() {
            return (long) limits.maxScalarWorkUnits() - scalarWorkUnits;
        }

        private NormalizationStatistics statistics() {
            return new NormalizationStatistics(
                    retainedNodes,
                    retainedFrames,
                    omittedFrames,
                    malformedFrames,
                    retainedSuppressedEdges,
                    omittedSuppressedEdges,
                    cycleReferences,
                    sharedReferences,
                    depthTruncations,
                    nodeLimitTruncations,
                    scalarWorkTruncations,
                    unreadableValues,
                    omittedTextUtf16Units,
                    retainedTextCodePoints,
                    retainedTextUtf8Bytes,
                    scalarWorkUnits);
        }
    }
}
