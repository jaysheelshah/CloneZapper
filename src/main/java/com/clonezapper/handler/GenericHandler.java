package com.clonezapper.handler;

import com.clonezapper.service.HashService;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * Fallback handler for any file type.
 * Uses full SHA-256 hash for fingerprinting — two files are identical (1.0)
 * or completely different (0.0), no fuzzy matching.
 *
 * Registered last ({@code @Order(Integer.MAX_VALUE)}) so specific handlers
 * always take priority.
 */
@Component
@Order(Integer.MAX_VALUE)
public class GenericHandler implements FileTypeHandler {

    private final HashService hashService;

    public GenericHandler(HashService hashService) {
        this.hashService = hashService;
    }

    @Override
    public boolean canHandle(String mimeType) {
        return true; // accepts everything
    }

    @Override
    public byte[] computeFingerprint(Path file) throws IOException {
        String hex = hashService.computeFullHash(file);
        return hex.getBytes();
    }

    @Override
    public double computeSimilarity(byte[] fingerprintA, byte[] fingerprintB) {
        return Arrays.equals(fingerprintA, fingerprintB) ? 1.0 : 0.0;
    }
}
