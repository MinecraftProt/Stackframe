package org.minecraftprot.stackframe.diagnostic;

import java.util.List;

/**
 * Immutable producer-ordered retained values plus the exact number deliberately
 * excluded. A non-zero omitted count must be matched by one {@link Omission} in
 * the completed document.
 */
public record BoundedList<T>(List<T> items, int omittedCount) {
    public BoundedList {
        Validation.required(items, "$.items");
        try {
            items = List.copyOf(items);
        } catch (NullPointerException exception) {
            throw new DiagnosticValidationException("$.items", "must not contain null");
        }
        Validation.nonNegative(omittedCount, "$.omittedCount");
    }

    public static <T> BoundedList<T> empty() {
        return new BoundedList<>(List.of(), 0);
    }

    public static <T> BoundedList<T> of(List<? extends T> items) {
        return copy(items, 0);
    }

    public static <T> BoundedList<T> withOmitted(List<? extends T> items, int omittedCount) {
        return copy(items, omittedCount);
    }

    private static <T> BoundedList<T> copy(List<? extends T> items, int omittedCount) {
        Validation.required(items, "$.items");
        try {
            return new BoundedList<>(List.copyOf(items), omittedCount);
        } catch (NullPointerException exception) {
            throw new DiagnosticValidationException("$.items", "must not contain null");
        }
    }
}
