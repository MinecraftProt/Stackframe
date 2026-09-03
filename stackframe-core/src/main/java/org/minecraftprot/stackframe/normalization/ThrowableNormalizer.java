package org.minecraftprot.stackframe.normalization;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.minecraftprot.stackframe.diagnostic.CandidateText;

/**
 * Iteratively copies a throwable graph under deterministic work and retention
 * limits after each public {@link Throwable} accessor returns.
 *
 * <p>The JDK materializes complete defensive arrays for stack frames and suppressed
 * exceptions before returning them. The normalizer retains and inspects only the
 * configured prefix, but cannot preempt that accessor allocation without relying
 * on unsupported JDK internals.
 */
public final class ThrowableNormalizer {
    private static final int THROWABLE_ACCESSOR_WORK_UNITS = 4;

    private final NormalizationLimits limits;

    public ThrowableNormalizer() {
        this(NormalizationLimits.DEFAULTS);
    }

    public ThrowableNormalizer(NormalizationLimits limits) {
        this.limits = Objects.requireNonNull(limits, "limits");
    }

    public NormalizedThrowableGraph normalize(Throwable throwable) {
        Objects.requireNonNull(throwable, "throwable");

        var seen = new IdentityHashMap<Throwable, MutableNode>();
        var tasks = new ArrayDeque<Task>();
        var rootSlot = new ElementSlot();
        var counters = new Counters();
        tasks.push(new VisitTask(throwable, 1, rootSlot));

        while (!tasks.isEmpty()) {
            var task = tasks.pop();
            if (task instanceof FinishTask finish) {
                finish(finish.node());
            } else if (task instanceof VisitTask visit) {
                visit(visit, seen, tasks, counters);
            } else {
                throw new IllegalStateException("unknown normalization task");
            }
        }

        var root = (NormalizedThrowable) rootSlot.resolve();
        return new NormalizedThrowableGraph(root, limits, counters.statistics());
    }

    private void visit(
            VisitTask visit,
            IdentityHashMap<Throwable, MutableNode> seen,
            ArrayDeque<Task> tasks,
            Counters counters) {
        var known = seen.get(visit.source());
        if (known != null) {
            if (known.active) {
                visit.destination().element = ThrowableGraphMarker.reference(
                        ThrowableGraphMarker.Kind.CYCLE_REFERENCE, known.id);
                counters.cycleReferences = add(counters.cycleReferences, 1);
            } else {
                visit.destination().element = ThrowableGraphMarker.reference(
                        ThrowableGraphMarker.Kind.SHARED_REFERENCE, known.id);
                counters.sharedReferences = add(counters.sharedReferences, 1);
            }
            return;
        }
        if (visit.depth() > limits.maxDepth()) {
            visit.destination().element =
                    ThrowableGraphMarker.truncation(ThrowableGraphMarker.Kind.DEPTH_LIMIT);
            counters.depthTruncations = add(counters.depthTruncations, 1);
            return;
        }
        if (counters.retainedNodes >= limits.maxNodes()) {
            visit.destination().element =
                    ThrowableGraphMarker.truncation(ThrowableGraphMarker.Kind.NODE_LIMIT);
            counters.nodeLimitTruncations = add(counters.nodeLimitTruncations, 1);
            return;
        }
        if (counters.remainingScalarWork(limits) < THROWABLE_ACCESSOR_WORK_UNITS) {
            visit.destination().element =
                    ThrowableGraphMarker.truncation(ThrowableGraphMarker.Kind.SCALAR_WORK_LIMIT);
            counters.scalarWorkTruncations = add(counters.scalarWorkTruncations, 1);
            return;
        }

        var node = new MutableNode(counters.retainedNodes);
        counters.retainedNodes = Math.incrementExact(counters.retainedNodes);
        counters.scalarWorkUnits =
                add(counters.scalarWorkUnits, THROWABLE_ACCESSOR_WORK_UNITS);
        visit.destination().child = node;
        seen.put(visit.source(), node);

        var children = snapshot(visit.source(), node, counters);
        tasks.push(new FinishTask(node));
        for (var index = children.suppressed().size() - 1; index >= 0; index--) {
            var child = children.suppressed().get(index);
            tasks.push(new VisitTask(
                    child.source(),
                    Math.incrementExact(visit.depth()),
                    child.destination()));
        }
        if (children.cause() != null) {
            tasks.push(new VisitTask(
                    children.cause(), Math.incrementExact(visit.depth()), node.cause));
        }
    }

    private SourceChildren snapshot(Throwable source, MutableNode node, Counters counters) {
        node.className = copyText(source.getClass().getName(), counters);
        node.message = copyMessage(source, counters);
        copyFrames(source, node, counters);

        Throwable cause = null;
        try {
            cause = source.getCause();
            if (cause != null) {
                node.cause = new ElementSlot();
            }
        } catch (Throwable failure) {
            rethrowIfFatal(failure);
            node.cause = new ElementSlot();
            node.cause.element = ThrowableGraphMarker.unreadableCause();
            counters.unreadableValues = add(counters.unreadableValues, 1);
        }

        List<PendingChild> retainedSuppressed = List.of();
        try {
            var suppressed = source.getSuppressed();
            if (suppressed == null) {
                node.suppressedState = NormalizedThrowable.SuppressedState.NULL_ARRAY;
                counters.unreadableValues = add(counters.unreadableValues, 1);
            } else if (suppressed.length == 0) {
                node.suppressedState = NormalizedThrowable.SuppressedState.EMPTY;
            } else {
                var perThrowableCount =
                        Math.min(suppressed.length, limits.maxSuppressedPerThrowable());
                var retainedCapacity = (int) Math.min(
                        perThrowableCount, counters.remainingScalarWork(limits));
                var retained = new ArrayList<PendingChild>(retainedCapacity);
                for (var index = 0;
                        index < perThrowableCount && counters.remainingScalarWork(limits) > 0;
                        index++) {
                    counters.scalarWorkUnits = add(counters.scalarWorkUnits, 1);
                    var child = suppressed[index];
                    if (child == null) {
                        node.suppressed.add(ElementSlot.malformedSuppressed());
                        counters.unreadableValues = add(counters.unreadableValues, 1);
                    } else {
                        var destination = new ElementSlot();
                        retained.add(new PendingChild(child, destination));
                        node.suppressed.add(destination);
                    }
                }
                var retainedCount = node.suppressed.size();
                node.omittedSuppressedCount = (long) suppressed.length - retainedCount;
                if (node.omittedSuppressedCount > 0) {
                    node.suppressedTruncationReason = Optional.of(
                            retainedCount == limits.maxSuppressedPerThrowable()
                                    ? NormalizedThrowable.SuppressedTruncationReason
                                            .PER_THROWABLE_LIMIT
                                    : NormalizedThrowable.SuppressedTruncationReason
                                            .SCALAR_WORK_LIMIT);
                }
                counters.omittedSuppressedEdges =
                        add(counters.omittedSuppressedEdges, node.omittedSuppressedCount);
                counters.retainedSuppressedEdges =
                        add(counters.retainedSuppressedEdges, retainedCount);
                node.suppressedState = NormalizedThrowable.SuppressedState.PRESENT;
                retainedSuppressed = retained;
            }
        } catch (Throwable failure) {
            rethrowIfFatal(failure);
            node.suppressedState = NormalizedThrowable.SuppressedState.UNREADABLE;
            counters.unreadableValues = add(counters.unreadableValues, 1);
        }
        return new SourceChildren(cause, retainedSuppressed);
    }

    private NormalizedMessage copyMessage(Throwable source, Counters counters) {
        try {
            var message = source.getMessage();
            return message == null
                    ? NormalizedMessage.absent()
                    : NormalizedMessage.present(copyText(message, counters));
        } catch (Throwable failure) {
            rethrowIfFatal(failure);
            counters.unreadableValues = add(counters.unreadableValues, 1);
            return NormalizedMessage.unreadable();
        }
    }

    private void copyFrames(Throwable source, MutableNode node, Counters counters) {
        final StackTraceElement[] frames;
        try {
            frames = source.getStackTrace();
        } catch (Throwable failure) {
            rethrowIfFatal(failure);
            node.stackTraceState = NormalizedThrowable.StackTraceState.UNREADABLE;
            counters.unreadableValues = add(counters.unreadableValues, 1);
            return;
        }
        if (frames == null) {
            node.stackTraceState = NormalizedThrowable.StackTraceState.NULL_ARRAY;
            counters.unreadableValues = add(counters.unreadableValues, 1);
            return;
        }
        if (frames.length == 0) {
            node.stackTraceState = NormalizedThrowable.StackTraceState.EMPTY;
            return;
        }

        var perThrowableCount = Math.min(frames.length, limits.maxFramesPerThrowable());
        for (var index = 0;
                index < perThrowableCount
                        && counters.retainedFrames < limits.maxTotalFrames()
                        && counters.remainingScalarWork(limits) > 0;
                index++) {
            counters.scalarWorkUnits = add(counters.scalarWorkUnits, 1);
            var frame = copyFrame(frames[index], index, counters);
            node.stackFrames.add(frame);
            counters.retainedFrames = add(counters.retainedFrames, 1);
            if (frame instanceof MalformedStackFrame) {
                counters.malformedFrames = add(counters.malformedFrames, 1);
            }
        }
        var retainedCount = node.stackFrames.size();
        node.omittedFrameCount = (long) frames.length - retainedCount;
        if (node.omittedFrameCount > 0) {
            node.frameTruncationReason = Optional.of(
                    retainedCount == limits.maxFramesPerThrowable()
                            ? NormalizedThrowable.FrameTruncationReason.PER_THROWABLE_LIMIT
                            : counters.retainedFrames >= limits.maxTotalFrames()
                                    ? NormalizedThrowable.FrameTruncationReason.TOTAL_FRAME_LIMIT
                                    : NormalizedThrowable.FrameTruncationReason.SCALAR_WORK_LIMIT);
        }
        counters.omittedFrames = add(counters.omittedFrames, node.omittedFrameCount);
        node.stackTraceState = NormalizedThrowable.StackTraceState.PRESENT;
    }

    private NormalizedFrameEntry copyFrame(
            StackTraceElement frame, int originalIndex, Counters counters) {
        if (frame == null) {
            return new MalformedStackFrame(
                    originalIndex, MalformedStackFrame.Reason.NULL_ELEMENT);
        }
        try {
            var declaringClass = frame.getClassName();
            if (declaringClass == null) {
                return new MalformedStackFrame(
                        originalIndex, MalformedStackFrame.Reason.NULL_DECLARING_CLASS);
            }
            var methodName = frame.getMethodName();
            if (methodName == null) {
                return new MalformedStackFrame(
                        originalIndex, MalformedStackFrame.Reason.NULL_METHOD_NAME);
            }
            var moduleName = frame.getModuleName();
            return new NormalizedStackFrame(
                    originalIndex,
                    copyText(declaringClass, counters),
                    copyText(methodName, counters),
                    copyOptionalText(frame.getFileName(), counters),
                    frame.getLineNumber(),
                    copyOptionalText(frame.getClassLoaderName(), counters),
                    copyOptionalText(moduleName, counters),
                    copyOptionalText(frame.getModuleVersion(), counters),
                    category());
        } catch (Throwable failure) {
            rethrowIfFatal(failure);
            counters.unreadableValues = add(counters.unreadableValues, 1);
            return new MalformedStackFrame(originalIndex, MalformedStackFrame.Reason.UNREADABLE);
        }
    }

    private Optional<NormalizedText> copyOptionalText(String value, Counters counters) {
        return value == null ? Optional.empty() : Optional.of(copyText(value, counters));
    }

    private NormalizedText copyText(String source, Counters counters) {
        var retained = new StringBuilder(Math.min(source.length(), limits.maxTextCodePoints()));
        var sourceIndex = 0;
        var retainedCodePoints = 0;
        var malformedUnits = 0;
        var extraInspectedUnits = 0;
        NormalizedText.TruncationReason truncationReason = null;
        while (sourceIndex < source.length()) {
            if (retainedCodePoints >= limits.maxTextCodePoints()) {
                truncationReason = NormalizedText.TruncationReason.PER_VALUE_CODE_POINT_LIMIT;
                break;
            }
            if (counters.retainedTextCodePoints >= limits.maxTotalTextCodePoints()) {
                truncationReason = NormalizedText.TruncationReason.TOTAL_CODE_POINT_LIMIT;
                break;
            }
            if (counters.remainingTextUtf8Bytes(limits) < 4) {
                truncationReason = NormalizedText.TruncationReason.TOTAL_UTF8_BYTE_LIMIT;
                break;
            }
            var remainingSourceUnits = source.length() - sourceIndex;
            var requiredWork = Math.min(remainingSourceUnits, 2);
            if (counters.remainingScalarWork(limits) < requiredWork) {
                truncationReason = NormalizedText.TruncationReason.SCALAR_WORK_LIMIT;
                break;
            }

            var first = source.charAt(sourceIndex);
            int retainedUtf16Units;
            int retainedCodePoint;
            var extraInspectedWork = 0;
            if (Character.isHighSurrogate(first)
                    && sourceIndex + 1 < source.length()
                    && Character.isLowSurrogate(source.charAt(sourceIndex + 1))) {
                retained.append(first).append(source.charAt(sourceIndex + 1));
                retainedUtf16Units = 2;
                retainedCodePoint = Character.toCodePoint(first, source.charAt(sourceIndex + 1));
            } else if (Character.isSurrogate(first)) {
                retained.append('\uFFFD');
                retainedUtf16Units = 1;
                retainedCodePoint = 0xFFFD;
                malformedUnits++;
                if (Character.isHighSurrogate(first) && sourceIndex + 1 < source.length()) {
                    extraInspectedUnits++;
                    extraInspectedWork = 1;
                }
            } else {
                retained.append(first);
                retainedUtf16Units = 1;
                retainedCodePoint = first;
            }
            sourceIndex += retainedUtf16Units;
            retainedCodePoints++;
            counters.retainedTextCodePoints = add(counters.retainedTextCodePoints, 1);
            counters.retainedTextUtf8Bytes =
                    add(counters.retainedTextUtf8Bytes, utf8Bytes(retainedCodePoint));
            counters.scalarWorkUnits =
                    add(counters.scalarWorkUnits, retainedUtf16Units + extraInspectedWork);
        }
        var omittedUnits = source.length() - sourceIndex;
        counters.omittedTextUtf16Units =
                add(counters.omittedTextUtf16Units, omittedUnits);
        return new NormalizedText(
                new CandidateText(retained.toString()),
                source.length(),
                omittedUnits,
                malformedUnits,
                extraInspectedUnits,
                Optional.ofNullable(truncationReason));
    }

    private static NormalizedStackFrame.Category category() {
        return NormalizedStackFrame.Category.UNKNOWN;
    }

    private static void finish(MutableNode node) {
        var suppressed = node.suppressed.stream()
                .map(ElementSlot::resolve)
                .toList();
        node.result = new NormalizedThrowable(
                node.id,
                node.className,
                node.message,
                node.stackTraceState,
                node.stackFrames,
                node.omittedFrameCount,
                node.frameTruncationReason,
                node.cause == null ? Optional.empty() : Optional.of(node.cause.resolve()),
                node.suppressedState,
                suppressed,
                node.omittedSuppressedCount,
                node.suppressedTruncationReason);
        node.active = false;
    }

    private static long add(long left, long right) {
        return Math.addExact(left, right);
    }

    private static int utf8Bytes(int codePoint) {
        if (codePoint <= 0x7F) {
            return 1;
        }
        if (codePoint <= 0x7FF) {
            return 2;
        }
        if (codePoint <= 0xFFFF) {
            return 3;
        }
        return 4;
    }

    /**
     * Only VM resource/integrity failures and asynchronous thread termination are
     * allowed to escape. AssertionError, LinkageError, and other accessor failures
     * are untrusted input failures and become unreadable markers.
     */
    @SuppressWarnings("removal")
    private static void rethrowIfFatal(Throwable failure) {
        if (failure instanceof VirtualMachineError virtualMachineError) {
            throw virtualMachineError;
        }
        if (failure instanceof ThreadDeath threadDeath) {
            throw threadDeath;
        }
    }

    private interface Task {
    }

    private record VisitTask(Throwable source, long depth, ElementSlot destination)
            implements Task {
    }

    private record FinishTask(MutableNode node) implements Task {
    }

    private record SourceChildren(Throwable cause, List<PendingChild> suppressed) {
    }

    private record PendingChild(Throwable source, ElementSlot destination) {
    }

    private static final class ElementSlot {
        private MutableNode child;
        private NormalizedThrowableElement element;

        static ElementSlot malformedSuppressed() {
            var slot = new ElementSlot();
            slot.element = ThrowableGraphMarker.malformedSuppressed();
            return slot;
        }

        NormalizedThrowableElement resolve() {
            return element != null ? element : Objects.requireNonNull(child.result);
        }
    }

    private static final class MutableNode {
        private final int id;
        private boolean active = true;
        private NormalizedText className;
        private NormalizedMessage message;
        private NormalizedThrowable.StackTraceState stackTraceState;
        private final List<NormalizedFrameEntry> stackFrames = new ArrayList<>();
        private long omittedFrameCount;
        private Optional<NormalizedThrowable.FrameTruncationReason> frameTruncationReason =
                Optional.empty();
        private ElementSlot cause;
        private NormalizedThrowable.SuppressedState suppressedState;
        private final List<ElementSlot> suppressed = new ArrayList<>();
        private long omittedSuppressedCount;
        private Optional<NormalizedThrowable.SuppressedTruncationReason>
                suppressedTruncationReason = Optional.empty();
        private NormalizedThrowable result;

        private MutableNode(int id) {
            this.id = id;
        }
    }

    private static final class Counters {
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

        long remainingScalarWork(NormalizationLimits limits) {
            return (long) limits.maxScalarWorkUnits() - scalarWorkUnits;
        }

        long remainingTextUtf8Bytes(NormalizationLimits limits) {
            return (long) limits.maxTotalTextUtf8Bytes() - retainedTextUtf8Bytes;
        }

        NormalizationStatistics statistics() {
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
