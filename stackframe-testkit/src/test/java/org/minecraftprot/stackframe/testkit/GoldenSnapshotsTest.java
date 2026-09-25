package org.minecraftprot.stackframe.testkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GoldenSnapshotsTest {
    @TempDir
    Path directory;

    @Test
    void updatingRequiresExplicitOptInAndProducesReviewableUtf8() throws IOException {
        var file = directory.resolve("golden/example.txt");
        assertThrows(AssertionError.class, () -> GoldenSnapshots.assertMatches(file, "界\n"));
        assertTrue(Files.notExists(file));

        var previous = System.getProperty(GoldenSnapshots.UPDATE_PROPERTY);
        System.setProperty(GoldenSnapshots.UPDATE_PROPERTY, "true");
        try {
            GoldenSnapshots.assertMatches(file, "界\n");
        } finally {
            if (previous == null) {
                System.clearProperty(GoldenSnapshots.UPDATE_PROPERTY);
            } else {
                System.setProperty(GoldenSnapshots.UPDATE_PROPERTY, previous);
            }
        }

        assertEquals("界\n", Files.readString(file, StandardCharsets.UTF_8));
        GoldenSnapshots.assertMatches(file, "界\n");
        var mismatch = assertThrows(AssertionError.class,
                () -> GoldenSnapshots.assertMatches(file, "different\n"));
        assertTrue(mismatch.getMessage().contains("line 1"));
    }
}
