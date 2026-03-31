package com.clonezapper.p2;

import com.clonezapper.BaseTest;
import com.clonezapper.TestFixtures;
import com.clonezapper.db.DuplicateGroupRepository;
import com.clonezapper.db.FileRepository;
import com.clonezapper.db.ScanRepository;
import com.clonezapper.engine.pipeline.ClusterStage;
import com.clonezapper.model.DuplicateGroup;
import com.clonezapper.model.DuplicateMember;
import com.clonezapper.model.ScannedFile;
import com.clonezapper.model.ScanRun;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2E: review queue — groups below the confidence threshold can be dismissed,
 * which removes them from the DB so they no longer appear in results or staging.
 */
@Tag("P2")
class ReviewQueueDismissTest extends BaseTest {

    @Autowired ScanRepository scanRepository;
    @Autowired FileRepository fileRepository;
    @Autowired DuplicateGroupRepository groupRepository;

    @Test
    void dismissedGroupIsRemovedFromDb() throws IOException {
        // Arrange: a scan run with one low-confidence group
        Path fileA = createFile("a/doc.txt", TestFixtures.CONTENT_A);
        Path fileB = createFile("b/doc.txt", TestFixtures.CONTENT_B);

        ScanRun run = new ScanRun();
        run.setPhase("COMPLETE");
        run.setRunLabel("test-review");
        scanRepository.save(run);

        ScannedFile sfA = persistFile(run.getId(), fileA, 100);
        ScannedFile sfB = persistFile(run.getId(), fileB, 100);

        // Confidence below threshold → review queue
        double lowConfidence = ClusterStage.DEFAULT_CONFIDENCE_THRESHOLD - 0.1;
        DuplicateGroup group = new DuplicateGroup();
        group.setScanId(run.getId());
        group.setCanonicalFileId(sfA.getId());
        group.setStrategy("near-dup");
        group.setConfidence(lowConfidence);
        groupRepository.save(group);
        groupRepository.saveMember(new DuplicateMember(group.getId(), sfA.getId(), lowConfidence));
        groupRepository.saveMember(new DuplicateMember(group.getId(), sfB.getId(), lowConfidence));

        // Confirm it appears in the review queue
        List<DuplicateGroup> queue = groupRepository.findReviewQueueByScanId(
            run.getId(), ClusterStage.DEFAULT_CONFIDENCE_THRESHOLD);
        assertThat(queue).hasSize(1);

        // Act: dismiss
        groupRepository.deleteById(group.getId());

        // Assert: group and members gone
        List<DuplicateGroup> afterDismiss = groupRepository.findReviewQueueByScanId(
            run.getId(), ClusterStage.DEFAULT_CONFIDENCE_THRESHOLD);
        assertThat(afterDismiss).isEmpty();
        assertThat(groupRepository.countByScanId(run.getId())).isZero();
    }

    @Test
    void highConfidenceGroupNotInReviewQueue() throws IOException {
        Path fileA = createFile("a/doc.txt", TestFixtures.CONTENT_A);
        Path fileB = createFile("b/doc.txt", TestFixtures.CONTENT_B);

        ScanRun run = new ScanRun();
        run.setPhase("COMPLETE");
        run.setRunLabel("test-auto");
        scanRepository.save(run);

        ScannedFile sfA = persistFile(run.getId(), fileA, 100);
        ScannedFile sfB = persistFile(run.getId(), fileB, 100);

        // Confidence at threshold → auto queue, not review queue
        DuplicateGroup group = new DuplicateGroup();
        group.setScanId(run.getId());
        group.setCanonicalFileId(sfA.getId());
        group.setStrategy("exact-hash");
        group.setConfidence(1.0);
        groupRepository.save(group);
        groupRepository.saveMember(new DuplicateMember(group.getId(), sfA.getId(), 1.0));
        groupRepository.saveMember(new DuplicateMember(group.getId(), sfB.getId(), 1.0));

        List<DuplicateGroup> queue = groupRepository.findReviewQueueByScanId(
            run.getId(), ClusterStage.DEFAULT_CONFIDENCE_THRESHOLD);
        assertThat(queue).isEmpty();
    }

    // ── helper ───────────────────────────────────────────────────────────────

    private ScannedFile persistFile(long scanId, Path path, long size) {
        ScannedFile f = ScannedFile.builder()
            .scanId(scanId)
            .path(path.toString())
            .size(size)
            .mimeType("text/plain")
            .provider("local")
            .build();
        return fileRepository.save(f);
    }
}
