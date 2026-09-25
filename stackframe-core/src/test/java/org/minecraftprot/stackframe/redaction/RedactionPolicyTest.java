package org.minecraftprot.stackframe.redaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.minecraftprot.stackframe.diagnostic.CandidateText;
import org.minecraftprot.stackframe.diagnostic.TextDisposition;
import org.minecraftprot.stackframe.diagnostic.TextOrigin;

final class RedactionPolicyTest {
    @TempDir Path root;

    @Test
    void knownCredentialsAreRejectedWithoutEchoingTheirValues() throws IOException {
        var inputs = List.of(
                "Authorization: Bearer abc.def.123",
                "Authorization: Basic dXNlcjpwYXNz",
                "password = summer-horse-battery",
                "api_key: hidden123",
                "github_pat_abcdefghijklmnopqrstuvwxyz0123456789",
                "ghp_abcdefghijklmnopqrstuvwxyz0123456789",
                "glpat-abcdefghijklmnopqrstuvwxyz",
                "AKIAABCDEFGHIJKLMNOP",
                "xox" + "b-1234567890-abcdefghijklmnop",
                "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjMifQ.signature",
                "-----BEGIN OPENSSH PRIVATE KEY-----");
        var session = new RedactionPolicy(root, Set.of()).newSession();
        for (var input : inputs) {
            var result = session.transform(new CandidateText(input),
                    RedactionPolicy.FieldKind.VERIFIED_PUBLIC, TextOrigin.EXTERNAL);
            assertEquals(TextDisposition.REDACTED, result.disposition(), input);
            assertFalse(result.value().contains(input), input);
            assertFalse(result.toString().contains(input), input);
        }
        assertEquals(inputs.size(), session.notices().items().stream()
                .mapToInt(notice -> notice.occurrenceCount()).sum());
    }

    @Test
    void addressesAndConfiguredIdentifiersDoNotLeakInOtherwisePublicText()
            throws IOException {
        var session = new RedactionPolicy(root, Set.of("private-user")).newSession();
        for (var input : List.of("server https://example.invalid/private",
                "connect 10.12.34.56:25565", "connect [2001:db8::1]:25565",
                "connect mc.example.invalid:25565", "player alice@example.invalid",
                "user 123e4567-e89b-12d3-a456-426614174000",
                "The PRIVATE-USER account failed")) {
            var result = session.transform(new CandidateText(input),
                    RedactionPolicy.FieldKind.VERIFIED_PUBLIC, TextOrigin.EXTERNAL);
            assertEquals(TextDisposition.REDACTED, result.disposition(), input);
            assertFalse(result.value().contains(input), input);
        }
        assertFalse(session.notices().toString().contains("private-user"));
    }

    @Test
    void unknownMessagesLabelsAndExcerptsAreOmittedEvenWithoutKnownTokens()
            throws IOException {
        var session = new RedactionPolicy(root, Set.of()).newSession();
        for (var kind : List.of(RedactionPolicy.FieldKind.UNTRUSTED_MESSAGE,
                RedactionPolicy.FieldKind.LABEL, RedactionPolicy.FieldKind.EXCERPT)) {
            var result = session.transform(new CandidateText("a user-controlled surprise"),
                    kind, TextOrigin.EXTERNAL);
            assertEquals(TextDisposition.OMITTED, result.disposition());
            assertEquals("<omitted:untrusted_text>", result.value());
        }
        assertEquals(TextDisposition.OMITTED, session.transform(
                new CandidateText("world/playerdata"), RedactionPolicy.FieldKind.WORLD_DATA,
                TextOrigin.EXTERNAL).disposition());
        assertEquals(TextDisposition.OMITTED, session.transform(
                new CandidateText("control\u001b[31m"),
                RedactionPolicy.FieldKind.VERIFIED_PUBLIC,
                TextOrigin.EXTERNAL).disposition());
    }

    @Test
    void pathsRequireExplicitRelativeModeAndCanonicalContainment() throws IOException {
        var file = root.resolve("config/server.properties");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "fixture");
        var defaultSession = new RedactionPolicy(root, Set.of()).newSession();
        var hidden = defaultSession.transform(new CandidateText(file.toString()),
                RedactionPolicy.FieldKind.PATH, TextOrigin.EXTERNAL);
        assertEquals("<redacted:path>", hidden.value());

        var relativeSession = new RedactionPolicy(root, Set.of(),
                RedactionPolicy.PathMode.RELATIVE_WITHIN_ROOT).newSession();
        var relative = relativeSession.transform(new CandidateText(file.toString()),
                RedactionPolicy.FieldKind.PATH, TextOrigin.EXTERNAL);
        assertEquals(TextDisposition.GENERALIZED, relative.disposition());
        assertEquals("config/server.properties", relative.value());
        assertFalse(relative.value().contains(root.toString()));

        var outside = Files.createTempFile(root.getParent(), "outside-", ".txt");
        try {
            var escaped = relativeSession.transform(new CandidateText(outside.toString()),
                    RedactionPolicy.FieldKind.PATH, TextOrigin.EXTERNAL);
            assertEquals("<redacted:path>", escaped.value());
            var traversal = relativeSession.transform(new CandidateText(
                    root.resolve("../" + outside.getFileName()).toString()),
                    RedactionPolicy.FieldKind.PATH, TextOrigin.EXTERNAL);
            assertEquals("<redacted:path>", traversal.value());
        } finally {
            Files.deleteIfExists(outside);
        }
        assertTrue(relativeSession.notices().items().stream()
                .anyMatch(notice -> notice.transformation() == TextDisposition.GENERALIZED));
    }
}
