package com.clonezapper.service;

import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Near-duplicate detection using MinHash + LSH with TF-IDF cosine verification.
 *
 * TODO: Implement using datasketch or a JVM MinHash library.
 */
@Service
public class NearDupService {

    /**
     * Compute a MinHash signature for the given shingles (text tokens).
     *
     * @throws UnsupportedOperationException until implemented
     */
    public byte[] computeSignature(List<String> shingles) {
        throw new UnsupportedOperationException(
            "NearDupService not yet implemented — requires MinHash/LSH integration");
    }

    /**
     * Estimate Jaccard similarity between two MinHash signatures.
     *
     * @throws UnsupportedOperationException until implemented
     */
    public double estimateSimilarity(byte[] sigA, byte[] sigB) {
        throw new UnsupportedOperationException(
            "NearDupService not yet implemented — requires MinHash/LSH integration");
    }
}
