package com.clonezapper.p1;

import com.clonezapper.BaseTest;
import com.clonezapper.handler.DocumentHandler;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("P1")
class DocumentHandlerTest extends BaseTest {

    @Autowired
    DocumentHandler handler;

    // ── canHandle ────────────────────────────────────────────────────────────

    @Test
    void canHandlePdf() {
        assertThat(handler.canHandle("application/pdf")).isTrue();
    }

    @Test
    void canHandleDocx() {
        assertThat(handler.canHandle(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document")).isTrue();
    }

    @Test
    void canHandlePptx() {
        assertThat(handler.canHandle(
            "application/vnd.openxmlformats-officedocument.presentationml.presentation")).isTrue();
    }

    @Test
    void canHandleXlsx() {
        assertThat(handler.canHandle(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).isTrue();
    }

    @Test
    void doesNotHandleImages() {
        assertThat(handler.canHandle("image/jpeg")).isFalse();
    }

    @Test
    void doesNotHandlePlainText() {
        assertThat(handler.canHandle("text/plain")).isFalse();
    }

    @Test
    void doesNotHandleNull() {
        assertThat(handler.canHandle(null)).isFalse();
    }

    // ── fingerprint + similarity ─────────────────────────────────────────────

    @Test
    void identicalFilesHaveSimilarityOne() throws IOException {
        String text = "The quick brown fox jumps over the lazy dog near the river bank.";
        Path a = createFile("a.txt", text);
        Path b = createFile("b.txt", text);
        byte[] sigA = handler.computeFingerprint(a);
        byte[] sigB = handler.computeFingerprint(b);
        assertThat(handler.computeSimilarity(sigA, sigB)).isEqualTo(1.0);
    }

    @Test
    void totallyDifferentFilesHaveLowSimilarity() throws IOException {
        Path a = createFile("a.txt",
            "alpha bravo charlie delta echo foxtrot golf hotel india juliet");
        Path b = createFile("b.txt",
            "kilo lima mike november oscar papa quebec romeo sierra tango");
        byte[] sigA = handler.computeFingerprint(a);
        byte[] sigB = handler.computeFingerprint(b);
        assertThat(handler.computeSimilarity(sigA, sigB)).isLessThan(0.2);
    }

    @Test
    void emptyFileProducesFingerprintWithoutThrowing() throws IOException {
        Path file = createFile("empty.txt", "");
        byte[] sig = handler.computeFingerprint(file);
        assertThat(sig).isNotNull().isNotEmpty();
    }
}
