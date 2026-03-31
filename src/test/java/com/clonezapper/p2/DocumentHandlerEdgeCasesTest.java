package com.clonezapper.p2;

import com.clonezapper.BaseTest;
import com.clonezapper.handler.DocumentHandler;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("P2")
class DocumentHandlerEdgeCasesTest extends BaseTest {

    @Autowired
    DocumentHandler handler;

    @Test
    void nearIdenticalDocumentsHaveHighSimilarity() throws IOException {
        // One sentence different out of a long paragraph
        String base = "The annual financial report shows strong growth across all divisions. "
            + "Revenue increased by fifteen percent compared to the previous year. "
            + "Operating margins improved due to cost reduction initiatives. "
            + "The board approved a dividend increase for shareholders. "
            + "Outlook for next year remains positive with projected double digit growth.";
        String modified = base.replace("fifteen percent", "twelve percent");

        Path a = createFile("report_a.txt", base);
        Path b = createFile("report_b.txt", modified);
        double sim = handler.computeSimilarity(
            handler.computeFingerprint(a),
            handler.computeFingerprint(b));
        assertThat(sim).isGreaterThan(0.7);
    }

    @Test
    void shingleSingleWord() {
        List<String> shingles = handler.shingle("hello");
        assertThat(shingles).containsExactly("hello");
    }

    @Test
    void shingleBigrams() {
        List<String> shingles = handler.shingle("one two three");
        assertThat(shingles).containsExactly("one two", "two three");
    }

    @Test
    void shingleNormalisesCase() {
        List<String> upper = handler.shingle("HELLO WORLD");
        List<String> lower = handler.shingle("hello world");
        assertThat(upper).isEqualTo(lower);
    }

    @Test
    void shingleStripsNonAlphanumeric() {
        List<String> shingles = handler.shingle("hello, world!");
        assertThat(shingles).containsExactly("hello world");
    }

    @Test
    void shingleEmptyStringReturnsEmpty() {
        assertThat(handler.shingle("")).isEmpty();
        assertThat(handler.shingle("   ")).isEmpty();
        assertThat(handler.shingle(null)).isEmpty();
    }

    @Test
    void fingerprintIsDeterministic() throws IOException {
        String text = "Deterministic fingerprinting is required for stable dedup.";
        Path file = createFile("doc.txt", text);
        byte[] first  = handler.computeFingerprint(file);
        byte[] second = handler.computeFingerprint(file);
        assertThat(first).isEqualTo(second);
    }

    @Test
    void caseInsensitiveMatchProducesHighSimilarity() throws IOException {
        Path a = createFile("a.txt", "The Quick Brown Fox Jumps Over The Lazy Dog");
        Path b = createFile("b.txt", "the quick brown fox jumps over the lazy dog");
        double sim = handler.computeSimilarity(
            handler.computeFingerprint(a),
            handler.computeFingerprint(b));
        assertThat(sim).isEqualTo(1.0);
    }
}
