package org.minecraftprot.stackframe.renderer;

import com.ibm.icu.lang.UCharacter;
import com.ibm.icu.lang.UProperty;
import com.ibm.icu.text.BreakIterator;
import com.ibm.icu.text.UnicodeSet;
import com.ibm.icu.util.ULocale;
import java.util.ArrayList;
import java.util.List;

/**
 * ICU-backed Unicode 17.0 terminal-width policy used by layout and golden tests.
 * Unsupported positional cases are marked uncertain for linear fallback.
 */
public final class UnicodeWidthPolicy {
    public static final String VERSION = "unicode-17.0-terminal-width-1";
    public static final int TAB_STOP = 4;
    private static final UnicodeSet BASIC_EMOJI = new UnicodeSet("[:Basic_Emoji:]").freeze();
    private static final UnicodeSet RGI_EMOJI = new UnicodeSet("[:RGI_Emoji:]").freeze();

    private UnicodeWidthPolicy() {
    }

    public static WidthMeasurement measure(String value, AmbiguousWidth ambiguousWidth) {
        var sanitized = sanitize(value, ambiguousWidth, false, 0, false);
        return new WidthMeasurement(sanitized.columns(), sanitized.certain());
    }

    static boolean isGraphemeBoundary(String value, int utf16Offset) {
        var boundaries = BreakIterator.getCharacterInstance(ULocale.ROOT);
        boundaries.setText(value);
        return boundaries.isBoundary(utf16Offset);
    }

    static SanitizedText sanitize(
            String value, AmbiguousWidth ambiguousWidth, boolean excerpt, int initialColumn) {
        return sanitize(value, ambiguousWidth, excerpt, initialColumn, true);
    }

    static SanitizedText sanitize(
            String value,
            AmbiguousWidth ambiguousWidth,
            boolean excerpt,
            int initialColumn,
            boolean escapeTrailingWhitespace) {
        if (value == null || ambiguousWidth == null) {
            throw new IllegalArgumentException("text and ambiguous-width policy must not be null");
        }
        var clusters = new ArrayList<Cluster>();
        var columns = initialColumn;
        var certain = true;
        var boundaries = BreakIterator.getCharacterInstance(ULocale.ROOT);
        boundaries.setText(value);
        var trailingWhitespaceStart = value.length();
        while (trailingWhitespaceStart > 0) {
            var codePoint = value.codePointBefore(trailingWhitespaceStart);
            if (!Character.isWhitespace(codePoint)) {
                break;
            }
            trailingWhitespaceStart -= Character.charCount(codePoint);
        }

        for (int index = boundaries.first(), end = boundaries.next();
                end != BreakIterator.DONE;
                index = end, end = boundaries.next()) {
            var codePoint = value.codePointAt(index);
            if (excerpt && codePoint == '\t') {
                var spaces = TAB_STOP - Math.floorMod(columns, TAB_STOP);
                var escaping = escapeTrailingWhitespace && index >= trailingWhitespaceStart;
                var text = escaping
                        ? "\\u{0020}".repeat(spaces)
                        : " ".repeat(spaces);
                var renderedColumns = escaping ? text.length() : spaces;
                clusters.add(new Cluster(
                        text, renderedColumns, !escaping, true));
                columns += renderedColumns;
                continue;
            }

            var rawCluster = value.substring(index, end);
            var cluster = sanitizeCluster(
                    rawCluster,
                    ambiguousWidth,
                    excerpt,
                    escapeTrailingWhitespace && index >= trailingWhitespaceStart);
            clusters.add(cluster);
            columns += cluster.columns();
            certain &= cluster.certain();
        }

        return new SanitizedText(List.copyOf(clusters), columns - initialColumn, certain);
    }

    private static Cluster sanitizeCluster(
            String cluster,
            AmbiguousWidth ambiguousWidth,
            boolean excerpt,
            boolean escapeTrailingWhitespace) {
        var first = cluster.codePointAt(0);
        if (cluster.codePointCount(0, cluster.length()) == 1 && isForbidden(first)) {
            if (excerpt && first == '\t') {
                throw new IllegalStateException("excerpt tabs must be expanded before sanitizing");
            }
            var escaped = first == '\n' ? "\\n" : first == '\t' ? "\\t" : escape(first);
            return new Cluster(escaped, escaped.length(), false, false);
        }

        var hasJoiner = cluster.indexOf('\u200D') >= 0;
        var emojiSequence = isSupportedEmojiCluster(cluster);
        var validEmojiVariation = hasValidEmojiPresentationSelector(cluster);
        var text = new StringBuilder(cluster.length());
        var certain = true;
        var escaped = false;
        var renderedColumns = 0;
        for (var index = 0; index < cluster.length();) {
            var codePoint = cluster.codePointAt(index);
            var preserveDefaultIgnorable =
                    (codePoint == 0x200D && emojiSequence)
                            || (isEmojiTag(codePoint) && emojiSequence)
                            || (isVariationSelector(codePoint)
                                    && (emojiSequence
                                            || isValidVariationSequence(
                                                    cluster, index, codePoint)));
            if (escapeTrailingWhitespace && Character.isWhitespace(codePoint)) {
                var escape = escape(codePoint);
                text.append(escape);
                renderedColumns += escape.length();
                certain = false;
                escaped = true;
            } else if (isForbidden(codePoint)
                    || isDefaultIgnorable(codePoint) && !preserveDefaultIgnorable) {
                var escape = escape(codePoint);
                text.append(escape);
                renderedColumns += escape.length();
                certain = false;
                escaped = true;
            } else {
                text.appendCodePoint(codePoint);
                renderedColumns += standaloneWidth(codePoint, ambiguousWidth);
            }
            index += Character.charCount(codePoint);
        }

        if ((hasJoiner || hasEmojiModifier(cluster)) && !emojiSequence) {
            certain = false;
        }
        var columns = clusterWidth(
                cluster, ambiguousWidth, emojiSequence, validEmojiVariation);
        if (escaped) {
            columns = renderedColumns;
        }
        if (cluster.codePoints().noneMatch(codePoint ->
                !isCombining(codePoint)
                        && !isVariationSelector(codePoint)
                        && codePoint != 0x200D)) {
            certain = false;
        }
        var breakAfter = Character.isWhitespace(first) || isBreakPunctuation(first);
        return new Cluster(text.toString(), columns, certain, breakAfter);
    }

    private static int clusterWidth(
            String cluster,
            AmbiguousWidth ambiguousWidth,
            boolean emojiSequence,
            boolean validEmojiVariation) {
        if (emojiSequence || validEmojiVariation) {
            return 2;
        }
        var base = cluster.codePoints()
                .filter(codePoint -> !isCombining(codePoint)
                        && !isVariationSelector(codePoint)
                        && codePoint != 0x200D)
                .findFirst();
        if (base.isEmpty()) {
            return 0;
        }
        var codePoint = base.getAsInt();
        var textPresentation = cluster.codePoints().anyMatch(candidate -> candidate == 0xFE0E);
        return switch (UCharacter.getIntPropertyValue(codePoint, UProperty.EAST_ASIAN_WIDTH)) {
            case UCharacter.EastAsianWidth.WIDE, UCharacter.EastAsianWidth.FULLWIDTH -> 2;
            case UCharacter.EastAsianWidth.AMBIGUOUS -> ambiguousWidth.columns();
            default -> !textPresentation && isDefaultEmojiPresentation(codePoint) ? 2 : 1;
        };
    }

    private static boolean isValidVariationSequence(
            String cluster, int selectorOffset, int selector) {
        var base = cluster.codePointAt(0);
        if (selectorOffset != Character.charCount(base)) {
            return false;
        }
        if (selector == 0xFE0E || selector == 0xFE0F) {
            return isEmojiVariationBase(base);
        }
        return selector >= 0xE0100
                && selector <= 0xE01EF
                && UCharacter.hasBinaryProperty(base, UProperty.IDEOGRAPHIC)
                && selectorOffset > 0;
    }

    private static boolean isSupportedEmojiCluster(String cluster) {
        return RGI_EMOJI.contains(cluster);
    }

    private static boolean isEmojiVariationBase(int codePoint) {
        var emojiPresentation = new String(Character.toChars(codePoint)) + "\uFE0F";
        return BASIC_EMOJI.contains(emojiPresentation)
                || codePoint == '#'
                || codePoint == '*'
                || codePoint >= '0' && codePoint <= '9';
    }

    private static boolean hasValidEmojiPresentationSelector(String cluster) {
        for (var index = 0; index < cluster.length();) {
            var codePoint = cluster.codePointAt(index);
            if (codePoint == 0xFE0F
                    && isValidVariationSequence(cluster, index, codePoint)) {
                return true;
            }
            index += Character.charCount(codePoint);
        }
        return false;
    }

    private static boolean hasEmojiModifier(String cluster) {
        return cluster.codePoints()
                .anyMatch(codePoint ->
                        UCharacter.hasBinaryProperty(codePoint, UProperty.EMOJI_MODIFIER));
    }

    private static boolean isEmojiTag(int codePoint) {
        return codePoint >= 0xE0020 && codePoint <= 0xE007F;
    }

    private static boolean isCombining(int codePoint) {
        var type = UCharacter.getType(codePoint);
        return type == UCharacter.NON_SPACING_MARK
                || type == UCharacter.COMBINING_SPACING_MARK
                || type == UCharacter.ENCLOSING_MARK;
    }

    private static boolean isVariationSelector(int codePoint) {
        return codePoint >= 0xFE00 && codePoint <= 0xFE0F
                || codePoint >= 0xE0100 && codePoint <= 0xE01EF;
    }

    private static boolean isDefaultEmojiPresentation(int codePoint) {
        return UCharacter.hasBinaryProperty(codePoint, UProperty.EMOJI_PRESENTATION);
    }

    private static boolean isForbidden(int codePoint) {
        return codePoint <= 0x1F
                || codePoint >= 0x7F && codePoint <= 0x9F
                || codePoint == 0x2028
                || codePoint == 0x2029
                || codePoint >= 0x202A && codePoint <= 0x202E
                || codePoint >= 0x2066 && codePoint <= 0x2069;
    }

    private static boolean isDefaultIgnorable(int codePoint) {
        return UCharacter.hasBinaryProperty(codePoint, UProperty.DEFAULT_IGNORABLE_CODE_POINT);
    }

    private static boolean isBreakPunctuation(int codePoint) {
        return switch (codePoint) {
            case ',', ';', ')' -> true;
            default -> false;
        };
    }

    private static int standaloneWidth(int codePoint, AmbiguousWidth ambiguousWidth) {
        if (isCombining(codePoint) || isVariationSelector(codePoint) || codePoint == 0x200D) {
            return 0;
        }
        return switch (UCharacter.getIntPropertyValue(codePoint, UProperty.EAST_ASIAN_WIDTH)) {
            case UCharacter.EastAsianWidth.WIDE, UCharacter.EastAsianWidth.FULLWIDTH -> 2;
            case UCharacter.EastAsianWidth.AMBIGUOUS -> ambiguousWidth.columns();
            default -> isDefaultEmojiPresentation(codePoint) ? 2 : 1;
        };
    }

    private static String escape(int codePoint) {
        return "\\u{" + String.format("%04X", codePoint) + "}";
    }

    public record WidthMeasurement(int columns, boolean certain) {
        public WidthMeasurement {
            if (columns < 0) {
                throw new IllegalArgumentException("columns must be non-negative");
            }
        }
    }

    record Cluster(String text, int columns, boolean certain, boolean breakAfter) {
    }

    record SanitizedText(List<Cluster> clusters, int columns, boolean certain) {
        String value() {
            var value = new StringBuilder();
            for (var cluster : clusters) {
                value.append(cluster.text());
            }
            return value.toString();
        }
    }
}
