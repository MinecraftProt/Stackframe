package org.minecraftprot.stackframe.diagnostic;

import java.util.Collection;
import java.util.Optional;
import java.util.regex.Pattern;

final class Validation {
    private Validation() {
    }

    static <T> T required(T value, String path) {
        if (value == null) {
            throw new DiagnosticValidationException(path, "value is required");
        }
        return value;
    }

    static <T> Optional<T> optional(Optional<T> value, String path) {
        required(value, path);
        if (value.isPresent() && value.get() == null) {
            throw new DiagnosticValidationException(path, "optional value cannot contain null");
        }
        return value;
    }

    static String identifier(String value, Pattern pattern, String path, String description) {
        required(value, path);
        if (!pattern.matcher(value).matches()) {
            throw new IdentifierValidationException(path, "must match " + description);
        }
        return value;
    }

    static String safeText(String value, int maxCodePoints, boolean blankAllowed, String path) {
        required(value, path);
        if (!blankAllowed && value.isBlank()) {
            throw new TextValidationException(path, "must not be blank");
        }
        var codePoints = value.codePointCount(0, value.length());
        if (codePoints > maxCodePoints) {
            throw new LimitValidationException(
                    path, "contains " + codePoints + " code points; maximum is " + maxCodePoints);
        }
        for (var index = 0; index < value.length(); index++) {
            if (Character.isSurrogate(value.charAt(index))) {
                if (!Character.isHighSurrogate(value.charAt(index))
                        || index + 1 >= value.length()
                        || !Character.isLowSurrogate(value.charAt(index + 1))) {
                    throw new TextValidationException(path, "contains an unpaired UTF-16 surrogate");
                }
                index++;
            }
        }
        value.codePoints().forEach(codePoint -> {
            if (isForbiddenCodePoint(codePoint)) {
                throw new TextValidationException(
                        path, "contains forbidden code point U+%04X".formatted(codePoint));
            }
        });
        return value;
    }

    static void noNewline(String value, String path) {
        if (value.indexOf('\n') >= 0) {
            throw new TextValidationException(path, "must not contain a newline");
        }
    }

    static void size(Collection<?> values, int maximum, String path) {
        required(values, path);
        if (values.size() > maximum) {
            throw new LimitValidationException(
                    path, "contains " + values.size() + " items; maximum is " + maximum);
        }
    }

    static void positive(int value, String path) {
        if (value < 1) {
            throw new RangeValidationException(path, "must be positive");
        }
    }

    static void nonNegative(int value, String path) {
        if (value < 0) {
            throw new LimitValidationException(path, "must be non-negative");
        }
    }

    private static boolean isForbiddenCodePoint(int codePoint) {
        return (Character.isISOControl(codePoint) && codePoint != '\n')
                || codePoint == 0x007F
                || codePoint == 0x2028
                || codePoint == 0x2029
                || (codePoint >= 0x202A && codePoint <= 0x202E)
                || (codePoint >= 0x2066 && codePoint <= 0x2069);
    }
}
