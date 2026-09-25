package org.minecraftprot.stackframe.trace;

/** Safe failure category; never contains a path, exception message, or source throwable. */
public enum TraceWriteFailure {
    STORAGE_UNAVAILABLE,
    PERMISSION_DENIED,
    TRACE_UNREADABLE,
    IDENTIFIER_EXHAUSTED
}
