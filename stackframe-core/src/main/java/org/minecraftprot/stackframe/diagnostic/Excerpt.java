package org.minecraftprot.stackframe.diagnostic;

/**
 * Ordered bounded post-redaction source context. Absolute line numbers increase
 * by exactly one from {@code startLine}; all label coordinates are checked
 * against final display text.
 */
public record Excerpt(
        int startLine,
        BoundedList<ExcerptLine> lines,
        BoundedList<Label> labels) {

    public Excerpt {
        Validation.positive(startLine, "$.excerpt.startLine");
        lines = Validation.required(lines, "$.excerpt.lines");
        labels = Validation.required(labels, "$.excerpt.labels");
        Validation.size(lines.items(), ModelLimits.EXCERPT_LINES, "$.excerpt.lines");
        Validation.size(labels.items(), ModelLimits.LABELS_PER_EXCERPT, "$.excerpt.labels");

        var expectedLine = startLine;
        for (var index = 0; index < lines.items().size(); index++) {
            var line = lines.items().get(index);
            if (line.lineNumber() != expectedLine) {
                throw new RangeValidationException(
                        "$.excerpt.lines.items[" + index + "].lineNumber",
                        "must equal " + expectedLine + " for contiguous absolute line ordering");
            }
            if (expectedLine == Integer.MAX_VALUE && index + 1 < lines.items().size()) {
                throw new RangeValidationException("$.excerpt.lines", "line number sequence overflows");
            }
            expectedLine++;
        }

        for (var index = 0; index < labels.items().size(); index++) {
            validateLabel(labels.items().get(index), lines.items(), index);
        }
    }

    private static void validateLabel(Label label, java.util.List<ExcerptLine> lines, int index) {
        var path = "$.excerpt.labels.items[" + index + "].range";
        if (lines.isEmpty()) {
            throw new RangeValidationException(path, "cannot resolve against an empty excerpt");
        }
        validatePosition(label.range().start(), lines, path + ".start");
        validatePosition(label.range().end(), lines, path + ".end");
    }

    private static void validatePosition(
            SourcePosition position, java.util.List<ExcerptLine> lines, String path) {
        var firstLine = lines.getFirst().lineNumber();
        var lastLine = lines.getLast().lineNumber();
        if (position.line() < firstLine || position.line() > lastLine) {
            throw new RangeValidationException(path, "line falls outside the retained excerpt");
        }
        var line = lines.get(position.line() - firstLine);
        var maximumColumn = line.text().value().codePointCount(0, line.text().value().length()) + 1;
        if (position.column() > maximumColumn) {
            throw new RangeValidationException(
                    path, "column exceeds the final post-redaction line length plus one");
        }
    }
}
