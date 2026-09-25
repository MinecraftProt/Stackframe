package org.minecraftprot.stackframe.testkit;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/** Compares UTF-8 snapshots; updates require an explicit test-task opt-in. */
public final class GoldenSnapshots {
    public static final String UPDATE_PROPERTY = "stackframe.updateGoldens";

    private GoldenSnapshots() {
    }

    public static void assertMatches(Path file, String actual) throws IOException {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(actual, "actual");
        if (Boolean.getBoolean(UPDATE_PROPERTY)) {
            var parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(file, actual, StandardCharsets.UTF_8);
            return;
        }
        if (!Files.isRegularFile(file)) {
            throw new AssertionError("missing golden file " + file);
        }
        var expected = Files.readString(file, StandardCharsets.UTF_8).replace("\r\n", "\n");
        if (!expected.equals(actual)) {
            throw new AssertionError("golden output differs at line "
                    + firstDifferentLine(expected, actual) + " in " + file
                    + "; run the explicit updateGoldenSnapshots task and review the diff");
        }
    }

    private static int firstDifferentLine(String expected, String actual) {
        var expectedLines = expected.split("\n", -1);
        var actualLines = actual.split("\n", -1);
        for (var index = 0; index < Math.min(expectedLines.length, actualLines.length); index++) {
            if (!expectedLines[index].equals(actualLines[index])) {
                return index + 1;
            }
        }
        return Math.min(expectedLines.length, actualLines.length) + 1;
    }
}
