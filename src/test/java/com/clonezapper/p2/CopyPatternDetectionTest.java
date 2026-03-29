package com.clonezapper.p2;

import com.clonezapper.service.CopyPatternDetector;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P2 — Copy-naming pattern detection.
 * CopyPatternDetector is fully implemented so these tests run without stubs.
 */
@Tag("P2")
class CopyPatternDetectionTest {

    @Test
    void detectsNumberedCopyPattern() {
        assertThat(CopyPatternDetector.detect("report (1).pdf")).isEqualTo("explicit_copy");
        assertThat(CopyPatternDetector.detect("photo (2).jpg")).isEqualTo("explicit_copy");
        assertThat(CopyPatternDetector.detect("budget (10).xlsx")).isEqualTo("explicit_copy");
    }

    @Test
    void detectsCopyOfPrefix() {
        assertThat(CopyPatternDetector.detect("Copy of report.pdf")).isEqualTo("explicit_copy");
        assertThat(CopyPatternDetector.detect("copy of notes.txt")).isEqualTo("explicit_copy");
    }

    @Test
    void detectsCopySuffix() {
        assertThat(CopyPatternDetector.detect("report - Copy.pdf")).isEqualTo("explicit_copy");
        assertThat(CopyPatternDetector.detect("budget - Copy (2).xlsx")).isEqualTo("explicit_copy");
    }

    @Test
    void normalFilenamesReturnNull() {
        assertThat(CopyPatternDetector.detect("report.pdf")).isNull();
        assertThat(CopyPatternDetector.detect("annual-report-2024.docx")).isNull();
        assertThat(CopyPatternDetector.detect("photo_001.jpg")).isNull();
    }

    @Test
    void nullAndBlankReturnNull() {
        assertThat(CopyPatternDetector.detect(null)).isNull();
        assertThat(CopyPatternDetector.detect("")).isNull();
        assertThat(CopyPatternDetector.detect("   ")).isNull();
    }
}
