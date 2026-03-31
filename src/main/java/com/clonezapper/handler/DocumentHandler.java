package com.clonezapper.handler;

import com.clonezapper.service.ContentExtractor;
import com.clonezapper.service.NearDupService;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Near-duplicate handler for documents (PDF, DOCX, PPTX, XLSX).
 * Pipeline:
 *   1. Extract plain text via Apache Tika (ContentExtractor)
 *   2. Normalise and tokenise into word bigram shingles
 *   3. Compute MinHash signature (NearDupService)
 * Fingerprint = MinHash signature byte[] (1024 bytes, 128 longs).
 * Similarity  = Jaccard estimate from signature comparison.
 */
@Component
@Order(1)
public class DocumentHandler implements FileTypeHandler {

    private final ContentExtractor contentExtractor;
    private final NearDupService nearDupService;

    public DocumentHandler(ContentExtractor contentExtractor, NearDupService nearDupService) {
        this.contentExtractor = contentExtractor;
        this.nearDupService   = nearDupService;
    }

    @Override
    public boolean canHandle(String mimeType) {
        if (mimeType == null) return false;
        return mimeType.equals("application/pdf")
            || mimeType.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
            || mimeType.equals("application/vnd.openxmlformats-officedocument.presentationml.presentation")
            || mimeType.equals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
            || mimeType.equals("application/msword")
            || mimeType.equals("text/html");
    }

    /**
     * Extract text from the document, shingle it, and return a MinHash signature.
     * If the document produces no text (encrypted, scanned image, empty), the
     * signature is all-max-value — it will compare as 0 % similar to anything.
     */
    @Override
    public byte[] computeFingerprint(Path file) throws IOException {
        String text = contentExtractor.extract(file);
        List<String> shingles = shingle(text);
        return nearDupService.computeSignature(shingles);
    }

    @Override
    public double computeSimilarity(byte[] fingerprintA, byte[] fingerprintB) {
        return nearDupService.estimateSimilarity(fingerprintA, fingerprintB);
    }

    /**
     * Tokenise text into word bigrams (2-shingles).
     * Bigrams capture local word order, which makes near-dup detection more
     * precise than single-word (unigram) shingles for prose documents.
     * "the quick brown fox" → ["the quick", "quick brown", "brown fox"]
     * Falls back to unigrams when the text has fewer than two words.
     */
    public List<String> shingle(String text) {
        if (text == null || text.isBlank()) return List.of();

        String normalised = text.toLowerCase().replaceAll("[^a-z0-9]+", " ").trim();
        String[] words = normalised.split("\\s+");

        if (words.length == 0) return List.of();
        if (words.length == 1) return List.of(words[0]);

        List<String> shingles = new ArrayList<>(words.length - 1);
        for (int i = 0; i < words.length - 1; i++) {
            shingles.add(words[i] + " " + words[i + 1]);
        }
        return shingles;
    }
}
