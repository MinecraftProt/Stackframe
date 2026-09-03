package org.minecraftprot.stackframe.normalization;

/** One retained position from a throwable's stack-trace array. */
public sealed interface NormalizedFrameEntry permits NormalizedStackFrame, MalformedStackFrame {
    int originalIndex();
}
