package com.clonezapper;

import java.util.Random;

/**
 * Shared test data builders and constants.
 */
public final class TestFixtures {

    public static final byte[] IDENTICAL_CONTENT = "identical content for dedup testing".getBytes();
    public static final byte[] CONTENT_A = "this is file content A — unique".getBytes();
    public static final byte[] CONTENT_B = "this is file content B — also unique".getBytes();

    private static final Random RNG = new Random(42); // fixed seed for reproducibility

    private TestFixtures() {}

    /** Generate random bytes of the given size (reproducible via fixed seed). */
    public static byte[] randomContent(int size) {
        byte[] data = new byte[size];
        RNG.nextBytes(data);
        return data;
    }

    /** Filename with a Windows-style numbered copy pattern. */
    public static String numberedCopyName(String base, String ext, int n) {
        return base + " (" + n + ")." + ext;
    }

    /** Filename with a "Copy of" prefix. */
    public static String copyOfName(String filename) {
        return "Copy of " + filename;
    }

    /** Filename with a " - Copy" suffix. */
    public static String copySuffixName(String base, String ext) {
        return base + " - Copy." + ext;
    }
}
