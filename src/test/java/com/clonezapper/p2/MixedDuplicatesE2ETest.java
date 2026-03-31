package com.clonezapper.p2;

import com.clonezapper.BaseTest;
import com.clonezapper.TestFixtures;
import com.clonezapper.db.DuplicateGroupRepository;
import com.clonezapper.engine.UnifiedScanner;
import com.clonezapper.engine.pipeline.ClusterStage;
import com.clonezapper.model.DuplicateGroup;
import com.clonezapper.model.ScanRun;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2E: scans that contain both exact duplicates and near-duplicates in the same run.
 * Verifies that exact groups land in auto-queue and near-dup groups land in review-queue.
 */
@Tag("P2")
class MixedDuplicatesE2ETest extends BaseTest {

    @Autowired UnifiedScanner scanner;
    @Autowired DuplicateGroupRepository groupRepository;

    private static final String SHARED_BODY =
        "This document contains several sentences that are nearly identical across versions. " +
        "The financial results for the quarter exceeded all analyst expectations by a wide margin. " +
        "Customer retention rates improved significantly due to the new loyalty programme initiatives. " +
        "Research and development expenditure was increased to support long-term product roadmap goals.";

    @Test
    void exactAndNearDupGroupsCoexistInSameScan() throws IOException {
        // Exact pair (binary content)
        createFile("exact/report_orig.dat", TestFixtures.IDENTICAL_CONTENT);
        createFile("exact/report_copy.dat", TestFixtures.IDENTICAL_CONTENT);

        // Near-dup HTML pair
        createFile("near/doc_a.html",
            "<html><body><p>" + SHARED_BODY + " Version A conclusion.</p></body></html>");
        createFile("near/doc_b.html",
            "<html><body><p>" + SHARED_BODY + " Version B conclusion.</p></body></html>");

        // Unique file — should not be grouped
        createFile("unique.txt", TestFixtures.CONTENT_A);

        ScanRun run = scanner.startScan(List.of(tempDir.toString()));
        assertThat(run.getPhase()).isEqualTo("COMPLETE");

        List<DuplicateGroup> allGroups = groupRepository.findByScanId(run.getId());
        assertThat(allGroups).hasSizeGreaterThanOrEqualTo(2);

        // Exact group: confidence == 1.0
        long exactCount = allGroups.stream()
            .filter(g -> g.getConfidence() == 1.0).count();
        assertThat(exactCount).isGreaterThanOrEqualTo(1);

        // Near-dup group: confidence < 1.0
        long nearDupCount = allGroups.stream()
            .filter(g -> g.getConfidence() < 1.0).count();
        assertThat(nearDupCount).isGreaterThanOrEqualTo(1);
    }

    @Test
    void autoQueueContainsOnlyExactMatches() throws IOException {
        createFile("exact/a.dat", TestFixtures.IDENTICAL_CONTENT);
        createFile("exact/b.dat", TestFixtures.IDENTICAL_CONTENT);

        createFile("near/a.html",
            "<html><body><p>" + SHARED_BODY + " End A.</p></body></html>");
        createFile("near/b.html",
            "<html><body><p>" + SHARED_BODY + " End B.</p></body></html>");

        ScanRun run = scanner.startScan(List.of(tempDir.toString()));

        // Auto queue = confidence >= threshold
        List<DuplicateGroup> allGroups = groupRepository.findByScanId(run.getId());
        List<DuplicateGroup> reviewQueue = groupRepository.findReviewQueueByScanId(
            run.getId(), ClusterStage.DEFAULT_CONFIDENCE_THRESHOLD);

        // All review-queue groups have confidence < threshold
        reviewQueue.forEach(g ->
            assertThat(g.getConfidence()).isLessThan(ClusterStage.DEFAULT_CONFIDENCE_THRESHOLD));

        // All non-review groups have confidence >= threshold
        allGroups.stream()
            .filter(g -> !reviewQueue.contains(g))
            .forEach(g ->
                assertThat(g.getConfidence())
                    .isGreaterThanOrEqualTo(ClusterStage.DEFAULT_CONFIDENCE_THRESHOLD));
    }

    @Test
    void uniqueFilesAreNeverGrouped() throws IOException {
        createFile("exact/a.dat", TestFixtures.IDENTICAL_CONTENT);
        createFile("exact/b.dat", TestFixtures.IDENTICAL_CONTENT);
        createFile("unique_a.txt", TestFixtures.CONTENT_A);
        createFile("unique_b.txt", TestFixtures.CONTENT_B);

        ScanRun run = scanner.startScan(List.of(tempDir.toString()));
        assertThat(run.getPhase()).isEqualTo("COMPLETE");

        List<DuplicateGroup> groups = groupRepository.findByScanId(run.getId());
        // Only the exact pair; both unique files are untouched
        assertThat(groups).hasSize(1);
        assertThat(groups.getFirst().getMembers()).hasSize(2);
    }
}
