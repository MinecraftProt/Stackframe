package org.minecraftprot.stackframe.trace;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** Opt-in cleanup of bounded, Stackframe-named trace files only. */
public final class TraceRetention {
    private static final Pattern OWNED_TRACE =
            Pattern.compile("[0-9A-HJKMNP-TV-Z]{6,26}\\.trace");
    private static final int MAX_SCAN = 8_192;
    private static final byte[] OWNERSHIP_HEADER =
            TraceRecorder.FILE_HEADER.getBytes(StandardCharsets.UTF_8);

    public enum Status {
        MANUAL,
        CLEAN,
        DIRECTORY_MISSING,
        UNSAFE_DIRECTORY,
        SCAN_LIMIT,
        PARTIAL_FAILURE
    }

    /** Manual is the default; automatic mode requires both age and count caps. */
    public record Policy(boolean automatic, Duration maxAge, int maxFiles) {
        public Policy {
            if (maxAge == null || (automatic
                    ? maxAge.isZero() || maxAge.isNegative()
                            || maxAge.compareTo(Duration.ofDays(365)) > 0
                            || maxFiles < 1 || maxFiles > MAX_SCAN
                    : !maxAge.isZero() || maxFiles != 0)) {
                throw new IllegalArgumentException("invalid trace retention policy");
            }
        }

        public static Policy manual() {
            return new Policy(false, Duration.ZERO, 0);
        }

        public static Policy bounded(Duration maxAge, int maxFiles) {
            return new Policy(true, maxAge, maxFiles);
        }
    }

    /** Safe counters and category; source paths and exception messages stay private. */
    public record Result(Status status, int inspected, int deleted, int failures) {
        public Result {
            if (status == null || inspected < 0 || deleted < 0 || failures < 0
                    || inspected > MAX_SCAN || deleted > inspected || failures > MAX_SCAN + 1) {
                throw new IllegalArgumentException("invalid retention result");
            }
        }

        public boolean failed() {
            return status == Status.DIRECTORY_MISSING || status == Status.UNSAFE_DIRECTORY
                    || status == Status.SCAN_LIMIT || status == Status.PARTIAL_FAILURE;
        }
    }

    private final Path directory;
    private final Policy policy;
    private final Clock clock;

    public TraceRetention(Path directory, Policy policy) {
        this(directory, policy, Clock.systemUTC());
    }

    TraceRetention(Path directory, Policy policy, Clock clock) {
        this.directory = Objects.requireNonNull(directory, "directory")
                .toAbsolutePath().normalize();
        this.policy = Objects.requireNonNull(policy, "policy");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Does not create directories, traverse links, or touch partial/unrelated files. */
    public Result clean() {
        return clean(null);
    }

    /** Preserve a just-published trace while enforcing limits on earlier records. */
    public Result clean(Path protectedTrace) {
        var protectedPath = protectedTrace == null
                ? null : protectedTrace.toAbsolutePath().normalize();
        if (protectedPath != null && !directory.equals(protectedPath.getParent())) {
            throw new IllegalArgumentException("protected trace must be in the retention directory");
        }
        if (!policy.automatic()) {
            return new Result(Status.MANUAL, 0, 0, 0);
        }
        if (hasSymbolicComponent(directory)
                || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            return new Result(Files.exists(directory, LinkOption.NOFOLLOW_LINKS)
                    ? Status.UNSAFE_DIRECTORY : Status.DIRECTORY_MISSING, 0, 0, 0);
        }
        var files = new ArrayList<TraceFile>();
        var inspected = 0;
        var failures = 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            for (var path : stream) {
                inspected++;
                if (inspected > MAX_SCAN) {
                    return new Result(Status.SCAN_LIMIT, MAX_SCAN, 0, 0);
                }
                if (!OWNED_TRACE.matcher(path.getFileName().toString()).matches()) {
                    continue;
                }
                try {
                    var attributes = Files.readAttributes(path, BasicFileAttributes.class,
                            LinkOption.NOFOLLOW_LINKS);
                    if (attributes.isRegularFile() && hasOwnershipHeader(path)) {
                        files.add(new TraceFile(path, attributes.lastModifiedTime().toInstant(),
                                attributes.fileKey()));
                    }
                } catch (IOException | SecurityException unreadable) {
                    failures++;
                }
            }
        } catch (IOException | SecurityException unreadable) {
            return new Result(Status.PARTIAL_FAILURE, Math.min(inspected, MAX_SCAN), 0,
                    Math.max(1, failures));
        }

        var cutoff = clock.instant().minus(policy.maxAge());
        files.sort(Comparator.comparing(TraceFile::modified).reversed()
                .thenComparing(file -> file.path().getFileName().toString()));
        if (protectedPath != null) {
            for (var index = 0; index < files.size(); index++) {
                if (files.get(index).path().equals(protectedPath)) {
                    files.addFirst(files.remove(index));
                    break;
                }
            }
        }
        Set<Path> selected = new HashSet<>();
        for (var index = 0; index < files.size(); index++) {
            var file = files.get(index);
            if (file.path().equals(protectedPath)) {
                continue;
            }
            if (file.modified().isBefore(cutoff) || index >= policy.maxFiles()) {
                selected.add(file.path());
            }
        }
        var deleted = 0;
        for (var file : files) {
            if (!selected.contains(file.path())) {
                continue;
            }
            try {
                var current = Files.readAttributes(file.path(), BasicFileAttributes.class,
                        LinkOption.NOFOLLOW_LINKS);
                if (!current.isRegularFile() || !hasOwnershipHeader(file.path())
                        || (file.fileKey() != null
                                && !file.fileKey().equals(current.fileKey()))) {
                    failures++;
                    continue;
                }
                Files.delete(file.path());
                deleted++;
            } catch (IOException | SecurityException changed) {
                failures++;
            }
        }
        return new Result(failures == 0 ? Status.CLEAN : Status.PARTIAL_FAILURE,
                inspected, deleted, failures);
    }

    private static boolean hasSymbolicComponent(Path path) {
        var current = path.getRoot();
        for (var part : path) {
            current = current == null ? part : current.resolve(part);
            if (Files.isSymbolicLink(current)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasOwnershipHeader(Path path) throws IOException {
        try (var input = Files.newInputStream(
                path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
            return Arrays.equals(OWNERSHIP_HEADER, input.readNBytes(OWNERSHIP_HEADER.length));
        }
    }

    private record TraceFile(Path path, java.time.Instant modified, Object fileKey) {
    }
}
