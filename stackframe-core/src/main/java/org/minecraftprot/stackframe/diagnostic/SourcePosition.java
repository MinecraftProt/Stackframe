package org.minecraftprot.stackframe.diagnostic;

/** One-based line and Unicode-code-point column in logical source text. */
public record SourcePosition(int line, int column) implements Comparable<SourcePosition> {
    public SourcePosition {
        Validation.positive(line, "$.sourcePosition.line");
        Validation.positive(column, "$.sourcePosition.column");
    }

    @Override
    public int compareTo(SourcePosition other) {
        Validation.required(other, "$.sourcePosition");
        var lineComparison = Integer.compare(line, other.line);
        return lineComparison != 0 ? lineComparison : Integer.compare(column, other.column);
    }
}
