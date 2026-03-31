package com.clonezapper.service;

import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Random;

/**
 * Near-duplicate detection using MinHash signatures.
 * Estimates Jaccard similarity between two sets of text shingles without
 * comparing every element — O(k) per pair where k = NUM_HASHES (128).
 * Algorithm:
 *   For each of the k hash functions h_i(x) = (a_i * x + b_i) mod P,
 *   the signature entry i = min over all shingles s of h_i(hash(s)).
 *   Two signatures agree at position i iff both sets share the same minimum
 *   element under h_i — the fraction of agreements approximates Jaccard.
 */
@Service
public class NearDupService {

    /** Number of hash functions — 128 gives ~1 % estimation error. */
    public static final int NUM_HASHES = 128;

    /**
     * Prime larger than 2^32 so the universal hash family h(x) = (ax+b) mod P
     * distributes well over unsigned 32-bit shingle hashes.
     */
    private static final long LARGE_PRIME = 4_294_967_311L;

    private final long[] a; // multiplier for each hash function
    private final long[] b; // offset  for each hash function

    public NearDupService() {
        // Fixed seed → deterministic signatures across JVM restarts
        Random rng = new Random(0xDEADBEEFL);
        a = new long[NUM_HASHES];
        b = new long[NUM_HASHES];
        for (int i = 0; i < NUM_HASHES; i++) {
            a[i] = (rng.nextInt(Integer.MAX_VALUE)) + 1L; // must be ≥ 1
            b[i] = rng.nextInt(Integer.MAX_VALUE);
        }
    }

    /**
     * Compute a MinHash signature for the given list of shingles (text tokens).
     * Each shingle is hashed with NUM_HASHES independent hash functions and the
     * per-function minimum is recorded.  Returns a byte array of length
     * {@code NUM_HASHES * 8} (one long per hash function).
     * An empty shingle list produces an all-max-value signature, which will
     * compare as 0 % similarity against any real document.
     */
    public byte[] computeSignature(List<String> shingles) {
        long[] minHashes = new long[NUM_HASHES];
        java.util.Arrays.fill(minHashes, Long.MAX_VALUE);

        for (String shingle : shingles) {
            long x = shingle.hashCode() & 0xFFFFFFFFL; // treat as unsigned 32-bit
            for (int i = 0; i < NUM_HASHES; i++) {
                long h = (a[i] * x + b[i]) % LARGE_PRIME;
                if (h < minHashes[i]) {
                    minHashes[i] = h;
                }
            }
        }

        ByteBuffer buf = ByteBuffer.allocate(NUM_HASHES * Long.BYTES);
        for (long v : minHashes) buf.putLong(v);
        return buf.array();
    }

    /**
     * Estimate Jaccard similarity between two MinHash signatures.
     * Returns a value in [0.0, 1.0] where 1.0 means the sets are identical
     * and 0.0 means they are disjoint.
     * Both signatures must have been produced by this service instance
     * (same hash parameters and NUM_HASHES).
     */
    public double estimateSimilarity(byte[] sigA, byte[] sigB) {
        if (sigA.length != sigB.length) {
            throw new IllegalArgumentException(
                "Signature length mismatch: " + sigA.length + " vs " + sigB.length);
        }
        ByteBuffer bufA = ByteBuffer.wrap(sigA);
        ByteBuffer bufB = ByteBuffer.wrap(sigB);
        int matches = 0;
        int count = sigA.length / Long.BYTES;
        for (int i = 0; i < count; i++) {
            if (bufA.getLong() == bufB.getLong()) matches++;
        }
        return (double) matches / count;
    }
}
