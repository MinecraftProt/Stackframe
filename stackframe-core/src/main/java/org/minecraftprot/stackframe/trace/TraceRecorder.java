package org.minecraftprot.stackframe.trace;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import org.minecraftprot.stackframe.diagnostic.CorrelationId;
import org.minecraftprot.stackframe.diagnostic.DiagnosticId;
import org.minecraftprot.stackframe.diagnostic.TraceState;

/**
 * Preserves the original throwable in a dedicated local file before a concise
 * diagnostic may claim that its full trace is available. Each record is closed
 * and published separately, so Log4j rotation cannot split a trace.
 *
 * <p>This is raw local debug data, not renderer-safe output. Callers must keep the
 * original platform log event flowing even when this recorder reports failure.
 */
public final class TraceRecorder {
    /** Marker allows automatic retention to distinguish Stackframe-owned files. */
    public static final String FILE_HEADER = "Stackframe trace v1\n";
    private static final char[] BASE32 =
            "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final int ID_BYTES = 10;
    private static final int MAX_IDENTIFIER_ATTEMPTS = 32;

    private final Path directory;
    private final SecureRandom random;

    public TraceRecorder(Path directory) {
        this(directory, new SecureRandom());
    }

    TraceRecorder(Path directory, SecureRandom random) {
        this.directory = Objects.requireNonNull(directory, "directory")
                .toAbsolutePath().normalize();
        this.random = Objects.requireNonNull(random, "random");
    }

    public static Path defaultDirectory() {
        return Path.of("logs", "stackframe-traces");
    }

    public Path directory() {
        return directory;
    }

    /** Allocate a candidate ID before correlation; only a successful write reserves it. */
    public CorrelationId newCorrelationId() {
        return new CorrelationId(randomIdentifier());
    }

    /**
     * Preserve the original failure using the correlation ID chosen before
     * duplicate suppression. A collision fails safely instead of changing the
     * ID already returned by the correlator or replacing an existing trace.
     */
    public TraceRecord record(Throwable throwable, CorrelationId id) {
        Objects.requireNonNull(throwable, "throwable");
        Objects.requireNonNull(id, "id");
        try {
            prepareDirectory();
            var target = directory.resolve(id.value() + ".trace");
            var partial = directory.resolve(id.value() + ".partial");
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                return failed(id, TraceWriteFailure.IDENTIFIER_EXHAUSTED);
            }
            try {
                createPrivateFile(partial);
            } catch (FileAlreadyExistsException collision) {
                return failed(id, TraceWriteFailure.IDENTIFIER_EXHAUSTED);
            }
            try {
                if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                    return failed(id, TraceWriteFailure.IDENTIFIER_EXHAUSTED);
                }
                writeThrowable(partial, throwable);
                publish(partial, target);
                return new TraceRecord(
                        id,
                        new DiagnosticId(id.value()),
                        TraceState.PRESERVED,
                        Optional.of(target),
                        Optional.empty());
            } catch (FileAlreadyExistsException collision) {
                return failed(id, TraceWriteFailure.IDENTIFIER_EXHAUSTED);
            } finally {
                removePartial(partial);
            }
        } catch (Throwable failure) {
            if (failure instanceof Error) {
                throw (Error) failure;
            }
            var category = failure instanceof SecurityException
                    ? TraceWriteFailure.PERMISSION_DENIED
                    : failure instanceof IOException
                            ? TraceWriteFailure.STORAGE_UNAVAILABLE
                            : TraceWriteFailure.TRACE_UNREADABLE;
            return failed(id, category);
        }
    }

    /**
     * Returns a safe status even if the filesystem or throwable printer fails.
     * JVM errors remain fatal to the caller.
     */
    public TraceRecord record(Throwable throwable) {
        Objects.requireNonNull(throwable, "throwable");
        CorrelationId id = null;
        try {
            prepareDirectory();
            for (var attempt = 0; attempt < MAX_IDENTIFIER_ATTEMPTS; attempt++) {
                id = new CorrelationId(randomIdentifier());
                var target = directory.resolve(id.value() + ".trace");
                var partial = directory.resolve(id.value() + ".partial");
                if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                    continue;
                }
                try {
                    createPrivateFile(partial);
                } catch (FileAlreadyExistsException collision) {
                    continue;
                }
                try {
                    if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                        continue;
                    }
                    writeThrowable(partial, throwable);
                    publish(partial, target);
                    return new TraceRecord(
                            id,
                            new DiagnosticId(id.value()),
                            TraceState.PRESERVED,
                            Optional.of(target),
                            Optional.empty());
                } catch (FileAlreadyExistsException collision) {
                    // Another process published the same identifier. Try a new one.
                } finally {
                    removePartial(partial);
                }
            }
            return failed(id, TraceWriteFailure.IDENTIFIER_EXHAUSTED);
        } catch (Throwable failure) {
            if (failure instanceof Error) {
                throw (Error) failure;
            }
            if (id == null) {
                id = new CorrelationId(randomIdentifier());
            }
            var category = failure instanceof SecurityException
                    ? TraceWriteFailure.PERMISSION_DENIED
                    : failure instanceof IOException
                            ? TraceWriteFailure.STORAGE_UNAVAILABLE
                            : TraceWriteFailure.TRACE_UNREADABLE;
            return failed(id, category);
        }
    }

    private static TraceRecord failed(CorrelationId id, TraceWriteFailure failure) {
        return new TraceRecord(
                id,
                new DiagnosticId(id.value()),
                TraceState.WRITE_FAILED,
                Optional.empty(),
                Optional.of(failure));
    }

    private void prepareDirectory() throws IOException {
        if (hasSymbolicComponent(directory)) {
            throw new IOException("trace directory path must not contain a symbolic link");
        }
        if (Files.getFileAttributeView(directory, PosixFileAttributeView.class) != null) {
            Files.createDirectories(
                    directory,
                    PosixFilePermissions.asFileAttribute(
                            PosixFilePermissions.fromString("rwx------")));
        } else {
            Files.createDirectories(directory);
        }
        if (hasSymbolicComponent(directory)
                || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("trace directory is unavailable");
        }
        restrictAccess(directory, true);
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

    private static void createPrivateFile(Path path) throws IOException {
        if (Files.getFileAttributeView(path.getParent(), PosixFileAttributeView.class) != null) {
            Files.createFile(
                    path,
                    PosixFilePermissions.asFileAttribute(
                            PosixFilePermissions.fromString("rw-------")));
        } else {
            Files.createFile(path);
        }
        try {
            restrictAccess(path, false);
        } catch (IOException | RuntimeException failure) {
            removePartial(path);
            throw failure;
        }
    }

    private static void restrictAccess(Path path, boolean directory) throws IOException {
        var posix = Files.getFileAttributeView(
                path, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        if (posix != null) {
            posix.setPermissions(PosixFilePermissions.fromString(
                    directory ? "rwx------" : "rw-------"));
            return;
        }
        var acl = Files.getFileAttributeView(
                path, AclFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        if (acl != null) {
            var owner = acl.getOwner();
            var onlyOwner = AclEntry.newBuilder()
                    .setType(AclEntryType.ALLOW)
                    .setPrincipal(owner)
                    .setPermissions(EnumSet.allOf(AclEntryPermission.class))
                    .build();
            acl.setAcl(java.util.List.of(onlyOwner));
        }
    }

    private static void writeThrowable(Path partial, Throwable throwable) throws IOException {
        var writer = new PrintWriter(Files.newBufferedWriter(
                partial, StandardCharsets.UTF_8, StandardOpenOption.WRITE));
        try (writer) {
            writer.print(FILE_HEADER);
            throwable.printStackTrace(writer);
            writer.flush();
        }
        if (writer.checkError()) {
            throw new IOException("full trace could not be flushed");
        }
        try (var channel = FileChannel.open(partial, StandardOpenOption.WRITE)) {
            channel.force(true);
        }
    }

    private static void publish(Path partial, Path target) throws IOException {
        try {
            Files.move(partial, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(partial, target);
        }
    }

    private static void removePartial(Path partial) {
        try {
            Files.deleteIfExists(partial);
        } catch (IOException | SecurityException ignored) {
            // A leftover partial file is not a published complete trace.
        }
    }

    private String randomIdentifier() {
        var bytes = new byte[ID_BYTES];
        random.nextBytes(bytes);
        var result = new StringBuilder(16);
        var bits = 0;
        var value = 0;
        for (var current : bytes) {
            value = (value << 8) | (current & 0xff);
            bits += 8;
            while (bits >= 5) {
                bits -= 5;
                result.append(BASE32[(value >>> bits) & 31]);
            }
        }
        return result.toString();
    }
}
