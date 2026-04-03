package com.clonezapper.p1;

import com.clonezapper.BaseTest;
import com.clonezapper.TestFixtures;
import com.clonezapper.engine.UnifiedScanner;
import com.clonezapper.model.ScanRun;
import com.clonezapper.model.preview.PreviewSummary;
import com.clonezapper.service.PreviewService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P1 — PreviewService.buildSummary correctness.
 * Each test runs the real pipeline to produce groups, then calls buildSummary
 * and asserts every field in the resulting PreviewSummary record.
 */
@Tag("P1")
class PreviewSummaryTest extends BaseTest {

    @Autowired UnifiedScanner scanner;
    @Autowired PreviewService previewService;

    // ── No duplicates ─────────────────────────────────────────────────────────

    @Test
    void noGroups_allCountsAreZero() throws IOException {
        createFile("a.txt", TestFixtures.CONTENT_A);
        createFile("b.txt", TestFixtures.CONTENT_B);

        ScanRun run = scanner.startScan(List.of(tempDir.toString()));
        PreviewSummary summary = previewService.buildSummary(run.getId());

        assertThat(summary.totalFilesScanned()).isEqualTo(2);
        assertThat(summary.totalGroups()).isZero();
        assertThat(summary.exactGroups()).isZero();
        assertThat(summary.nearDupGroups()).isZero();
        assertThat(summary.totalDupeCount()).isZero();
        assertThat(summary.reclaimableBytes()).isZero();
        assertThat(summary.autoQueueCount()).isZero();
        assertThat(summary.reviewQueueCount()).isZero();
    }

    // ── Single exact-duplicate group ──────────────────────────────────────────

    @Test
    void twoIdenticalFiles_exactGroupCountedCorrectly() throws IOException {
        createFile("orig/report.txt", TestFixtures.IDENTICAL_CONTENT);
        createFile("copy/report.txt", TestFixtures.IDENTICAL_CONTENT);

        ScanRun run = scanner.startScan(List.of(tempDir.toString()));
        PreviewSummary summary = previewService.buildSummary(run.getId());

        assertThat(summary.totalGroups()).isEqualTo(1);
        assertThat(summary.exactGroups()).isEqualTo(1);
        assertThat(summary.nearDupGroups()).isZero();
    }

    @Test
    void twoIdenticalFiles_totalDupeCountIsOne() throws IOException {
        createFile("orig/file.bin", TestFixtures.IDENTICAL_CONTENT);
        createFile("copy/file.bin", TestFixtures.IDENTICAL_CONTENT);

        ScanRun run = scanner.startScan(List.of(tempDir.toString()));
        PreviewSummary summary = previewService.buildSummary(run.getId());

        // 2 members, 1 canonical → 1 dupe
        assertThat(summary.totalDupeCount()).isEqualTo(1);
    }

    @Test
    void twoIdenticalFiles_reclaimableBytesIsFileSizeOfOneCopy() throws IOException {
        createFile("orig/file.bin", TestFixtures.IDENTICAL_CONTENT);
        createFile("copy/file.bin", TestFixtures.IDENTICAL_CONTENT);

        ScanRun run = scanner.startScan(List.of(tempDir.toString()));
        PreviewSummary summary = previewService.buildSummary(run.getId());

        assertThat(summary.reclaimableBytes()).isEqualTo(TestFixtures.IDENTICAL_CONTENT.length);
    }

    @Test
    void exactDuplicate_avgConfidenceIsOne() throws IOException {
        createFile("a/x.dat", TestFixtures.IDENTICAL_CONTENT);
        createFile("b/x.dat", TestFixtures.IDENTICAL_CONTENT);

        ScanRun run = scanner.startScan(List.of(tempDir.toString()));
        PreviewSummary summary = previewService.buildSummary(run.getId());

        assertThat(summary.avgConfidence()).isEqualTo(1.0);
    }

    @Test
    void exactDuplicate_landsInAutoQueueNotReviewQueue() throws IOException {
        createFile("a/x.dat", TestFixtures.IDENTICAL_CONTENT);
        createFile("b/x.dat", TestFixtures.IDENTICAL_CONTENT);

        ScanRun run = scanner.startScan(List.of(tempDir.toString()));
        PreviewSummary summary = previewService.buildSummary(run.getId());

        assertThat(summary.autoQueueCount()).isEqualTo(1);
        assertThat(summary.reviewQueueCount()).isZero();
    }

    // ── Triple group ──────────────────────────────────────────────────────────

    @Test
    void threeIdenticalFiles_totalDupeCountIsTwo() throws IOException {
        createFile("dir1/file.txt", TestFixtures.IDENTICAL_CONTENT);
        createFile("dir2/file.txt", TestFixtures.IDENTICAL_CONTENT);
        createFile("dir3/file.txt", TestFixtures.IDENTICAL_CONTENT);

        ScanRun run = scanner.startScan(List.of(tempDir.toString()));
        PreviewSummary summary = previewService.buildSummary(run.getId());

        // 3 members, 1 canonical → 2 dupes
        assertThat(summary.totalDupeCount()).isEqualTo(2);
        // Reclaimable = 2 × file size
        assertThat(summary.reclaimableBytes())
            .isEqualTo(2L * TestFixtures.IDENTICAL_CONTENT.length);
    }

    // ── Archive root ──────────────────────────────────────────────────────────

    @Test
    void archiveRootIsPopulatedFromScanRun() throws IOException {
        createFile("a.txt", TestFixtures.CONTENT_A);

        ScanRun run = scanner.startScan(List.of(tempDir.toString()));
        PreviewSummary summary = previewService.buildSummary(run.getId());

        // archiveRoot is set on the ScanRun at scan creation time (not null/blank)
        assertThat(summary.archiveRoot()).isNotBlank();
        assertThat(summary.archiveRoot()).isEqualTo(run.getArchiveRoot());
    }

    // ── Total files scanned ───────────────────────────────────────────────────

    @Test
    void totalFilesScanned_matchesActualFileCount() throws IOException {
        createFile("a/1.txt", TestFixtures.CONTENT_A);
        createFile("a/2.txt", TestFixtures.CONTENT_B);
        createFile("a/3.txt", TestFixtures.IDENTICAL_CONTENT);
        createFile("b/3.txt", TestFixtures.IDENTICAL_CONTENT);  // duplicate of above

        ScanRun run = scanner.startScan(List.of(tempDir.toString()));
        PreviewSummary summary = previewService.buildSummary(run.getId());

        assertThat(summary.totalFilesScanned()).isEqualTo(4);
        assertThat(summary.totalGroups()).isEqualTo(1);
    }
}
