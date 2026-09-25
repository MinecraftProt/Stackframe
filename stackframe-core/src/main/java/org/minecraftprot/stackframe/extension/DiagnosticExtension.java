package org.minecraftprot.stackframe.extension;

import java.util.List;

/** Inspect one bounded, redacted, loader-neutral event without retaining it. */
@FunctionalInterface
public interface DiagnosticExtension {
    List<ExtensionFinding> inspect(ExtensionEvent event) throws Exception;
}
