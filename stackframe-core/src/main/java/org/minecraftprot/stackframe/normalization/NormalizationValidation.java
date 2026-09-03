package org.minecraftprot.stackframe.normalization;

import java.util.List;
import java.util.Objects;

final class NormalizationValidation {
    private NormalizationValidation() {
    }

    static int positive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    static long nonNegative(long value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must be non-negative");
        }
        return value;
    }

    static int nonNegative(int value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must be non-negative");
        }
        return value;
    }

    static <T> List<T> immutableList(List<? extends T> values, String name) {
        Objects.requireNonNull(values, name);
        try {
            return List.copyOf(values);
        } catch (NullPointerException exception) {
            throw new IllegalArgumentException(name + " must not contain null", exception);
        }
    }
}
