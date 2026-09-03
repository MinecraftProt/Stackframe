package org.minecraftprot.stackframe.renderer;

/** Utilities for Stackframe's fixed ANSI SGR output. */
public final class AnsiText {
    private AnsiText() {
    }

    public static String stripStyling(String value) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        var result = new StringBuilder(value.length());
        for (var index = 0; index < value.length();) {
            var codePoint = value.codePointAt(index);
            if (codePoint != 0x1B) {
                result.appendCodePoint(codePoint);
                index += Character.charCount(codePoint);
                continue;
            }
            if (index + 2 >= value.length() || value.charAt(index + 1) != '[') {
                throw new IllegalArgumentException("unsupported ANSI escape at UTF-16 index " + index);
            }
            var cursor = index + 2;
            while (cursor < value.length()) {
                var current = value.charAt(cursor);
                if (current >= 0x40 && current <= 0x7E) {
                    if (current != 'm') {
                        throw new IllegalArgumentException(
                                "unsupported ANSI CSI command at UTF-16 index " + index);
                    }
                    cursor++;
                    break;
                }
                if (current < 0x20 || current > 0x3F) {
                    throw new IllegalArgumentException(
                            "malformed ANSI CSI sequence at UTF-16 index " + index);
                }
                cursor++;
            }
            if (cursor > value.length() || cursor == value.length()
                    && value.charAt(cursor - 1) != 'm') {
                throw new IllegalArgumentException(
                        "unterminated ANSI CSI sequence at UTF-16 index " + index);
            }
            index = cursor;
        }
        return result.toString();
    }
}
