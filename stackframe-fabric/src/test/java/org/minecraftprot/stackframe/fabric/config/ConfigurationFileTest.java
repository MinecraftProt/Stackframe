package org.minecraftprot.stackframe.fabric.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.minecraftprot.stackframe.diagnostic.DiagnosticCode;

class ConfigurationFileTest {
    @TempDir Path temporaryDirectory;

    @Test
    void versionOneFixtureParsesOnlyLiveSettings() throws Exception {
        var parsed = ConfigurationFile.parse(fixture("valid-v1.properties"));

        assertEquals(StackframeConfiguration.Output.ANSI, parsed.output());
        assertEquals(Path.of("logs", "private-traces"), parsed.traceDirectory());
        assertTrue(parsed.retention().automatic());
        assertEquals(7, parsed.retention().maxAge().toDays());
        assertEquals(64, parsed.retention().maxFiles());
        assertFalse(parsed.deduplication().enabled());
        assertEquals(256, parsed.deduplication().maxEntries());
        assertTrue(parsed.includes(new DiagnosticCode("SF0001")));
    }

    @Test
    void missingFileUsesSafeDocumentedDefaults() throws Exception {
        var loaded = ConfigurationFile.load(temporaryDirectory);

        assertEquals(StackframeConfiguration.DEFAULT, loaded);
        assertEquals(StackframeConfiguration.Output.PLAIN, loaded.output());
        assertFalse(loaded.retention().automatic());
        assertTrue(loaded.deduplication().enabled());
        assertTrue(loaded.includes(new DiagnosticCode("SF0001")));
    }

    @Test
    void exactLocationsAndMigrationPolicyAreReportedWithoutEchoingValues() throws Exception {
        assertProblem("migration-v0.properties", 1, "schema_version", "migration");
        assertProblem("future-v2.properties", 1, "schema_version", "unsupported schema");
        assertProblem("unknown-v1.properties", 2, "verbosity", "unknown setting");
        assertProblem("duplicate-v1.properties", 3, "output", "duplicate setting");
        assertProblem("malformed-v1.properties", 3, "trace_max_age_days", "1 to 365");
    }

    @Test
    void invalidSettingsDoNotFallBackOrBecomeSilentNoOps() throws Exception {
        var invalid = new String[] {
            "schema_version=1\noutput=json\n",
            "schema_version=1\ntrace_directory=../private\n",
            "schema_version=1\ntrace_directory=logs/../private\n",
            "schema_version=1\ntrace_retention=manual\ntrace_max_files=5\n",
            "schema_version=1\ntrace_retention=bounded\ntrace_max_age_days=5\n",
            "schema_version=1\ninclude_codes=none\nexclude_codes=SF0001\n",
            "schema_version=1\nredaction=off\n",
            "schema_version=1\ndedup_window_ms=-1\n",
            "schema_version=1\ndedup_window_ms=0\ndedup_max_entries=16\n",
            "schema_version=1\noutput=ansi\noutput=plain\n"
        };
        for (var source : invalid) {
            assertThrows(ConfigurationProblem.class,
                    () -> ConfigurationFile.parse(source), source);
        }
    }

    @Test
    void unreadableOrMalformedUtf8FileIsAnErrorNotDefaults() throws Exception {
        var configDirectory = Files.createDirectory(temporaryDirectory.resolve("config"));
        var file = configDirectory.resolve("stackframe.properties");
        Files.write(file, new byte[] {(byte) 0xc3, (byte) 0x28});

        var malformed = assertThrows(ConfigurationProblem.class,
                () -> ConfigurationFile.load(temporaryDirectory));
        assertEquals(0, malformed.line());
        assertEquals("file", malformed.key());
        assertTrue(malformed.getMessage().contains("UTF-8"));
    }

    @Test
    void presentValidFileLoadsAndPresentInvalidFileDoesNotUseDefaults() throws Exception {
        var configDirectory = Files.createDirectory(temporaryDirectory.resolve("config"));
        var file = configDirectory.resolve("stackframe.properties");
        Files.writeString(file, fixture("valid-v1.properties"));
        assertEquals(StackframeConfiguration.Output.ANSI,
                ConfigurationFile.load(temporaryDirectory).output());

        Files.writeString(file, "schema_version=1\noutput=unsafe\n");
        var problem = assertThrows(ConfigurationProblem.class,
                () -> ConfigurationFile.load(temporaryDirectory));
        assertEquals(2, problem.line());
        assertEquals("output", problem.key());
        assertFalse(problem.getMessage().contains("unsafe"));
    }

    private static void assertProblem(
            String fixture, int line, String key, String expected) throws Exception {
        var problem = assertThrows(ConfigurationProblem.class,
                () -> ConfigurationFile.parse(fixture(fixture)));
        assertEquals(line, problem.line());
        assertEquals(key, problem.key());
        assertTrue(problem.getMessage().contains("config/stackframe.properties:" + line));
        assertTrue(problem.getMessage().contains(expected));
    }

    private static String fixture(String name) throws IOException {
        try (var stream = ConfigurationFileTest.class.getResourceAsStream("/config/" + name)) {
            if (stream == null) {
                throw new IOException("missing test fixture");
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
