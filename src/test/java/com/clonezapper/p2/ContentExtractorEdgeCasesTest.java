package com.clonezapper.p2;

import com.clonezapper.BaseTest;
import com.clonezapper.service.ContentExtractor;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("P2")
class ContentExtractorEdgeCasesTest extends BaseTest {

    @Autowired
    ContentExtractor extractor;

    @Test
    void unicodeContentIsPreserved() throws IOException {
        Path file = createFile("unicode.txt", "日本語テスト — Ünïcödé — αβγδ");
        String result = extractor.extract(file);
        assertThat(result).contains("日本語");
        assertThat(result).contains("Ünïcödé");
    }

    @Test
    void multilineContentPreservesWords() throws IOException {
        Path file = createFile("multiline.txt",
            "Line one\nLine two\nLine three\n");
        String result = extractor.extract(file);
        assertThat(result).contains("Line one");
        assertThat(result).contains("Line three");
    }

    @Test
    void twoIdenticalFilesProduceSameExtraction() throws IOException {
        String content = "Identical document content for dedup testing.";
        Path a = createFile("a/doc.txt", content);
        Path b = createFile("b/doc.txt", content);
        assertThat(extractor.extract(a)).isEqualTo(extractor.extract(b));
    }

    @Test
    void twoDifferentFilesProduceDifferentExtraction() throws IOException {
        Path a = createFile("a/doc.txt", "Alpha bravo charlie delta");
        Path b = createFile("b/doc.txt", "Echo foxtrot golf hotel");
        assertThat(extractor.extract(a)).isNotEqualTo(extractor.extract(b));
    }

    @Test
    void largeTextFileExtractsWithoutError() throws IOException {
        // ~100 KB of text — well under the 1 MB cap
        Path file = createFile("large.txt",
            "The quick brown fox jumps over the lazy dog. ".repeat(2000));
        String result = extractor.extract(file);
        assertThat(result).isNotEmpty();
        assertThat(result).contains("quick brown fox");
    }
}
