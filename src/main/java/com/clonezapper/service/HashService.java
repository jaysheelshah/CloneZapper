package com.clonezapper.service;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Computes file hashes for deduplication.
 *
 * Currently uses SHA-256 as a placeholder.
 * TODO: Replace with BLAKE3 (via https://github.com/sken77/BLAKE3j) for production.
 */
@Service
public class HashService {

    private static final int PARTIAL_HASH_BYTES = 4 * 1024; // 4 KB
    private static final String ALGORITHM = "SHA-256";

    /** Hash of the first 4 KB — cheap candidate filter. */
    public String computePartialHash(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[PARTIAL_HASH_BYTES];
            int bytesRead = in.read(buffer, 0, PARTIAL_HASH_BYTES);
            if (bytesRead <= 0) return hashBytes(new byte[0]);
            byte[] partial = new byte[bytesRead];
            System.arraycopy(buffer, 0, partial, 0, bytesRead);
            return hashBytes(partial);
        }
    }

    /** Full-file hash — used for exact duplicate confirmation. */
    public String computeFullHash(Path file) throws IOException {
        MessageDigest digest = newDigest();
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[8 * 1024];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
        }
        return toHex(digest.digest());
    }

    private String hashBytes(byte[] data) {
        MessageDigest digest = newDigest();
        digest.update(data);
        return toHex(digest.digest());
    }

    private MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance(ALGORITHM);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
