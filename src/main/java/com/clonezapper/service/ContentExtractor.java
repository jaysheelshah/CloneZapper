package com.clonezapper.service;

import org.springframework.stereotype.Service;

import java.nio.file.Path;

/**
 * Extracts text content from documents for near-duplicate detection.
 *
 * TODO: Implement using Apache Tika + PDFBox + Apache POI + Tess4j.
 */
@Service
public class ContentExtractor {

    /**
     * Extract plain-text content from the given file.
     *
     * @throws UnsupportedOperationException until implemented
     */
    public String extract(Path file) {
        throw new UnsupportedOperationException(
            "ContentExtractor not yet implemented — requires Apache Tika integration");
    }
}
