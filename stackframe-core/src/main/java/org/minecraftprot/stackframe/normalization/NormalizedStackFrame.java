package org.minecraftprot.stackframe.normalization;

import java.util.Objects;
import java.util.Optional;

/** Scalar-only copy of one valid stack frame. */
public record NormalizedStackFrame(
        int originalIndex,
        NormalizedText declaringClass,
        NormalizedText methodName,
        Optional<NormalizedText> fileName,
        int lineNumber,
        Optional<NormalizedText> classLoaderName,
        Optional<NormalizedText> moduleName,
        Optional<NormalizedText> moduleVersion,
        Category category)
        implements NormalizedFrameEntry {
    public enum Category {
        JDK,
        UNKNOWN
    }

    public NormalizedStackFrame {
        NormalizationValidation.nonNegative(originalIndex, "originalIndex");
        Objects.requireNonNull(declaringClass, "declaringClass");
        Objects.requireNonNull(methodName, "methodName");
        Objects.requireNonNull(fileName, "fileName");
        Objects.requireNonNull(classLoaderName, "classLoaderName");
        Objects.requireNonNull(moduleName, "moduleName");
        Objects.requireNonNull(moduleVersion, "moduleVersion");
        Objects.requireNonNull(category, "category");
    }
}
