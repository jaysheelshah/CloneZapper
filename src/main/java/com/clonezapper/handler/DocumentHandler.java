package com.clonezapper.handler;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/**
 * Near-duplicate handler for documents (PDF, DOCX, PPTX, XLSX).
 * Uses Apache Tika for text extraction + MinHash/LSH for similarity.
 *
 * TODO: Implement using Apache Tika + NearDupService.
 */
@Component
@Order(1)
public class DocumentHandler implements FileTypeHandler {

    @Override
    public boolean canHandle(String mimeType) {
        if (mimeType == null) return false;
        return mimeType.equals("application/pdf")
            || mimeType.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
            || mimeType.equals("application/vnd.openxmlformats-officedocument.presentationml.presentation")
            || mimeType.equals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
            || mimeType.equals("application/msword");
    }

    @Override
    public byte[] computeFingerprint(Path file) {
        throw new UnsupportedOperationException(
            "DocumentHandler not yet implemented — requires Apache Tika + MinHash integration");
    }

    @Override
    public double computeSimilarity(byte[] fingerprintA, byte[] fingerprintB) {
        throw new UnsupportedOperationException(
            "DocumentHandler not yet implemented — requires MinHash similarity");
    }
}
