package com.clonezapper.p1;

import com.clonezapper.BaseTest;
import com.clonezapper.TestFixtures;
import com.clonezapper.engine.UnifiedScanner;
import com.clonezapper.model.ScanRun;
import com.clonezapper.model.preview.GroupPreviewRow;
import com.clonezapper.service.PreviewService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P1 — PreviewService.buildGroups correctness.
 * Verifies per-group row fields: counts, bytes, canonical path, strategy, sort order.
 */
@Tag("P1")
class PreviewGroupsTest extends BaseTest {

    @Autowired UnifiedScanner scanner;
    @Autowired PreviewService previewService;

    @Test
    void noGroups_returnsEmptyList() throws IOException {
        createFile("a.txt", TestFixtures.CONTENT_A);
        createFile("b.txt", TestFixtures.CONTENT_B);

        ScanRun run = scanner.startScan(List.of(tempDir.toString()));
        List<GroupPreviewRow> groups = previewService.buildGroups(run.getId());

        assertThat(groups).isEmpty();
    }

    @Test
    void oneExactGroup_returnsOneRow() throws IOException {
        createFile("orig/report.txt", TestFixtures.IDENTICAL_CONTENT);
        createFile("copy/report.txt", TestFixtures.IDENTICAL_CONTENT);

        ScanRun run = scanner.startScan(List.of(tempDir.toString()));
        List<GroupPreviewRow> groups = previewService.buildGroups(run.getId());

        assertThat(groups).hasSize(1);
    }

    @Test
    void groupRow_strategyIsExactHash() throws IOException {
        createFile("a/f.bin", TestFixtures.IDENTICAL_CONTENT);
        createFile("b/f.bin", TestFixtures.IDENTICAL_CONTENT);

        ScanRun run = scanner.startScan(List.of(tempDir.toString()));
        GroupPreviewRow row = previewService.buildGroups(run.getId()).getFirst();

        assertThat(row.strategy()).isEqualTo("exact-hash");
    }

    @Test
    void groupRow_confidenceIsOneForExactMatch() throws IOException {
        createFile("a/f.bin", TestFixtures.IDENTICAL_CONTENT);
        createFile("b/f.bin", TestFixtures.IDENTICAL_CONTENT);

        ScanRun run = scanner.startScan(List.of(tempDir.toString()));
        GroupPreviewRow row = previewService.buildGroups(run.getId()).getFirst();

        assertThat(row.confidence()).isEqualTo(1.0);
    }

    @Test
    void groupRow_memberCountAndDupeCountCorrect() throws IOException {
        createFile("dir1/f.txt", TestFixtures.IDENTICAL_CONTENT);
        createFile("dir2/f.txt", TestFixtures.IDENTICAL_CONTENT);
        createFile("dir3/f.txt", TestFixtures.IDENTICAL_CONTENT);

        ScanRun run = scanner.startScan(List.of(tempDir.toString()));
        GroupPreviewRow row = previewService.buildGroups(run.getId()).getFirst();

        assertThat(row.memberCount()).isEqualTo(3);
        assertThat(row.dupeCount()).isEqualTo(2);          // memberCount - 1
    }

    @Test
    void groupRow_reclaimableBytesExcludesCanonical() throws IOException {
        createFile("a/f.txt", TestFixtures.IDENTICAL_CONTENT);
        createFile("b/f.txt", TestFixtures.IDENTICAL_CONTENT);

        ScanRun run = scanner.startScan(List.of(tempDir.toString()));
        GroupPreviewRow row = previewService.buildGroups(run.getId()).getFirst();

        // 2 files of identical size; reclaimable = 1 × fileSize, total = 2 × fileSize
        long fileSize = TestFixtures.IDENTICAL_CONTENT.length;
        assertThat(row.reclaimableBytes()).isEqualTo(fileSize);
        assertThat(row.totalBytes()).isEqualTo(fileSize * 2);
    }

    @Test
    void groupRow_canonicalPathIsNotBlank() throws IOException {
        createFile("orig/data.bin", TestFixtures.IDENTICAL_CONTENT);
        createFile("copy/data.bin", TestFixtures.IDENTICAL_CONTENT);

        ScanRun run = scanner.startScan(List.of(tempDir.toString()));
        GroupPreviewRow row = previewService.buildGroups(run.getId()).getFirst();

        assertThat(row.canonicalPath()).isNotBlank();
    }

    @Test
    void groupRow_membersListIsEmptyUntilBuildMembersIsCalled() throws IOException {
        createFile("a/f.bin", TestFixtures.IDENTICAL_CONTENT);
        createFile("b/f.bin", TestFixtures.IDENTICAL_CONTENT);

        ScanRun run = scanner.startScan(List.of(tempDir.toString()));
        GroupPreviewRow row = previewService.buildGroups(run.getId()).getFirst();

        // members are lazy — buildGroups leaves the list empty
        assertThat(row.members()).isEmpty();
    }

    @Test
    void multipleGroups_sortedByConfidenceAscending() throws IOException {
        // Group 1: exact match (confidence = 1.0)
        createFile("exact/a.dat", TestFixtures.IDENTICAL_CONTENT);
        createFile("exact/b.dat", TestFixtures.IDENTICAL_CONTENT);

        // Group 2: near-dup HTML (confidence < 1.0)
        String body = "The quarterly earnings report showed significant improvement across all divisions. "
            + "Revenue growth in the consumer segment exceeded management guidance for the third consecutive quarter.";
        createFile("near/doc_a.html", "<html><body>" + body + " Appendix A.</body></html>");
        createFile("near/doc_b.html", "<html><body>" + body + " Appendix B.</body></html>");

        ScanRun run = scanner.startScan(List.of(tempDir.toString()));
        List<GroupPreviewRow> groups = previewService.buildGroups(run.getId());

        assertThat(groups.size()).isGreaterThanOrEqualTo(2);

        // buildGroups sorts by confidence ASC → lowest-confidence (near-dup) comes first
        for (int i = 0; i < groups.size() - 1; i++) {
            assertThat(groups.get(i).confidence())
                .isLessThanOrEqualTo(groups.get(i + 1).confidence());
        }
    }
}
