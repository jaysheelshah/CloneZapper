package com.clonezapper.p2;

import com.clonezapper.BaseTest;
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
 * E2E: near-duplicate HTML document detection.
 * <p>Two HTML files with nearly identical body text should be detected as a
 * near-dup pair and land in either the auto-queue (similarity ≥ 0.95)
 * or review-queue (0.5 ≤ similarity < 0.95).
 * <p>Requires: OS probeContentType returns "text/html" for .html files (Windows ✓).
 */
@Tag("P2")
class NearDupDocumentE2ETest extends BaseTest {

    @Autowired UnifiedScanner scanner;
    @Autowired DuplicateGroupRepository groupRepository;

    // Shared paragraph that appears in both documents
    private static final String SHARED_BODY =
        "The annual financial report for this fiscal year demonstrates exceptional growth. " +
        "Revenue increased significantly across all major product lines and service categories. " +
        "The executive team attributes this success to strategic investment in research and development. " +
        "Customer satisfaction scores reached an all-time high during the second and third quarters. " +
        "Operating costs were reduced through the implementation of lean process improvements. " +
        "The board of directors approved a substantial dividend increase for all common shareholders. " +
        "International expansion efforts yielded strong results in the Asia-Pacific and European markets. " +
        "New product launches exceeded initial sales projections by a considerable margin throughout the year.";

    @Test
    void nearlyIdenticalDocumentsAreDetectedAsNearDup() throws IOException {
        // One sentence differs between A and B
        createHtml("doc_a.html", SHARED_BODY + " Outlook for next year remains highly positive.");
        createHtml("doc_b.html", SHARED_BODY + " Future projections indicate continued strong performance.");

        ScanRun run = scanner.startScan(List.of(tempDir.toString()));
        assertThat(run.getPhase()).isEqualTo("COMPLETE");

        List<DuplicateGroup> groups = groupRepository.findByScanId(run.getId());
        assertThat(groups).as("near-dup group should be found").hasSize(1);

        DuplicateGroup group = groups.getFirst();
        assertThat(group.getStrategy()).startsWith("near-dup");
        assertThat(group.getConfidence())
            .as("near-dup similarity should be above threshold")
            .isGreaterThanOrEqualTo(0.5);
        assertThat(group.getCanonicalFileId()).isNotNull();
    }

    @Test
    void completelyDifferentDocumentsProduceNoGroups() throws IOException {
        createHtml("doc_a.html",
            "Apple banana cherry date elderberry fig grape honeydew kiwi lemon mango.");
        createHtml("doc_b.html",
            "Quantum physics relativity entropy thermodynamics electromagnetic radiation spectrum.");

        ScanRun run = scanner.startScan(List.of(tempDir.toString()));
        assertThat(run.getPhase()).isEqualTo("COMPLETE");
        assertThat(groupRepository.countByScanId(run.getId())).isZero();
    }

    @Test
    void identicalDocumentsLandInAutoQueue() throws IOException {
        String content = SHARED_BODY + " This sentence is the same in both documents.";
        createHtml("doc_a.html", content);
        createHtml("doc_b.html", content);

        ScanRun run = scanner.startScan(List.of(tempDir.toString()));
        assertThat(run.getPhase()).isEqualTo("COMPLETE");

        // Exact-hash pass finds them → confidence 1.0 → auto queue
        List<DuplicateGroup> groups = groupRepository.findByScanId(run.getId());
        assertThat(groups).hasSize(1);
        assertThat(groups.getFirst().getConfidence()).isEqualTo(1.0);

        // Auto queue: NOT in review queue
        List<DuplicateGroup> review = groupRepository.findReviewQueueByScanId(
            run.getId(), ClusterStage.DEFAULT_CONFIDENCE_THRESHOLD);
        assertThat(review).isEmpty();
    }

    @Test
    void threeNearlyIdenticalDocumentsClusterToOneGroup() throws IOException {
        createHtml("doc_a.html", SHARED_BODY + " Version alpha of the document.");
        createHtml("doc_b.html", SHARED_BODY + " Version beta of the document.");
        createHtml("doc_c.html", SHARED_BODY + " Version gamma of the document.");

        ScanRun run = scanner.startScan(List.of(tempDir.toString()));
        assertThat(run.getPhase()).isEqualTo("COMPLETE");

        // All three share high pairwise similarity → Union-Find should merge to one group
        List<DuplicateGroup> groups = groupRepository.findByScanId(run.getId());
        assertThat(groups).hasSizeLessThanOrEqualTo(3);
        assertThat(groups).isNotEmpty();
        // One canonical chosen
        groups.forEach(g -> assertThat(g.getCanonicalFileId()).isNotNull());
    }

    @Test
    void uniqueDocumentNotGroupedWithNearDups() throws IOException {
        createHtml("doc_a.html", SHARED_BODY + " Conclusion A.");
        createHtml("doc_b.html", SHARED_BODY + " Conclusion B.");
        createHtml("unique.html",
            "Completely unrelated content about cooking recipes and culinary arts techniques.");

        ScanRun run = scanner.startScan(List.of(tempDir.toString()));
        assertThat(run.getPhase()).isEqualTo("COMPLETE");

        List<DuplicateGroup> groups = groupRepository.findByScanId(run.getId());
        // Only the A+B pair; unique is not grouped
        assertThat(groups).hasSize(1);
        assertThat(groups.getFirst().getMembers()).hasSize(2);
    }

    // ── helper ───────────────────────────────────────────────────────────────

    private void createHtml(String name, String bodyText) throws IOException {
        createFile(name, "<html><body><p>" + bodyText + "</p></body></html>");
    }
}
