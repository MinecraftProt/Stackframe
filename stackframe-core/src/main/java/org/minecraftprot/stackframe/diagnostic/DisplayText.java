package org.minecraftprot.stackframe.diagnostic;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Sanitized post-policy text safe to pass to renderers. Redacted and omitted
 * values are derived solely from their category marker, so those dispositions
 * cannot retain caller-supplied protected text. Generalization policy owns the
 * safe replacement passed to {@link #generalized}.
 */
public final class DisplayText {
    private final String value;
    private final TextOrigin origin;
    private final Sensitivity sensitivity;
    private final TextDisposition disposition;
    private final Optional<RedactionMarker> marker;

    private DisplayText(
            String value,
            TextOrigin origin,
            Sensitivity sensitivity,
            TextDisposition disposition,
            Optional<RedactionMarker> marker) {
        this.value = Validation.safeText(
                value, ModelLimits.TEXT_CODE_POINTS, true, "$.displayText.value");
        this.origin = Validation.required(origin, "$.displayText.origin");
        this.sensitivity = Validation.required(sensitivity, "$.displayText.sensitivity");
        this.disposition = Validation.required(disposition, "$.displayText.disposition");
        this.marker = Validation.optional(marker, "$.displayText.marker");
        if (disposition == TextDisposition.VISIBLE) {
            if (sensitivity != Sensitivity.PUBLIC) {
                throw new RedactionValidationException(
                        "$.displayText.sensitivity", "VISIBLE text must be PUBLIC");
            }
            if (marker.isPresent()) {
                throw new RedactionValidationException(
                        "$.displayText.marker", "VISIBLE text must not have a redaction marker");
            }
        } else {
            if (marker.isEmpty()) {
                throw new RedactionValidationException(
                        "$.displayText.marker", disposition + " text requires a redaction marker");
            }
            if (value.isBlank()) {
                throw new RedactionValidationException(
                        "$.displayText.value", "non-visible replacement text must not be blank");
            }
        }
    }

    public static DisplayText visible(String value, TextOrigin origin) {
        return new DisplayText(
                value, origin, Sensitivity.PUBLIC, TextDisposition.VISIBLE, Optional.empty());
    }

    /**
     * Creates a canonical typed marker such as {@code <redacted:token>} without
     * accepting or retaining the removed value.
     */
    public static DisplayText redacted(
            TextOrigin origin, Sensitivity sensitivity, RedactionMarker marker) {
        return marked("redacted", origin, sensitivity, TextDisposition.REDACTED, marker);
    }

    /**
     * Creates a canonical typed marker such as {@code <omitted:world_data>}
     * without accepting or retaining the removed value.
     */
    public static DisplayText omitted(
            TextOrigin origin, Sensitivity sensitivity, RedactionMarker marker) {
        return marked("omitted", origin, sensitivity, TextDisposition.OMITTED, marker);
    }

    /**
     * Stores only policy-produced generalized text. Callers must release the
     * protected original before constructing this value.
     */
    public static DisplayText generalized(
            String generalizedValue,
            TextOrigin origin,
            Sensitivity sensitivity,
            RedactionMarker marker) {
        return new DisplayText(
                generalizedValue,
                origin,
                sensitivity,
                TextDisposition.GENERALIZED,
                Optional.of(Validation.required(marker, "$.displayText.marker")));
    }

    private static DisplayText marked(
            String action,
            TextOrigin origin,
            Sensitivity sensitivity,
            TextDisposition disposition,
            RedactionMarker marker) {
        Validation.required(marker, "$.displayText.marker");
        var replacement = "<" + action + ":"
                + marker.category().toLowerCase(Locale.ROOT) + ">";
        return new DisplayText(
                replacement, origin, sensitivity, disposition, Optional.of(marker));
    }

    public String value() {
        return value;
    }

    public TextOrigin origin() {
        return origin;
    }

    public Sensitivity sensitivity() {
        return sensitivity;
    }

    public TextDisposition disposition() {
        return disposition;
    }

    public Optional<RedactionMarker> marker() {
        return marker;
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || other instanceof DisplayText that
                && value.equals(that.value)
                && origin == that.origin
                && sensitivity == that.sensitivity
                && disposition == that.disposition
                && marker.equals(that.marker);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value, origin, sensitivity, disposition, marker);
    }

    @Override
    public String toString() {
        return "DisplayText[value=" + value
                + ", origin=" + origin
                + ", sensitivity=" + sensitivity
                + ", disposition=" + disposition
                + ", marker=" + marker + "]";
    }
}
