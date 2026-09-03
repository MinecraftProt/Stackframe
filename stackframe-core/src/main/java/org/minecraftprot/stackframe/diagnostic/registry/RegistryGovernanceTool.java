package org.minecraftprot.stackframe.diagnostic.registry;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Offline command used by Gradle to generate and verify governed registry artifacts. */
public final class RegistryGovernanceTool {
    private RegistryGovernanceTool() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            throw new RegistryValidationException(
                    "usage: check <catalog> <baseline> | write-catalog <catalog> | "
                            + "write-baseline <baseline>");
        }
        var command = args[0];
        var registry = CanonicalDiagnosticRegistry.snapshot();
        switch (command) {
            case "check" -> {
                if (args.length != 3) {
                    throw new RegistryValidationException(
                            "check requires catalog and baseline paths");
                }
                RegistryCatalogGenerator.verify(registry, read(Path.of(args[1])));
                RegistryCompatibility.verify(registry, read(Path.of(args[2])));
            }
            case "write-catalog" -> {
                requireLength(args, 2);
                write(Path.of(args[1]), RegistryCatalogGenerator.generate(registry));
            }
            case "write-baseline" -> {
                requireLength(args, 2);
                write(Path.of(args[1]), RegistryCompatibility.baseline(registry));
            }
            default -> throw new RegistryValidationException("unknown command: " + command);
        }
    }

    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private static void write(Path path, String value) throws IOException {
        Files.createDirectories(path.toAbsolutePath().getParent());
        Files.writeString(path, value, StandardCharsets.UTF_8);
    }

    private static void requireLength(String[] args, int expected) {
        if (args.length != expected) {
            throw new RegistryValidationException(
                    args[0] + " requires exactly one output path");
        }
    }
}
