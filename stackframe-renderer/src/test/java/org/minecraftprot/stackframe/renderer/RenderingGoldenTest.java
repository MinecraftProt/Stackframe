package org.minecraftprot.stackframe.renderer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class RenderingGoldenTest {
    @Test
    void rendersMinimumAndFullDiagnostics() throws IOException {
        assertEquals(golden("golden/basic/minimum.txt"), render(RendererFixtures.minimum(), 80));
        assertEquals(golden("golden/basic/full.txt"), render(RendererFixtures.full(), 100));
    }

    @Test
    void rendersUnicodeAndMultilineExcerpts() throws IOException {
        assertEquals(
                golden("golden/excerpts/unicode.txt"),
                render(RendererFixtures.unicodeExcerpt(), 100));
        assertEquals(
                golden("golden/excerpts/multiline.txt"),
                render(RendererFixtures.multilineExcerpt(), 80));
    }

    @Test
    void rendersOmissionsRedactionsAndWriteFailure() throws IOException {
        assertEquals(
                golden("golden/metadata/omissions.txt"),
                render(RendererFixtures.omissionsAndWriteFailure(), 80));
    }

    @Test
    void rendersWidthPolicies() throws IOException {
        assertAll(
                () -> assertEquals(golden("golden/width/39.txt"),
                        render(RendererFixtures.widthFixture(), 39), "width 39"),
                () -> assertEquals(golden("golden/width/40.txt"),
                        render(RendererFixtures.widthFixture(), 40), "width 40"),
                () -> assertEquals(golden("golden/width/79.txt"),
                        render(RendererFixtures.widthFixture(), 79), "width 79"),
                () -> assertEquals(golden("golden/width/80.txt"),
                        render(RendererFixtures.widthFixture(), 80), "width 80"),
                () -> assertEquals(golden("golden/width/100.txt"),
                        render(RendererFixtures.widthFixture(), 100), "width 100"));
    }

    private static String render(
            org.minecraftprot.stackframe.diagnostic.DiagnosticDocument document, int width) {
        return DiagnosticRenderer.renderToString(
                document, RenderOptions.plain(RenderWidth.known(width)));
    }

    private static String golden(String name) throws IOException {
        try (var stream = RenderingGoldenTest.class.getClassLoader().getResourceAsStream(name)) {
            if (stream == null) {
                throw new IOException("missing golden resource " + name);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8)
                    .replace("\r\n", "\n");
        }
    }
}
