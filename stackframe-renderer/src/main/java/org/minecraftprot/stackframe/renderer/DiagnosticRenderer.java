package org.minecraftprot.stackframe.renderer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.minecraftprot.stackframe.diagnostic.Diagnostic;
import org.minecraftprot.stackframe.diagnostic.DiagnosticDocument;
import org.minecraftprot.stackframe.diagnostic.Excerpt;
import org.minecraftprot.stackframe.diagnostic.ExcerptLine;
import org.minecraftprot.stackframe.diagnostic.Label;
import org.minecraftprot.stackframe.diagnostic.Location;
import org.minecraftprot.stackframe.diagnostic.Omission;
import org.minecraftprot.stackframe.diagnostic.RedactionNotice;
import org.minecraftprot.stackframe.diagnostic.RelatedDiagnostic;
import org.minecraftprot.stackframe.diagnostic.TraceState;

/** Deterministic, bounded renderer for completed diagnostic documents. */
public final class DiagnosticRenderer {
    private static final String ANSI_EMPHASIS = "\u001B[1m";
    private static final String ANSI_RESET = "\u001B[0m";
    private static final int MAX_POSITIONAL_LABELS = 8;

    private DiagnosticRenderer() {
    }

    public static void render(
            DiagnosticDocument document, Appendable destination, RenderOptions options)
            throws IOException {
        if (document == null || destination == null || options == null) {
            throw new IllegalArgumentException("document, destination, and options must not be null");
        }
        var renderer = new Engine(new BoundedOutput(destination, options.limits()), options);
        renderer.render(document);
    }

    public static String renderToString(DiagnosticDocument document, RenderOptions options) {
        var output = new StringBuilder();
        try {
            render(document, output, options);
        } catch (IOException exception) {
            throw new IllegalStateException("StringBuilder unexpectedly rejected renderer output", exception);
        }
        return output.toString();
    }

    private static final class Engine {
        private final BoundedOutput output;
        private final RenderOptions options;
        private final int width;

        private Engine(BoundedOutput output, RenderOptions options) {
            this.output = output;
            this.options = options;
            this.width = options.width().targetColumns();
        }

        private void render(DiagnosticDocument document) throws IOException {
            renderDiagnostic(document.root(), "", false);
            for (var redaction : document.redactions().items()) {
                writeRedaction(redaction);
            }
            for (var omission : document.omissions().items()) {
                writeOmission("", omission);
            }
            writeTrace(
                    "",
                    document.root(),
                    document.diagnosticId().value(),
                    document.correlationId().value());
        }

        private void renderDiagnostic(Diagnostic diagnostic, String indent, boolean includeTrace)
                throws IOException {
            writeHeader(diagnostic, indent);
            for (var location : diagnostic.locations().items()) {
                writeLocation(location, indent);
            }
            for (var note : diagnostic.notes().items()) {
                var label = switch (note.kind()) {
                    case CAUSE -> "cause";
                    case CONTEXT -> "context";
                    case NOTE -> "note";
                };
                writeField(indent, label, note.text().value(), false);
            }
            for (var evidence : diagnostic.evidence().items()) {
                var text = evidence.summary().value();
                if (evidence.source().isPresent()) {
                    text += " (source: " + evidence.source().orElseThrow().value() + ")";
                }
                writeField(indent, "evidence", text, false);
            }
            for (var related : diagnostic.children().items()) {
                writeRelation(indent, related);
            }
            for (var help : diagnostic.help().items()) {
                writeField(indent, "help", help.text().value(), false);
            }
            for (var omission : diagnostic.omissions().items()) {
                writeOmission(indent, omission);
            }
            if (includeTrace) {
                writeTrace(indent, diagnostic, null, null);
            }
        }

        private void writeHeader(Diagnostic diagnostic, String indent) throws IOException {
            var severity = diagnostic.severity().name().toLowerCase(Locale.ROOT);
            var prefix = indent + severity + "[" + diagnostic.code().value() + "]: ";
            writeWrapped(prefix, indent + "  ", diagnostic.title().value(), false, true);
        }

        private void writeLocation(Location location, String indent) throws IOException {
            var display = location.display().value();
            if (location.position().isPresent()) {
                var range = location.position().orElseThrow();
                display += ":" + range.start().line() + ":" + range.start().column()
                        + "-" + range.end().line() + ":" + range.end().column();
            }
            writeField(indent, "location", display, true);
            if (location.excerpt().isPresent()) {
                writeExcerpt(indent, location.excerpt().orElseThrow());
            }
        }

        private void writeExcerpt(String indent, Excerpt excerpt) throws IOException {
            if (excerpt.lines().items().isEmpty()) {
                return;
            }
            var positional = excerpt.labels().items().size() <= MAX_POSITIONAL_LABELS;
            var renderedAsGutter = new ArrayList<Integer>();

            for (var line : excerpt.lines().items()) {
                var text = sanitize(line.text().value(), true, 0);
                var gutter = indent + "context line " + line.lineNumber() + ": ";
                if (positional && text.certain()
                        && !hasTrailingWhitespace(text.value())
                        && columns(gutter) + text.columns() <= width) {
                    writeLine(text.value().isBlank() ? gutter.stripTrailing() : gutter + text.value(), false);
                    renderedAsGutter.add(line.lineNumber());
                } else {
                    writeExcerptField(indent, line.lineNumber(), text.value());
                }
            }

            for (var label : excerpt.labels().items()) {
                if (!writeCaretIfSafe(indent, excerpt, renderedAsGutter, label)) {
                    writeLinearAnnotation(indent, label);
                }
            }
        }

        private boolean writeCaretIfSafe(
                String indent,
                Excerpt excerpt,
                List<Integer> renderedAsGutter,
                Label label) throws IOException {
            var range = label.range();
            if (range.start().line() != range.end().line()
                    || !renderedAsGutter.contains(range.start().line())) {
                return false;
            }
            var line = excerpt.lines().items().get(range.start().line() - excerpt.startLine());
            var raw = line.text().value();
            var startOffset = raw.offsetByCodePoints(0, range.start().column() - 1);
            var endOffset = raw.offsetByCodePoints(0, range.end().column() - 1);
            if (!UnicodeWidthPolicy.isGraphemeBoundary(raw, startOffset)
                    || !UnicodeWidthPolicy.isGraphemeBoundary(raw, endOffset)) {
                return false;
            }
            var prefix = UnicodeWidthPolicy.sanitize(
                    raw.substring(0, startOffset),
                    options.ambiguousWidth(),
                    true,
                    0,
                    false);
            var span = UnicodeWidthPolicy.sanitize(
                    raw.substring(startOffset, endOffset),
                    options.ambiguousWidth(),
                    true,
                    prefix.columns(),
                    false);
            if (!prefix.certain() || !span.certain()) {
                return false;
            }
            var labelKind = label.style().name().toLowerCase(Locale.ROOT);
            var message = sanitize(label.message().value(), false, 0);
            var caretColumns = Math.max(1, span.columns());
            var contextPrefix = indent + "context line " + range.start().line() + ": ";
            var annotation = "annotation line " + range.start().line()
                    + " columns " + range.start().column() + "-" + range.end().column()
                    + " " + labelKind + ": ";
            var linePrefix = " ".repeat(columns(contextPrefix) + prefix.columns());
            var rendered = linePrefix + "^".repeat(caretColumns) + " "
                    + annotation + message.value();
            if (columns(rendered) > width) {
                return false;
            }
            writeLine(rendered, true);
            return true;
        }

        private void writeLinearAnnotation(String indent, Label label) throws IOException {
            var range = label.range();
            var location = range.start().line() == range.end().line()
                    ? "line " + range.start().line() + " columns "
                            + range.start().column() + "-" + range.end().column()
                    : "lines " + range.start().line() + ":" + range.start().column()
                            + "-" + range.end().line() + ":" + range.end().column();
            var kind = label.style().name().toLowerCase(Locale.ROOT);
            writeField(indent, "annotation " + location + " " + kind,
                    label.message().value(), false);
        }

        private void writeExcerptField(String indent, int lineNumber, String value)
                throws IOException {
            writeField(indent, "context line " + lineNumber, value, false);
        }

        private void writeRelation(String indent, RelatedDiagnostic related) throws IOException {
            var relation = related.relation().name().toLowerCase(Locale.ROOT).replace('_', ' ');
            writeField(indent, "relation", relation, true);
            renderDiagnostic(related.diagnostic(), indent + "  ", true);
        }

        private void writeOmission(String indent, Omission omission) throws IOException {
            var count = omission.omittedCount();
            var reason = omission.reason().name().toLowerCase(Locale.ROOT).replace('_', ' ');
            var text = count + " " + plural(count, "value", "values")
                    + " omitted from " + omission.affectedPath().value()
                    + " (reason: " + reason + ")";
            writeField(indent, "omission", text, false);
        }

        private void writeRedaction(RedactionNotice notice) throws IOException {
            var transformation = notice.transformation().name()
                    .toLowerCase(Locale.ROOT)
                    .replace('_', ' ');
            var count = notice.occurrenceCount();
            var text = count + " " + plural(count, "value", "values") + " "
                    + transformation + " as "
                    + notice.marker().category().toLowerCase(Locale.ROOT);
            writeField("", "redaction", text, false);
        }

        private void writeTrace(
                String indent,
                Diagnostic diagnostic,
                String diagnosticId,
                String correlationId) throws IOException {
            var trace = diagnostic.trace();
            var parts = new ArrayList<String>();
            if (trace.state() == TraceState.NOT_APPLICABLE) {
                parts.add("no originating trace");
            } else if (trace.state() == TraceState.WRITE_FAILED) {
                parts.add("complete details could not be preserved");
                addTraceCounts(parts, trace.shownFrames(), trace.omittedFrames(), trace.omittedCauses());
            } else {
                addTraceCounts(parts, trace.shownFrames(), trace.omittedFrames(), trace.omittedCauses());
                var preserved = new StringBuilder("complete details preserved");
                trace.destination().ifPresent(destination ->
                        preserved.append(" at ").append(destination.value()));
                trace.recordId().ifPresent(record ->
                        preserved.append(" as diagnostic ").append(record.value()));
                parts.add(preserved.toString());
            }
            if (diagnosticId != null) {
                parts.add("diagnostic " + diagnosticId);
            }
            if (correlationId != null) {
                parts.add("correlation " + correlationId);
            }
            writeField(indent, "trace", String.join("; ", parts), false);
        }

        private void addTraceCounts(
                List<String> parts, int shownFrames, int omittedFrames, int omittedCauses) {
            if (shownFrames > 0) {
                parts.add(shownFrames + " " + plural(shownFrames, "frame", "frames") + " shown");
            }
            if (omittedFrames > 0) {
                parts.add(omittedFrames + " " + plural(omittedFrames, "frame", "frames")
                        + " collapsed");
            }
            if (omittedCauses > 0) {
                parts.add(omittedCauses + " " + plural(omittedCauses, "cause", "causes")
                        + " omitted");
            }
        }

        private void writeField(String indent, String label, String value, boolean atomic)
                throws IOException {
            var prefix = indent + label + ": ";
            var continuation = indent + "  ";
            var sanitized = sanitize(value, false, 0);
            if (sanitized.value().isBlank()) {
                writeLine(prefix.stripTrailing(), true);
                return;
            }
            if (width < 80 && (columns(prefix) + sanitized.columns() > width)) {
                writeLine(prefix.substring(0, prefix.length() - 1), true);
                writeWrapped(continuation, continuation, value, atomic, false);
                return;
            }
            writeWrapped(prefix, continuation, value, atomic, true);
        }

        private void writeWrapped(
                String firstPrefix,
                String continuationPrefix,
                String value,
                boolean atomic,
                boolean emphasizeFirst) throws IOException {
            var sanitized = sanitize(value, false, 0);
            var firstAvailable = Math.max(1, width - columns(firstPrefix));
            var continuationAvailable = Math.max(1, width - columns(continuationPrefix));
            var lines = atomic
                    ? List.of(sanitized.value())
                    : wrap(sanitized.clusters(), firstAvailable, continuationAvailable);
            for (var index = 0; index < lines.size(); index++) {
                var prefix = index == 0 ? firstPrefix : continuationPrefix;
                var content = lines.get(index);
                writeLine(
                        content.isBlank() ? prefix.stripTrailing() : prefix + content,
                        emphasizeFirst && index == 0);
            }
        }

        private List<String> wrap(
                List<UnicodeWidthPolicy.Cluster> clusters,
                int firstAvailable,
                int continuationAvailable) {
            output.consumeWork(clusters.size());
            var words = words(clusters);
            if (words.isEmpty()) {
                return List.of("");
            }
            var lines = new ArrayList<String>();
            var current = new StringBuilder();
            var currentWidth = 0;
            var available = firstAvailable;

            for (var word : words) {
                var wordText = word.text();
                var wordWidth = word.columns();
                if (currentWidth > 0 && currentWidth + wordWidth > available) {
                    lines.add(stripTrailingWhitespace(current.toString()));
                    current.setLength(0);
                    currentWidth = 0;
                    available = continuationAvailable;
                    wordText = stripLeadingWhitespace(wordText);
                    wordWidth = columns(wordText);
                }
                current.append(wordText);
                currentWidth += wordWidth;
            }
            lines.add(stripTrailingWhitespace(current.toString()));
            return List.copyOf(lines);
        }

        private List<Word> words(List<UnicodeWidthPolicy.Cluster> clusters) {
            var words = new ArrayList<Word>();
            var token = new ArrayList<UnicodeWidthPolicy.Cluster>();
            var quote = 0;
            for (var index = 0; index < clusters.size(); index++) {
                var cluster = clusters.get(index);
                var clusterCodePoint = cluster.text().codePointCount(0, cluster.text().length()) == 1
                        ? cluster.text().codePointAt(0)
                        : -1;
                if (clusterCodePoint == '"' || clusterCodePoint == '`'
                        || clusterCodePoint == '\''
                                && isSingleQuoteDelimiter(clusters, index, quote)) {
                    quote = quote == 0 ? clusterCodePoint : quote == clusterCodePoint ? 0 : quote;
                }
                token.add(cluster);
                if (quote == 0 && cluster.text().codePoints().allMatch(Character::isWhitespace)) {
                    appendToken(words, token);
                    token.clear();
                }
            }
            if (!token.isEmpty()) {
                appendToken(words, token);
            }
            return words;
        }

        private boolean isSingleQuoteDelimiter(
                List<UnicodeWidthPolicy.Cluster> clusters, int index, int quote) {
            if (quote == '\'') {
                return index + 1 == clusters.size()
                        || isQuoteBoundary(clusters.get(index + 1));
            }
            if (quote != 0
                    || index > 0 && !isQuoteBoundary(clusters.get(index - 1))) {
                return false;
            }
            for (var candidate = index + 1; candidate < clusters.size(); candidate++) {
                if (clusters.get(candidate).text().equals("'")
                        && (candidate + 1 == clusters.size()
                                || isQuoteBoundary(clusters.get(candidate + 1)))) {
                    return true;
                }
            }
            return false;
        }

        private boolean isQuoteBoundary(UnicodeWidthPolicy.Cluster cluster) {
            return cluster.text().codePoints().allMatch(Character::isWhitespace)
                    || cluster.text().equals(",")
                    || cluster.text().equals(";")
                    || cluster.text().equals(":")
                    || cluster.text().equals(".")
                    || cluster.text().equals("=")
                    || cluster.text().equals("(")
                    || cluster.text().equals(")")
                    || cluster.text().equals("[")
                    || cluster.text().equals("]")
                    || cluster.text().equals("{")
                    || cluster.text().equals("}");
        }

        private void appendToken(
                List<Word> words, List<UnicodeWidthPolicy.Cluster> token) {
            var tokenText = new StringBuilder();
            for (var cluster : token) {
                tokenText.append(cluster.text());
            }
            var protectedToken = tokenText.indexOf("/") >= 0
                    || tokenText.indexOf("\\") >= 0
                    || tokenText.indexOf("\"") >= 0
                    || tokenText.indexOf("'") >= 0
                    || tokenText.indexOf("`") >= 0
                    || tokenText.indexOf("-") >= 0;
            var current = new StringBuilder();
            var currentWidth = 0;
            for (var cluster : token) {
                current.append(cluster.text());
                currentWidth += cluster.columns();
                if (!protectedToken && cluster.breakAfter()
                        && !cluster.text().codePoints().allMatch(Character::isWhitespace)) {
                    words.add(new Word(current.toString(), currentWidth));
                    current.setLength(0);
                    currentWidth = 0;
                }
            }
            if (!current.isEmpty()) {
                words.add(new Word(current.toString(), currentWidth));
            }
        }

        private UnicodeWidthPolicy.SanitizedText sanitize(
                String value, boolean excerpt, int initialColumn) {
            output.consumeWork(value.codePointCount(0, value.length()));
            return UnicodeWidthPolicy.sanitize(
                    value, options.ambiguousWidth(), excerpt, initialColumn);
        }

        private int columns(String value) {
            return UnicodeWidthPolicy.measure(value, options.ambiguousWidth()).columns();
        }

        private void writeLine(String value, boolean emphasize) throws IOException {
            if (!value.isEmpty() && Character.isWhitespace(value.charAt(value.length() - 1))) {
                throw new IllegalStateException("renderer attempted to emit trailing whitespace");
            }
            var line = new StringBuilder(value.length() + 9);
            if (options.outputMode() == OutputMode.ANSI && emphasize && !value.isEmpty()) {
                line.append(ANSI_EMPHASIS).append(value).append(ANSI_RESET);
            } else {
                line.append(value);
            }
            line.append('\n');
            output.append(line);
        }

        private static String plural(int count, String singular, String plural) {
            return count == 1 ? singular : plural;
        }

        private static boolean hasTrailingWhitespace(String value) {
            return !value.isEmpty() && Character.isWhitespace(value.codePointBefore(value.length()));
        }

        private static String stripLeadingWhitespace(String value) {
            var index = 0;
            while (index < value.length()) {
                var codePoint = value.codePointAt(index);
                if (!Character.isWhitespace(codePoint)) {
                    break;
                }
                index += Character.charCount(codePoint);
            }
            return value.substring(index);
        }

        private static String stripTrailingWhitespace(String value) {
            var index = value.length();
            while (index > 0) {
                var codePoint = value.codePointBefore(index);
                if (!Character.isWhitespace(codePoint)) {
                    break;
                }
                index -= Character.charCount(codePoint);
            }
            return value.substring(0, index);
        }
    }

    private record Word(String text, int columns) {
    }

    private static final class BoundedOutput {
        private final Appendable destination;
        private final RenderLimits limits;
        private long bytes;
        private int lines;
        private long work;

        private BoundedOutput(Appendable destination, RenderLimits limits) {
            this.destination = destination;
            this.limits = limits;
        }

        private void append(CharSequence value) throws IOException {
            var additionalBytes = utf8Bytes(value);
            var additionalLines = countLines(value);
            if (additionalBytes > limits.maxUtf8Bytes() - bytes) {
                throw new RenderLimitException(
                        "renderer UTF-8 output limit exceeded: " + limits.maxUtf8Bytes());
            }
            if ((long) lines + additionalLines > limits.maxLines()) {
                throw new RenderLimitException(
                        "renderer logical line limit exceeded: " + limits.maxLines());
            }
            bytes += additionalBytes;
            lines += additionalLines;
            destination.append(value);
        }

        private void consumeWork(long units) {
            if (units < 0 || units > limits.maxWorkUnits() - work) {
                throw new RenderLimitException(
                        "renderer work limit exceeded: " + limits.maxWorkUnits());
            }
            work += units;
        }

        private static long utf8Bytes(CharSequence value) {
            long bytes = 0;
            for (var index = 0; index < value.length();) {
                var codePoint = Character.codePointAt(value, index);
                bytes += codePoint <= 0x7F ? 1
                        : codePoint <= 0x7FF ? 2
                        : codePoint <= 0xFFFF ? 3 : 4;
                index += Character.charCount(codePoint);
            }
            return bytes;
        }

        private static int countLines(CharSequence value) {
            var lines = 0;
            for (var index = 0; index < value.length(); index++) {
                if (value.charAt(index) == '\n') {
                    lines++;
                }
            }
            return lines;
        }
    }
}
