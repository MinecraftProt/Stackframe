package org.minecraftprot.stackframe.normalization;

/** A retained throwable node or an explicit reference/truncation marker. */
public sealed interface NormalizedThrowableElement
        permits NormalizedThrowable, ThrowableGraphMarker {
}
