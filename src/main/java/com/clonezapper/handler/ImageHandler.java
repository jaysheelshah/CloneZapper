package com.clonezapper.handler;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/**
 * Near-duplicate handler for images (JPEG, PNG, GIF, BMP, WEBP, TIFF).
 * Uses dHash as primary fingerprint, pHash for verification.
 *
 * TODO: Implement using JImageHash library.
 */
@Component
@Order(2)
public class ImageHandler implements FileTypeHandler {

    @Override
    public boolean canHandle(String mimeType) {
        return mimeType != null && mimeType.startsWith("image/");
    }

    @Override
    public byte[] computeFingerprint(Path file) {
        throw new UnsupportedOperationException(
            "ImageHandler not yet implemented — requires JImageHash integration");
    }

    @Override
    public double computeSimilarity(byte[] fingerprintA, byte[] fingerprintB) {
        throw new UnsupportedOperationException(
            "ImageHandler not yet implemented — requires dHash/pHash comparison");
    }
}
