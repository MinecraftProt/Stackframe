package org.minecraftprot.stackframe.fabric;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import net.fabricmc.loader.api.Version;
import org.junit.jupiter.api.Test;

class FabricArtifactTest {
    private static final String ICU_LICENSE_SHA256 =
            "e55522d81edc687a341a4411e0776e54ca654e90147f354a90458aaced4116af";
    private static final String ICU_LICENSE_ENTRY =
            "META-INF/licenses/icu4j-78.3-LICENSE.txt";
    private static final String ICU_JAR_ENTRY = "META-INF/jars/icu4j-78.3.jar";
    private static final Pattern VERSION =
            Pattern.compile("\"version\"\\s*:\\s*\"([^\"]+)\"");

    @Test
    void distributionContainsModulesLicensesAndLoadableServerMetadata() throws Exception {
        var artifact = requiredPath("stackframe.fabricArtifact");
        var stackframeLicense = Files.readAllBytes(requiredPath("stackframe.stackframeLicense"));
        var icuLicense = Files.readAllBytes(requiredPath("stackframe.icuLicense"));
        var artifactVersion = System.getProperty("stackframe.artifactVersion");
        assertNotNull(artifactVersion, "missing test property stackframe.artifactVersion");

        assertEquals(ICU_LICENSE_SHA256, sha256(icuLicense));
        try (var zip = new ZipFile(artifact.toFile())) {
            assertArrayEquals(stackframeLicense, read(zip, "LICENSE_stackframe"));
            assertArrayEquals(icuLicense, read(zip, ICU_LICENSE_ENTRY));
            assertNotNull(zip.getEntry(
                    "META-INF/jars/stackframe-core-" + artifactVersion + ".jar"));
            assertNotNull(zip.getEntry(
                    "META-INF/jars/stackframe-renderer-" + artifactVersion + ".jar"));

            var metadata = new String(read(zip, "fabric.mod.json"), StandardCharsets.UTF_8);
            assertTrue(metadata.matches("(?s).*\"environment\"\\s*:\\s*\"server\".*"));
            assertFalse(metadata.matches("(?s).*\"client\"\\s*:.*"));

            var icuMetadata = nestedEntry(read(zip, ICU_JAR_ENTRY), "fabric.mod.json");
            var matcher = VERSION.matcher(new String(icuMetadata, StandardCharsets.UTF_8));
            assertTrue(matcher.find());
            assertEquals("78.3", matcher.group(1));
            assertEquals("78.3", Version.parse(matcher.group(1)).getFriendlyString());
        }
    }

    private static Path requiredPath(String property) {
        var value = System.getProperty(property);
        assertNotNull(value, () -> "missing test property " + property);
        return Path.of(value);
    }

    private static byte[] read(ZipFile zip, String name) throws IOException {
        var entry = zip.getEntry(name);
        assertNotNull(entry, () -> "missing artifact entry " + name);
        try (var input = zip.getInputStream(entry)) {
            return input.readAllBytes();
        }
    }

    private static byte[] nestedEntry(byte[] archive, String name) throws IOException {
        try (var input = new ZipInputStream(new ByteArrayInputStream(archive))) {
            for (var entry = input.getNextEntry(); entry != null; entry = input.getNextEntry()) {
                if (entry.getName().equals(name)) {
                    return input.readAllBytes();
                }
            }
        }
        throw new AssertionError("missing nested artifact entry " + name);
    }

    private static String sha256(byte[] value) throws NoSuchAlgorithmException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    }
}
