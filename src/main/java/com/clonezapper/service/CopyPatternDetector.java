package com.clonezapper.service;

import java.util.regex.Pattern;

/**
 * Detects OS-generated copy-naming patterns in filenames.
 * Returns a copy_hint string if a pattern is matched, null otherwise.
 */
public class CopyPatternDetector {

    public static final String HINT_EXPLICIT_COPY = "explicit_copy";

    // Matches: "file (1).pdf", "file (2).txt", etc.
    private static final Pattern NUMBERED_COPY =
        Pattern.compile("^.+\\s\\(\\d+\\)(\\.\\w+)?$");

    // Matches: "Copy of file.pdf", "copy of file.pdf"
    private static final Pattern COPY_OF_PREFIX =
        Pattern.compile("^[Cc]opy\\s+of\\s+.+$");

    // Matches: "file - Copy.pdf", "file - Copy (2).pdf"
    private static final Pattern COPY_SUFFIX =
        Pattern.compile("^.+\\s-\\s[Cc]opy(\\s\\(\\d+\\))?(\\.\\w+)?$");

    private CopyPatternDetector() {}

    /**
     * Returns {@code HINT_EXPLICIT_COPY} if the filename matches a known copy pattern,
     * {@code null} otherwise.
     */
    public static String detect(String filename) {
        if (filename == null || filename.isBlank()) return null;
        if (NUMBERED_COPY.matcher(filename).matches()) return HINT_EXPLICIT_COPY;
        if (COPY_OF_PREFIX.matcher(filename).matches()) return HINT_EXPLICIT_COPY;
        if (COPY_SUFFIX.matcher(filename).matches()) return HINT_EXPLICIT_COPY;
        return null;
    }
}
