package org.minecraftprot.stackframe.normalization;

/** Exact accounting for retained values and observable normalization omissions. */
public record NormalizationStatistics(
        int retainedNodes,
        long retainedFrames,
        long omittedFrames,
        long malformedFrames,
        long retainedSuppressedEdges,
        long omittedSuppressedEdges,
        long cycleReferences,
        long sharedReferences,
        long depthTruncations,
        long nodeLimitTruncations,
        long scalarWorkTruncations,
        long unreadableValues,
        long omittedTextUtf16Units,
        long retainedTextCodePoints,
        long retainedTextUtf8Bytes,
        long scalarWorkUnits) {
    public NormalizationStatistics {
        NormalizationValidation.nonNegative(retainedNodes, "retainedNodes");
        NormalizationValidation.nonNegative(retainedFrames, "retainedFrames");
        NormalizationValidation.nonNegative(omittedFrames, "omittedFrames");
        NormalizationValidation.nonNegative(malformedFrames, "malformedFrames");
        NormalizationValidation.nonNegative(retainedSuppressedEdges, "retainedSuppressedEdges");
        NormalizationValidation.nonNegative(omittedSuppressedEdges, "omittedSuppressedEdges");
        NormalizationValidation.nonNegative(cycleReferences, "cycleReferences");
        NormalizationValidation.nonNegative(sharedReferences, "sharedReferences");
        NormalizationValidation.nonNegative(depthTruncations, "depthTruncations");
        NormalizationValidation.nonNegative(nodeLimitTruncations, "nodeLimitTruncations");
        NormalizationValidation.nonNegative(scalarWorkTruncations, "scalarWorkTruncations");
        NormalizationValidation.nonNegative(unreadableValues, "unreadableValues");
        NormalizationValidation.nonNegative(
                omittedTextUtf16Units, "omittedTextUtf16Units");
        NormalizationValidation.nonNegative(retainedTextCodePoints, "retainedTextCodePoints");
        NormalizationValidation.nonNegative(retainedTextUtf8Bytes, "retainedTextUtf8Bytes");
        NormalizationValidation.nonNegative(scalarWorkUnits, "scalarWorkUnits");
    }
}
