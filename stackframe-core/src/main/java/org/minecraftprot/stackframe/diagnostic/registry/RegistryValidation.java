package org.minecraftprot.stackframe.diagnostic.registry;

import java.util.Collection;
import java.util.regex.Pattern;

final class RegistryValidation {
    private RegistryValidation() {
    }

    static <T> T required(T value, String field) {
        if (value == null) {
            throw new RegistryValidationException(field + " is required");
        }
        return value;
    }

    static String identifier(String value, Pattern pattern, String field) {
        required(value, field);
        if (!pattern.matcher(value).matches()) {
            throw new RegistryValidationException(field + " has an invalid format");
        }
        return value;
    }

    static String text(String value, String field) {
        required(value, field);
        if (value.isBlank()) {
            throw new RegistryValidationException(field + " must not be blank");
        }
        if (value.codePointCount(0, value.length()) > 4_096) {
            throw new RegistryValidationException(field + " exceeds 4096 code points");
        }
        value.codePoints().forEach(codePoint -> {
            if (Character.isISOControl(codePoint)
                    || codePoint == 0x007F
                    || codePoint == 0x2028
                    || codePoint == 0x2029) {
                throw new RegistryValidationException(field + " must be single-line safe text");
            }
        });
        return value;
    }

    static <T> void noNullElements(Collection<T> values, String field) {
        required(values, field);
        if (values.stream().anyMatch(value -> value == null)) {
            throw new RegistryValidationException(field + " must not contain null");
        }
    }
}
