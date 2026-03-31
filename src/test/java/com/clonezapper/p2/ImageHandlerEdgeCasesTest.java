package com.clonezapper.p2;

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

@Tag("P2")
class ImageHandlerEdgeCasesTest extends BaseTest {

    @Autowired
    ImageHandler handler;

    @Test
    void invertedGradientHasLowSimilarityToOriginal() throws IOException {
        // Gradient black→white vs white→black — opposite brightness order
        // produces maximally different dHash bits
        Path a = createGradientImage("a.png", false);
        Path b = createGradientImage("b.png", true);
        double sim = handler.computeSimilarity(
            handler.computeFingerprint(a),
            handler.computeFingerprint(b));
        assertThat(sim).isLessThan(0.3);
    }

    @Test
    void nearlyIdenticalImagesHaveHighSimilarity() throws IOException {
        // Same gradient with a small brightness shift — still very similar after downscale
        Path a = createGradientImage("a.png", false);
        Path b = createShiftedGradientImage("b.png");
        double sim = handler.computeSimilarity(
            handler.computeFingerprint(a),
            handler.computeFingerprint(b));
        assertThat(sim).isGreaterThan(0.7);
    }

    @Test
    void fingerprintIsDeterministic() throws IOException {
        Path img = createGradientImage("img.png", false);
        byte[] first  = handler.computeFingerprint(img);
        byte[] second = handler.computeFingerprint(img);
        assertThat(first).isEqualTo(second);
    }

    @Test
    void lengthMismatchReturnsZeroSimilarity() {
        byte[] a = new byte[ImageHandler.FINGERPRINT_BYTES];
        byte[] b = new byte[4];
        assertThat(handler.computeSimilarity(a, b)).isEqualTo(0.0);
    }

    @Test
    void canHandleAllCommonImageMimeTypes() {
        for (String mime : new String[]{"image/png", "image/jpeg", "image/gif",
                                        "image/bmp", "image/webp", "image/tiff"}) {
            assertThat(handler.canHandle(mime))
                .as("expected true for " + mime).isTrue();
        }
    }

    @Test
    void largeImageProducesSameLengthFingerprint() throws IOException {
        // 1024×768 — should still produce 8-byte dHash
        BufferedImage img = new BufferedImage(1024, 768, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.BLUE);
        g.fillRect(0, 0, 512, 768);
        g.setColor(Color.RED);
        g.fillRect(512, 0, 512, 768);
        g.dispose();
        Path out = tempDir.resolve("large.png");
        ImageIO.write(img, "png", out.toFile());

        byte[] fp = handler.computeFingerprint(out);
        assertThat(fp).hasSize(ImageHandler.FINGERPRINT_BYTES);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Path createGradientImage(String name, boolean invert) throws IOException {
        BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < 64; x++) {
            int level = (int) (255.0 * x / 63);
            if (invert) level = 255 - level;
            Color c = new Color(level, level, level);
            for (int y = 0; y < 64; y++) img.setRGB(x, y, c.getRGB());
        }
        Path out = tempDir.resolve(name);
        ImageIO.write(img, "png", out.toFile());
        return out;
    }

    /** Same gradient with a +10 brightness shift — visually almost identical. */
    private Path createShiftedGradientImage(String name) throws IOException {
        BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < 64; x++) {
            int level = Math.min(255, (int) (255.0 * x / 63) + 10);
            Color c = new Color(level, level, level);
            for (int y = 0; y < 64; y++) img.setRGB(x, y, c.getRGB());
        }
        Path out = tempDir.resolve(name);
        ImageIO.write(img, "png", out.toFile());
        return out;
    }
}
