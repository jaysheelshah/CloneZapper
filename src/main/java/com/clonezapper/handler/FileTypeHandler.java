package com.clonezapper.handler;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Handles content-aware fingerprinting and similarity for a specific file type.
 * Implementations are registered in {@link HandlerRegistry} via Spring beans.
 */
public interface FileTypeHandler {

    /** Returns true if this handler can process the given MIME type. */
    boolean canHandle(String mimeType);

    /** Compute a content fingerprint for the file (hash, pHash, MinHash, etc.). */
    byte[] computeFingerprint(Path file) throws IOException;

    /**
     * Compute similarity between two fingerprints.
     *
     * @return value in [0.0, 1.0] where 1.0 means identical
     */
    double computeSimilarity(byte[] fingerprintA, byte[] fingerprintB);
}
