package org.minecraftprot.stackframe.redaction;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.minecraftprot.stackframe.diagnostic.CandidateText;
import org.minecraftprot.stackframe.diagnostic.DisplayText;
import org.minecraftprot.stackframe.diagnostic.Sensitivity;
import org.minecraftprot.stackframe.diagnostic.TextOrigin;

/**
 * Conservative conversion of bounded candidate text into renderer-safe text.
 * An unverified message, label, or excerpt is omitted as a whole: a detector
 * cannot prove that an arbitrary string contains no private information.
 */
public final class RedactionPolicy {
    private static final Pattern CREDENTIAL = Pattern.compile(
            "(?i)(?:\\b(?:bearer|basic)\\s+[^\\s,;]+|\\b(?:password|passwd|pwd|token|"
                    + "api[_-]?key|secret|session[_-]?id|access[_-]?key)\\s*[:=]\\s*[^\\s,;]+)");
    private static final Pattern PRIVATE_KEY = Pattern.compile(
            "(?i)-----BEGIN [A-Z0-9 ]*PRIVATE KEY-----");
    private static final Pattern STANDALONE_TOKEN = Pattern.compile(
            "(?i)(?:\\b(?:gh[psuor]_[A-Za-z0-9_]{20,}|github_pat_[A-Za-z0-9_]{20,}|"
                    + "glpat-[A-Za-z0-9_-]{16,}|AKIA[0-9A-Z]{16}|xox[baprs]-[A-Za-z0-9-]{16,})\\b"
                    + "|\\beyJ[A-Za-z0-9_-]+\\.eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\b)");
    private static final Pattern UUID = Pattern.compile(
            "(?i)\\b[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}\\b");
    private static final Pattern ADDRESS = Pattern.compile(
            "(?i)(?:\\b(?:https?|wss?)://[^\\s]+|\\b(?:[0-9]{1,3}\\.){3}[0-9]{1,3}(?::[0-9]{1,5})?\\b"
                    + "|\\[[0-9a-f:]+](?::[0-9]{1,5})?"
                    + "|\\b[a-z0-9][a-z0-9.-]*\\.[a-z]{2,}:[0-9]{1,5}\\b)");
    private static final Pattern EMAIL = Pattern.compile(
            "(?i)\\b[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}\\b");
    private static final Pattern ABSOLUTE_PATH = Pattern.compile(
            "(?i)(?:\\b[A-Z]:[\\\\/][^\\s]+|(?<![A-Za-z0-9])/(?:home|users|root|etc|var|opt)/[^\\s]+)");
    private static final Pattern IDENTIFIER = Pattern.compile("[a-z0-9][a-z0-9_.:-]{0,127}");

    public enum FieldKind {
        VERIFIED_PUBLIC,
        VERIFIED_IDENTIFIER,
        UNTRUSTED_MESSAGE,
        LABEL,
        EXCERPT,
        PATH,
        WORLD_DATA
    }

    public enum PathMode {
        REDACT_ALL,
        RELATIVE_WITHIN_ROOT
    }

    private final Path approvedRoot;
    private final List<String> protectedIdentifiers;
    private final PathMode pathMode;

    /** The approved root must exist; paths are redacted unless relative display is opted into. */
    public RedactionPolicy(Path approvedRoot, Set<String> protectedIdentifiers) throws IOException {
        this(approvedRoot, protectedIdentifiers, PathMode.REDACT_ALL);
    }

    public RedactionPolicy(Path approvedRoot, Set<String> protectedIdentifiers, PathMode pathMode)
            throws IOException {
        this.approvedRoot = Objects.requireNonNull(approvedRoot, "approvedRoot").toRealPath();
        this.pathMode = Objects.requireNonNull(pathMode, "pathMode");
        if (protectedIdentifiers == null || protectedIdentifiers.size() > 64
                || protectedIdentifiers.stream().anyMatch(value -> value == null
                        || value.isBlank() || value.length() > 256)) {
            throw new IllegalArgumentException("invalid protected identifiers");
        }
        this.protectedIdentifiers = List.copyOf(protectedIdentifiers);
    }

    public RedactionSession newSession() {
        return new RedactionSession(this);
    }

    /** A final built-in guard for completed visible/generalized model text. */
    public static boolean containsBuiltInSensitiveData(String value) {
        Objects.requireNonNull(value, "value");
        return PRIVATE_KEY.matcher(value).find()
                || CREDENTIAL.matcher(value).find()
                || STANDALONE_TOKEN.matcher(value).find()
                || UUID.matcher(value).find()
                || EMAIL.matcher(value).find()
                || ADDRESS.matcher(value).find()
                || ABSOLUTE_PATH.matcher(value).find();
    }

    DisplayText transform(CandidateText candidate, FieldKind kind, TextOrigin origin,
            RedactionSession session) {
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(origin, "origin");
        var raw = candidate.value();
        if (PRIVATE_KEY.matcher(raw).find()) {
            return session.redacted(origin, Sensitivity.SECRET, "PRIVATE_KEY");
        }
        if (CREDENTIAL.matcher(raw).find() || STANDALONE_TOKEN.matcher(raw).find()) {
            return session.redacted(origin, Sensitivity.SECRET, "TOKEN");
        }
        var folded = raw.toLowerCase(Locale.ROOT);
        for (var identifier : protectedIdentifiers) {
            if (folded.contains(identifier.toLowerCase(Locale.ROOT))) {
                return session.redacted(origin, Sensitivity.SECRET, "CONFIGURED_IDENTIFIER");
            }
        }
        if (UUID.matcher(raw).find() || EMAIL.matcher(raw).find()) {
            return session.redacted(origin, Sensitivity.PERSONAL, "PERSONAL_IDENTIFIER");
        }
        if (ADDRESS.matcher(raw).find()) {
            return session.redacted(origin, Sensitivity.SERVER_SENSITIVE, "ADDRESS");
        }
        if (kind == FieldKind.WORLD_DATA) {
            return session.omitted(origin, Sensitivity.WORLD_DATA, "WORLD_DATA");
        }
        if (kind == FieldKind.PATH) {
            return safePath(raw, origin, session);
        }
        if (ABSOLUTE_PATH.matcher(raw).find()) {
            return session.redacted(origin, Sensitivity.SERVER_SENSITIVE, "PATH");
        }
        if (kind != FieldKind.VERIFIED_PUBLIC && kind != FieldKind.VERIFIED_IDENTIFIER) {
            return session.omitted(origin, Sensitivity.SECRET, "UNTRUSTED_TEXT");
        }
        if (kind == FieldKind.VERIFIED_IDENTIFIER && !IDENTIFIER.matcher(raw).matches()) {
            return session.omitted(origin, Sensitivity.SECRET, "UNSAFE_IDENTIFIER");
        }
        try {
            return DisplayText.visible(raw, origin);
        } catch (RuntimeException invalidText) {
            return session.omitted(origin, Sensitivity.SECRET, "UNSAFE_TEXT");
        }
    }

    private DisplayText safePath(String raw, TextOrigin origin, RedactionSession session) {
        if (pathMode == PathMode.REDACT_ALL) {
            return session.redacted(origin, Sensitivity.SERVER_SENSITIVE, "PATH");
        }
        try {
            var candidate = Path.of(raw).toRealPath();
            if (!candidate.startsWith(approvedRoot) || candidate.equals(approvedRoot)) {
                return session.redacted(origin, Sensitivity.SERVER_SENSITIVE, "PATH");
            }
            var relative = approvedRoot.relativize(candidate).toString().replace('\\', '/');
            if (relative.isBlank() || relative.startsWith("../")
                    || PRIVATE_KEY.matcher(relative).find()
                    || CREDENTIAL.matcher(relative).find()
                    || STANDALONE_TOKEN.matcher(relative).find()
                    || UUID.matcher(relative).find()
                    || ADDRESS.matcher(relative).find()
                    || EMAIL.matcher(relative).find()) {
                return session.redacted(origin, Sensitivity.SERVER_SENSITIVE, "PATH");
            }
            try {
                return session.generalized(relative, origin, Sensitivity.SERVER_SENSITIVE, "PATH");
            } catch (RuntimeException unsafeRelative) {
                return session.redacted(origin, Sensitivity.SERVER_SENSITIVE, "PATH");
            }
        } catch (IOException | RuntimeException inaccessible) {
            return session.redacted(origin, Sensitivity.SERVER_SENSITIVE, "PATH");
        }
    }

    @Override
    public String toString() {
        return "RedactionPolicy[configuredIdentifiers=" + protectedIdentifiers.size()
                + ", pathMode=" + pathMode + "]";
    }
}
