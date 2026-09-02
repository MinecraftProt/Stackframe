package org.minecraftprot.stackframe.diagnostic;

/**
 * Aggregate metadata for completed transformations. It contains no source value,
 * location, hash, prefix, suffix, or protected length.
 */
public record RedactionNotice(
        RedactionMarker marker,
        TextDisposition transformation,
        int occurrenceCount) {

    public RedactionNotice {
        marker = Validation.required(marker, "$.redactionNotice.marker");
        transformation = Validation.required(
                transformation, "$.redactionNotice.transformation");
        if (transformation == TextDisposition.VISIBLE) {
            throw new RedactionValidationException(
                    "$.redactionNotice.transformation", "must describe a non-visible transformation");
        }
        if (occurrenceCount < 1) {
            throw new RedactionValidationException(
                    "$.redactionNotice.occurrenceCount", "must be positive");
        }
    }
}
