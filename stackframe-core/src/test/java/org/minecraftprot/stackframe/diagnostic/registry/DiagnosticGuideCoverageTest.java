package org.minecraftprot.stackframe.diagnostic.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class DiagnosticGuideCoverageTest {
    @Test
    void everyEmittableCodeHasAnOperatorGuideMatchingTheRegistry() throws IOException {
        var guideDirectory = Path.of(System.getProperty("user.dir"))
                .getParent()
                .resolve("docs/diagnostics");
        var index = Files.readString(
                guideDirectory.resolve("README.md"), StandardCharsets.UTF_8);
        var expectedCodes = CanonicalDiagnosticRegistry.snapshot().entries().stream()
                .filter(entry -> entry.lifecycle() != DiagnosticLifecycle.RESERVED)
                .map(entry -> entry.code().value())
                .collect(Collectors.toSet());

        for (var entry : CanonicalDiagnosticRegistry.snapshot().entries()) {
            if (entry.lifecycle() == DiagnosticLifecycle.RESERVED) {
                continue;
            }
            var code = entry.code().value();
            var page = guideDirectory.resolve(code + ".md");
            assertTrue(Files.isRegularFile(page), () -> "missing guide for " + code);
            var guide = Files.readString(page, StandardCharsets.UTF_8)
                    .replaceAll("\\s+", " ");

            assertContains(guide, "# " + code + ": " + entry.title().value(), code);
            assertContains(guide, "**Symbolic key:** `" + entry.symbolicKey() + "`", code);
            assertContains(guide, "**Registry status:** `" + entry.lifecycle() + "`", code);
            assertContains(guide, "**Release status:**", code);
            assertContains(guide, "**Meaning:** " + entry.meaning(), code);
            assertContains(guide, "**Required evidence:** " + entry.evidence().description(), code);
            assertContains(guide, "**Fallback:** " + entry.fallback().description(), code);
            assertContains(guide,
                    "**Remediation ceiling:** `" + entry.remediation().safety() + "`", code);
            for (var action : entry.remediation().actions()) {
                assertContains(guide, "`" + action + "`", code);
            }
            assertContains(guide,
                    "(../diagnostic-registry/catalog.md#" + entry.documentationAnchor() + ")",
                    code);
            assertContains(guide, "## Plain example", code);
            assertContains(guide, "## Safe checks", code);
            assertContains(guide, "## Recovery actions and limits", code);
            assertTrue(index.contains("](" + code + ".md)"),
                    () -> "operator index does not link " + code);
        }

        Set<String> actualCodes;
        try (var pages = Files.list(guideDirectory)) {
            actualCodes = pages.map(path -> path.getFileName().toString())
                    .filter(name -> name.matches("SF[0-9]{4}\\.md"))
                    .map(name -> name.substring(0, 6))
                    .collect(Collectors.toSet());
        }
        assertEquals(expectedCodes, actualCodes, "orphan or missing operator guide");
    }

    private static void assertContains(String guide, String expected, String code) {
        assertTrue(guide.contains(expected),
                () -> code + " guide does not contain registry contract: " + expected);
    }
}
