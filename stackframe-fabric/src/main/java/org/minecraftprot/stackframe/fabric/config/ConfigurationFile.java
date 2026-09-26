package org.minecraftprot.stackframe.fabric.config;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.minecraftprot.stackframe.correlation.DiagnosticCorrelator;
import org.minecraftprot.stackframe.trace.TraceRetention;

/** Strict, location-aware parser for config/stackframe.properties schema 1. */
public final class ConfigurationFile {
    public static final Path RELATIVE_PATH = Path.of("config", "stackframe.properties");
    private static final int MAX_BYTES = 32_768;
    private static final int MAX_LINES = 512;
    private static final int MAX_LINE_LENGTH = 512;
    private static final Pattern KEY = Pattern.compile("[a-z][a-z0-9_]*");
    private static final Pattern SAFE_RELATIVE_PATH = Pattern.compile("[A-Za-z0-9._/ -]{1,160}");
    private static final Set<String> KEYS = Set.of(
            "schema_version", "output", "trace_directory", "trace_retention",
            "trace_max_age_days", "trace_max_files", "include_codes", "exclude_codes",
            "dedup_window_ms", "dedup_max_entries");

    private ConfigurationFile() {
    }

    /** Missing file uses documented defaults; invalid/unreadable files never do. */
    public static StackframeConfiguration load(Path serverDirectory)
            throws ConfigurationProblem {
        if (serverDirectory == null) {
            throw new IllegalArgumentException("serverDirectory must not be null");
        }
        var root = serverDirectory.toAbsolutePath().normalize();
        var configDirectory = root.resolve("config");
        var file = root.resolve(RELATIVE_PATH);
        if (Files.isSymbolicLink(configDirectory) || Files.isSymbolicLink(file)) {
            throw new ConfigurationProblem(0, "file", "configuration path must not be a link");
        }
        final BasicFileAttributes attributes;
        try {
            attributes = Files.readAttributes(file, BasicFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS);
        } catch (java.nio.file.NoSuchFileException absent) {
            return StackframeConfiguration.DEFAULT;
        } catch (IOException | SecurityException unreadable) {
            throw new ConfigurationProblem(0, "file", "configuration must be readable");
        }
        if (!attributes.isRegularFile() || attributes.size() > MAX_BYTES) {
            throw new ConfigurationProblem(0, "file",
                    "configuration must be a regular UTF-8 file of at most 32768 bytes");
        }
        final byte[] bytes;
        try (var stream = Files.newInputStream(file, StandardOpenOption.READ,
                LinkOption.NOFOLLOW_LINKS)) {
            bytes = stream.readNBytes(MAX_BYTES + 1);
        } catch (IOException | SecurityException unreadable) {
            throw new ConfigurationProblem(0, "file", "configuration must be readable");
        }
        if (bytes.length > MAX_BYTES) {
            throw new ConfigurationProblem(0, "file",
                    "configuration must be at most 32768 bytes");
        }
        try {
            var decoder = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
            return parse(decoder.decode(ByteBuffer.wrap(bytes)).toString());
        } catch (CharacterCodingException malformed) {
            throw new ConfigurationProblem(0, "file", "configuration must be valid UTF-8");
        }
    }

    static StackframeConfiguration parse(String contents) throws ConfigurationProblem {
        if (contents == null) {
            throw new IllegalArgumentException("contents must not be null");
        }
        var lines = contents.split("\\n", -1);
        if (lines.length > MAX_LINES) {
            throw new ConfigurationProblem(MAX_LINES + 1, "file",
                    "configuration must have at most 512 lines");
        }
        Map<String, Entry> entries = new LinkedHashMap<>();
        for (var index = 0; index < lines.length; index++) {
            var lineNumber = index + 1;
            var line = lines[index];
            if (line.endsWith("\r")) {
                line = line.substring(0, line.length() - 1);
            }
            if (line.length() > MAX_LINE_LENGTH) {
                throw new ConfigurationProblem(lineNumber, "file",
                        "line must have at most 512 characters");
            }
            var trimmed = line.strip();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            var equals = trimmed.indexOf('=');
            if (equals <= 0 || equals != trimmed.lastIndexOf('=')) {
                throw new ConfigurationProblem(lineNumber, "line",
                        "expected one key=value assignment");
            }
            var key = trimmed.substring(0, equals).strip();
            var value = trimmed.substring(equals + 1).strip();
            if (!KEY.matcher(key).matches()) {
                throw new ConfigurationProblem(lineNumber, "key",
                        "expected lowercase letters, digits, and underscores");
            }
            if (entries.isEmpty() && !key.equals("schema_version")) {
                throw new ConfigurationProblem(lineNumber, key,
                        "schema_version must be the first setting");
            }
            if (entries.putIfAbsent(key, new Entry(value, lineNumber)) != null) {
                throw new ConfigurationProblem(lineNumber, key,
                        "duplicate setting; each key may appear once");
            }
        }
        var schema = entries.get("schema_version");
        if (schema == null) {
            throw new ConfigurationProblem(1, "schema_version",
                    "required first setting; expected schema_version=1");
        }
        if (!schema.value().equals("1")) {
            throw new ConfigurationProblem(schema.line(), "schema_version",
                    schema.value().equals("0")
                            ? "schema 0 has no released automatic migration; create a version 1 file"
                            : "unsupported schema; expected 1 (upgrade Stackframe for a future version)");
        }
        for (var entry : entries.entrySet()) {
            if (!KEYS.contains(entry.getKey())) {
                throw new ConfigurationProblem(entry.getValue().line(), entry.getKey(),
                        "unknown setting for schema 1; remove it or use a compatible Stackframe version");
            }
        }

        var output = parseOutput(entries);
        var directory = parseDirectory(entries);
        var retention = parseRetention(entries);
        var include = parseFilter(entries, "include_codes", "SF0001");
        var exclude = parseFilter(entries, "exclude_codes", "none");
        if (!include && exclude) {
            throw problem(entries, "exclude_codes",
                    "exclude_codes=SF0001 has no effect when include_codes=none");
        }
        var window = parseLong(entries, "dedup_window_ms", 30_000, 0, 300_000);
        var maximum = (int) parseLong(entries, "dedup_max_entries", 256, 1, 4_096);
        if (window == 0 && entries.containsKey("dedup_max_entries")) {
            throw problem(entries, "dedup_max_entries",
                    "has no effect when dedup_window_ms=0; remove this setting");
        }
        var deduplication = new DiagnosticCorrelator.Config(
                window > 0, Duration.ofMillis(window > 0 ? window : 1), maximum);
        return new StackframeConfiguration(output, directory, retention,
                deduplication, include, exclude);
    }

    private static StackframeConfiguration.Output parseOutput(Map<String, Entry> entries)
            throws ConfigurationProblem {
        var value = value(entries, "output", "plain");
        return switch (value) {
            case "auto" -> StackframeConfiguration.Output.AUTO;
            case "ansi" -> StackframeConfiguration.Output.ANSI;
            case "plain" -> StackframeConfiguration.Output.PLAIN;
            default -> throw problem(entries, "output",
                    "expected auto, ansi, or plain; JSON needs a dedicated Fabric sink");
        };
    }

    private static Path parseDirectory(Map<String, Entry> entries) throws ConfigurationProblem {
        var value = value(entries, "trace_directory", "logs/stackframe-traces");
        if (!SAFE_RELATIVE_PATH.matcher(value).matches() || value.startsWith("/")
                || value.contains(":") || value.contains("\\")) {
            throw problem(entries, "trace_directory",
                    "expected a relative server path with no drive, backslash, or control character");
        }
        var raw = Path.of(value);
        for (var part : raw) {
            if (part.toString().equals("..")) {
                throw problem(entries, "trace_directory",
                        "expected a relative path inside the server directory");
            }
        }
        var path = raw.normalize();
        if (path.isAbsolute() || path.startsWith("..") || path.toString().equals(".")) {
            throw problem(entries, "trace_directory",
                    "expected a relative path inside the server directory");
        }
        return path;
    }

    private static TraceRetention.Policy parseRetention(Map<String, Entry> entries)
            throws ConfigurationProblem {
        var mode = value(entries, "trace_retention", "manual");
        if (mode.equals("manual")) {
            for (var key : new String[] {"trace_max_age_days", "trace_max_files"}) {
                if (entries.containsKey(key)) {
                    throw problem(entries, key,
                            "requires trace_retention=bounded; manual mode never deletes files");
                }
            }
            return TraceRetention.Policy.manual();
        }
        if (!mode.equals("bounded")) {
            throw problem(entries, "trace_retention", "expected manual or bounded");
        }
        if (!entries.containsKey("trace_max_age_days") || !entries.containsKey("trace_max_files")) {
            throw problem(entries, "trace_retention",
                    "bounded mode requires trace_max_age_days and trace_max_files");
        }
        var days = parseLong(entries, "trace_max_age_days", 0, 1, 365);
        var files = (int) parseLong(entries, "trace_max_files", 0, 1, 8_192);
        return TraceRetention.Policy.bounded(Duration.ofDays(days), files);
    }

    private static boolean parseFilter(Map<String, Entry> entries, String key, String fallback)
            throws ConfigurationProblem {
        var value = value(entries, key, fallback);
        if (value.equals("SF0001")) {
            return true;
        }
        if (value.equals("none")) {
            return false;
        }
        throw problem(entries, key,
                "expected SF0001 or none; only SF0001 is emitted by this Fabric pipeline");
    }

    private static long parseLong(Map<String, Entry> entries, String key,
            long fallback, long minimum, long maximum) throws ConfigurationProblem {
        var text = value(entries, key, Long.toString(fallback));
        if (!text.matches("[0-9]{1,9}")) {
            throw problem(entries, key, "expected a decimal integer from "
                    + minimum + " to " + maximum);
        }
        var value = Long.parseLong(text);
        if (value < minimum || value > maximum) {
            throw problem(entries, key, "expected a decimal integer from "
                    + minimum + " to " + maximum);
        }
        return value;
    }

    private static String value(Map<String, Entry> entries, String key, String fallback) {
        var entry = entries.get(key);
        return entry == null ? fallback : entry.value();
    }

    private static ConfigurationProblem problem(
            Map<String, Entry> entries, String key, String expectation) {
        var entry = entries.get(key);
        return new ConfigurationProblem(entry == null ? 0 : entry.line(), key, expectation);
    }

    private record Entry(String value, int line) {
    }
}
