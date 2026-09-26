package org.minecraftprot.stackframe.fabric.config;

import java.nio.file.Path;
import org.minecraftprot.stackframe.correlation.DiagnosticCorrelator;
import org.minecraftprot.stackframe.diagnostic.DiagnosticCode;
import org.minecraftprot.stackframe.trace.TraceRecorder;
import org.minecraftprot.stackframe.trace.TraceRetention;

/** Version 1 settings with only behaviors implemented by the Fabric pipeline. */
public record StackframeConfiguration(
        Output output,
        Path traceDirectory,
        TraceRetention.Policy retention,
        DiagnosticCorrelator.Config deduplication,
        boolean includeGeneric,
        boolean excludeGeneric) {
    public enum Output {
        AUTO,
        ANSI,
        PLAIN
    }

    public static final StackframeConfiguration DEFAULT = new StackframeConfiguration(
            Output.PLAIN,
            TraceRecorder.defaultDirectory(),
            TraceRetention.Policy.manual(),
            DiagnosticCorrelator.DEFAULT_CONFIG,
            true,
            false);

    public StackframeConfiguration {
        if (output == null || traceDirectory == null || retention == null
                || deduplication == null || (!includeGeneric && excludeGeneric)) {
            throw new IllegalArgumentException("invalid Stackframe configuration");
        }
        if (traceDirectory.isAbsolute() || traceDirectory.normalize().startsWith("..")
                || traceDirectory.toString().equals(".")) {
            throw new IllegalArgumentException("trace directory must stay inside the server directory");
        }
    }

    public boolean includes(DiagnosticCode code) {
        return includeGeneric && !excludeGeneric && "SF0001".equals(code.value());
    }
}
