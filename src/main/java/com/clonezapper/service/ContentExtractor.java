package com.clonezapper.service;

import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Extracts plain-text content from documents for near-duplicate detection.
 * Uses Apache Tika's auto-detect parser — handles PDF, DOCX, PPTX, XLSX,
 * HTML, plain text, and many other formats transparently.
 */
@Service
public class ContentExtractor {

    private static final Logger log = LoggerFactory.getLogger(ContentExtractor.class);

    /** Cap extraction at 1 million characters to avoid OOM on huge files. */
    private static final int MAX_CHARS = 1_000_000;

    private final Tika tika;

    public ContentExtractor() {
        this.tika = new Tika();
        this.tika.setMaxStringLength(MAX_CHARS);
    }

    /**
     * Extract plain-text content from the given file.
     * Returns an empty string if the file cannot be parsed (e.g. corrupted,
     * encrypted, or a format Tika does not support) — callers should treat
     * an empty result as "no text available" rather than an error.
     */
    public String extract(Path file) {
        try {
            return tika.parseToString(file.toFile());
        } catch (TikaException | IOException e) {
            log.warn("Text extraction failed for {}: {}", file.getFileName(), e.getMessage());
            return "";
        }
    }
}
