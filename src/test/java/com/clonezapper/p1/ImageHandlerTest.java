package com.clonezapper.p1;

import com.clonezapper.BaseTest;
import com.clonezapper.handler.ImageHandler;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("P1")
class ImageHandlerTest extends BaseTest {

    @Autowired
    ImageHandler handler;

    // ── canHandle ────────────────────────────────────────────────────────────

    @Test
    void canHandleJpeg() {
        assertThat(handler.canHandle("image/jpeg")).isTrue();
    }

    @Test
    void canHandlePng() {
        assertThat(handler.canHandle("image/png")).isTrue();
    }

    @Test
    void doesNotHandlePdf() {
        assertThat(handler.canHandle("application/pdf")).isFalse();
    }

    @Test
    void doesNotHandleNull() {
        assertThat(handler.canHandle(null)).isFalse();
    }

    // ── fingerprint ──────────────────────────────────────────────────────────

    @Test
    void fingerprintHasExpectedLength() throws IOException {
        Path img = createGradientImage("img.png");
        byte[] fp = handler.computeFingerprint(img);
        assertThat(fp).hasSize(ImageHandler.FINGERPRINT_BYTES);
    }

    @Test
    void identicalImagesHaveSimilarityOne() throws IOException {
        Path a = createGradientImage("a.png");
        Path b = createGradientImage("b.png");
        byte[] fpA = handler.computeFingerprint(a);
        byte[] fpB = handler.computeFingerprint(b);
        assertThat(handler.computeSimilarity(fpA, fpB)).isEqualTo(1.0);
    }

    @Test
    void nonImageFileReturnsEmptyFingerprintWithoutThrowing() throws IOException {
        Path txt = createFile("not_an_image.png", "this is not image data");
        byte[] fp = handler.computeFingerprint(txt);
        assertThat(fp).isEmpty();
    }

    @Test
    void emptyFingerprintProducesZeroSimilarity() {
        byte[] empty = new byte[0];
        byte[] real  = new byte[ImageHandler.FINGERPRINT_BYTES];
        assertThat(handler.computeSimilarity(empty, real)).isEqualTo(0.0);
        assertThat(handler.computeSimilarity(real, empty)).isEqualTo(0.0);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /** Horizontal gradient: black on left, white on right. */
    private Path createGradientImage(String name) throws IOException {
        BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < 64; x++) {
            int level = (int) (255.0 * x / 63);
            Color c = new Color(level, level, level);
            for (int y = 0; y < 64; y++) img.setRGB(x, y, c.getRGB());
        }
        Path out = tempDir.resolve(name);
        ImageIO.write(img, "png", out.toFile());
        return out;
    }
}
