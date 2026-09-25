package org.minecraftprot.stackframe.renderer;

import java.io.IOException;

/** Destination for complete NDJSON lines staged by the renderer. */
@FunctionalInterface
public interface NdjsonRecordSink {
    /**
     * Receives a complete line, including its trailing LF, in one callback.
     * Implementations that share a destination must synchronize publication to
     * prevent interleaving. Atomic filesystem or network writes depend on the
     * destination and are not implied by a single callback.
     */
    void writeRecord(String completeLine) throws IOException;
}
