package com.clonezapper.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Near-duplicate handler for images (JPEG, PNG, GIF, BMP, WEBP, TIFF).
 * Uses dHash (difference hash):
 *   1. Resize to 9×8 pixels using bilinear interpolation
 *   2. Convert each pixel to grayscale (ITU-R BT.601 luma)
 *   3. For each row, set bit = 1 if left pixel is brighter than right pixel
 *   4. Produces a 64-bit (8-byte) fingerprint
 * Similarity = 1 − (Hamming distance / 64).
 * Images with ≤ 10 differing bits (similarity ≥ 0.84) are typically near-duplicates.
 */
@Component
@Order(2)
public class ImageHandler implements FileTypeHandler {

    private static final Logger log = LoggerFactory.getLogger(ImageHandler.class);

    /** dHash grid: (HASH_WIDTH+1) columns × HASH_HEIGHT rows → HASH_WIDTH×HASH_HEIGHT bits. */
    private static final int HASH_WIDTH  = 8;
    private static final int HASH_HEIGHT = 8;

    /** Fingerprint size in bytes (one bit per comparison = 64 bits = 8 bytes). */
    public static final int FINGERPRINT_BYTES = HASH_WIDTH * HASH_HEIGHT / 8;

    /**
     * Minimum pixel area (width × height) for dHash fingerprinting.
     * Images smaller than 32×32 (1024 pixels) have insufficient spatial detail —
     * when downsampled to 9×8, solid-colour icons and spacers all produce near-zero
     * hashes, making unrelated tiny images appear similar. Returns empty fingerprint
     * for sub-threshold images so callers treat them as "not comparable".
     */
    public static final int MIN_IMAGE_PIXELS = 1024;

    @Override
    public boolean canHandle(String mimeType) {
        return mimeType != null && mimeType.startsWith("image/");
    }

    /**
     * Compute a dHash fingerprint for the image.
     * Returns an empty byte array if the file cannot be decoded (unsupported
     * format, corrupt file) — callers should treat an empty fingerprint as
     * "not comparable".
     */
    @Override
    public byte[] computeFingerprint(Path file) throws IOException {
        BufferedImage image = ImageIO.read(file.toFile());
        if (image == null) {
            log.warn("Could not decode image: {}", file.getFileName());
            return new byte[0];
        }
        if (image.getWidth() * image.getHeight() < MIN_IMAGE_PIXELS) {
            log.debug("Skipping tiny image ({}×{}): {}", image.getWidth(), image.getHeight(), file.getFileName());
            return new byte[0];
        }
        return dHash(image);
    }

    /**
     * Compute similarity from two dHash fingerprints using Hamming distance.
     * Returns 0.0 if either fingerprint is empty or lengths differ.
     */
    @Override
    public double computeSimilarity(byte[] fingerprintA, byte[] fingerprintB) {
        if (fingerprintA.length == 0 || fingerprintB.length == 0) return 0.0;
        if (fingerprintA.length != fingerprintB.length) return 0.0;

        int totalBits = fingerprintA.length * 8;
        int diffBits  = 0;
        for (int i = 0; i < fingerprintA.length; i++) {
            diffBits += Integer.bitCount((fingerprintA[i] ^ fingerprintB[i]) & 0xFF);
        }
        return 1.0 - (double) diffBits / totalBits;
    }

    // ── internals ────────────────────────────────────────────────────────────

    private byte[] dHash(BufferedImage original) {
        // Resize to (HASH_WIDTH+1) × HASH_HEIGHT
        BufferedImage small = new BufferedImage(
            HASH_WIDTH + 1, HASH_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = small.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                           RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(original, 0, 0, HASH_WIDTH + 1, HASH_HEIGHT, null);
        g.dispose();

        // One byte per row; bit set if pixel[col] > pixel[col+1]
        byte[] hash = new byte[FINGERPRINT_BYTES];
        for (int row = 0; row < HASH_HEIGHT; row++) {
            byte bits = 0;
            for (int col = 0; col < HASH_WIDTH; col++) {
                int left  = luma(small.getRGB(col,     row));
                int right = luma(small.getRGB(col + 1, row));
                if (left > right) {
                    bits |= (byte) (1 << (7 - col));
                }
            }
            hash[row] = bits;
        }
        return hash;
    }

    /** ITU-R BT.601 luma — perceptual grayscale from packed ARGB int. */
    private static int luma(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >>  8) & 0xFF;
        int b =  rgb        & 0xFF;
        return (r * 299 + g * 587 + b * 114) / 1000;
    }
}
