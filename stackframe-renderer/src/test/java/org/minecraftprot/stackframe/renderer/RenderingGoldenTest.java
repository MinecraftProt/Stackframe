package org.minecraftprot.stackframe.renderer;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.minecraftprot.stackframe.testkit.GoldenSnapshots;

@Tag("golden")
class RenderingGoldenTest {
    @Test
    void rendersMinimumAndFullDiagnostics() throws IOException {
        assertSnapshot("golden/basic/minimum.txt", RendererFixtures.minimum(), 80);
        assertSnapshot("golden/basic/full.txt", RendererFixtures.full(), 100);
    }

    @Test
    void rendersUnicodeAndMultilineExcerpts() throws IOException {
        assertSnapshot("golden/excerpts/unicode.txt", RendererFixtures.unicodeExcerpt(), 100);
        assertSnapshot("golden/excerpts/multiline.txt", RendererFixtures.multilineExcerpt(), 80);
    }

    @Test
    void rendersOmissionsRedactionsAndWriteFailure() throws IOException {
        assertSnapshot("golden/metadata/omissions.txt",
                RendererFixtures.omissionsAndWriteFailure(), 80);
    }

    @Test
    void rendersWidthPolicies() throws IOException {
        assertAll(
                () -> assertSnapshot("golden/width/39.txt", RendererFixtures.widthFixture(), 39),
                () -> assertSnapshot("golden/width/40.txt", RendererFixtures.widthFixture(), 40),
                () -> assertSnapshot("golden/width/79.txt", RendererFixtures.widthFixture(), 79),
                () -> assertSnapshot("golden/width/80.txt", RendererFixtures.widthFixture(), 80),
                () -> assertSnapshot("golden/width/100.txt", RendererFixtures.widthFixture(), 100));
    }

    private static void assertSnapshot(String name,
            org.minecraftprot.stackframe.diagnostic.DiagnosticDocument document, int width)
            throws IOException {
        var renderWidth = RenderWidth.known(width);
        var plain = DiagnosticRenderer.renderToString(document, RenderOptions.plain(renderWidth));
        var ansi = DiagnosticRenderer.renderToString(document, RenderOptions.ansi(renderWidth));
        GoldenSnapshots.assertMatches(Path.of("src/test/resources").resolve(name), plain);
        assertEquals(plain, AnsiText.stripStyling(ansi), "ANSI/plain equivalence for " + name);
    }
}
