package com.clonezapper.p1;

import com.clonezapper.BaseTest;
import com.clonezapper.service.ContentExtractor;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("P1")
class ContentExtractorTest extends BaseTest {

    @Autowired
    ContentExtractor extractor;

    @Test
    void plainTextFileReturnsContent() throws IOException {
        Path file = createFile("hello.txt", "The quick brown fox jumps over the lazy dog.");
        String result = extractor.extract(file);
        assertThat(result).contains("quick brown fox");
    }

    @Test
    void htmlFileReturnsTextWithoutTags() throws IOException {
        Path file = createFile("page.html",
            "<html><body><h1>Title</h1><p>Some content here.</p></body></html>");
        String result = extractor.extract(file);
        assertThat(result).contains("Title");
        assertThat(result).contains("Some content here");
        assertThat(result).doesNotContain("<h1>");
    }

    @Test
    void emptyFileReturnsEmptyString() throws IOException {
        Path file = createFile("empty.txt", "");
        String result = extractor.extract(file);
        assertThat(result).isEmpty();
    }

    @Test
    void binaryFileDoesNotThrow() throws IOException {
        // Random bytes — Tika may return empty or partial text, but must not throw
        byte[] bytes = {0x00, 0x01, 0x02, (byte) 0xFF, (byte) 0xFE, 0x00};
        Path file = createFile("binary.bin", bytes);
        String result = extractor.extract(file);
        assertThat(result).isNotNull(); // just no exception
    }
}
