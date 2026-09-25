package org.minecraftprot.stackframe.fabric.client;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;

class FabricClientArtifactTest {
    @Test
    void distributionIsDistinctAndPhysicallyClientOnly() throws Exception {
        var artifact = requiredPath("stackframe.clientArtifact");
        var version = System.getProperty("stackframe.artifactVersion");
        assertNotNull(version);
        assertEquals("stackframe-fabric-client-" + version + ".jar",
                artifact.getFileName().toString());

        try (var jar = new ZipFile(artifact.toFile())) {
            var metadata = new String(read(jar, "fabric.mod.json"), StandardCharsets.UTF_8);
            assertTrue(hasStringField(metadata, "id", "stackframe_client"));
            assertTrue(hasStringField(metadata, "name", "Stackframe Fabric Client"));
            assertTrue(hasStringField(metadata, "version", version));
            assertTrue(hasStringField(metadata, "environment", "client"));
            assertTrue(hasArrayField(metadata, "client"));
            assertTrue(metadata.contains("org.minecraftprot.stackframe.fabric.client.StackframeClient"));
            assertFalse(hasArrayField(metadata, "main"));
            assertFalse(hasArrayField(metadata, "server"));
            assertFalse(hasArrayField(metadata, "preLaunch"));

            assertNotNull(jar.getEntry("org/minecraftprot/stackframe/fabric/client/StackframeClient.class"));
            assertNull(jar.getEntry("org/minecraftprot/stackframe/fabric/StackframeFabric.class"));
            assertNotNull(jar.getEntry("META-INF/jars/stackframe-core-" + version + ".jar"));
            assertNotNull(jar.getEntry("META-INF/jars/stackframe-renderer-" + version + ".jar"));
            assertNull(jar.getEntry("META-INF/jars/stackframe-fabric-" + version + ".jar"));
            assertNotNull(jar.getEntry("META-INF/jars/icu4j-78.3.jar"));
            assertArrayEquals(Files.readAllBytes(requiredPath("stackframe.stackframeLicense")),
                    read(jar, "LICENSE_stackframe"));
            assertArrayEquals(Files.readAllBytes(requiredPath("stackframe.icuLicense")),
                    read(jar, "META-INF/licenses/icu4j-78.3-LICENSE.txt"));
        }
    }

    private static boolean hasStringField(String json, String key, String value) {
        return Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\""
                + Pattern.quote(value) + "\"").matcher(json).find();
    }

    private static boolean hasArrayField(String json, String key) {
        return Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\\[")
                .matcher(json).find();
    }

    private static Path requiredPath(String property) {
        var value = System.getProperty(property);
        assertNotNull(value, () -> "missing test property " + property);
        return Path.of(value);
    }

    private static byte[] read(ZipFile jar, String name) throws IOException {
        var entry = jar.getEntry(name);
        assertNotNull(entry, () -> "missing artifact entry " + name);
        try (var stream = jar.getInputStream(entry)) {
            return stream.readAllBytes();
        }
    }
}
