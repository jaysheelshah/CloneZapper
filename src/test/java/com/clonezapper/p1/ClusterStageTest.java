package com.clonezapper.p1;

import com.clonezapper.BaseTest;
import com.clonezapper.TestFixtures;
import com.clonezapper.db.DuplicateGroupRepository;
import com.clonezapper.db.ScanRepository;
import com.clonezapper.engine.pipeline.CandidateStage;
import com.clonezapper.engine.pipeline.ClusterStage;
import com.clonezapper.engine.pipeline.CompareStage;
import com.clonezapper.engine.pipeline.ScanStage;
import com.clonezapper.model.ScanRun;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P1 — ClusterStage correctness.
 * Duplicate pairs must form groups; groups must be persisted; routing must be correct.
 */
@Tag("P1")
class ClusterStageTest extends BaseTest {

    @Autowired private ScanStage scanStage;
    @Autowired private CandidateStage candidateStage;
    @Autowired private CompareStage compareStage;
    @Autowired private ClusterStage clusterStage;
    @Autowired private DuplicateGroupRepository groupRepository;
    @Autowired private ScanRepository scanRepository;

    @Test
    void twoIdenticalFilesFormOneGroupInAutoQueue() throws IOException {
        createFile("docs/report.pdf",      TestFixtures.IDENTICAL_CONTENT);
        createFile("downloads/report.pdf", TestFixtures.IDENTICAL_CONTENT);

        long runId = runPipeline();
        List<CompareStage.ScoredPair> pairs = compareStage.execute(
            candidateStage.execute(runId));
        ClusterStage.ClusterResult result = clusterStage.execute(runId, pairs);

        assertThat(result.autoQueue()).hasSize(1);
        assertThat(result.reviewQueue()).isEmpty();

        var group = result.autoQueue().getFirst();
        assertThat(group.getMembers()).hasSize(2);
        assertThat(group.getConfidence()).isEqualTo(1.0);
        assertThat(group.getStrategy()).isEqualTo("exact-hash");
        assertThat(group.getCanonicalFileId()).isNotNull();
    }

    @Test
    void groupIsPersistedToDatabase() throws IOException {
        createFile("a/file.txt", TestFixtures.IDENTICAL_CONTENT);
        createFile("b/file.txt", TestFixtures.IDENTICAL_CONTENT);

        long runId = runPipeline();
        List<CompareStage.ScoredPair> pairs = compareStage.execute(
            candidateStage.execute(runId));
        clusterStage.execute(runId, pairs);

        assertThat(groupRepository.countByScanId(runId)).isEqualTo(1);

        var groups = groupRepository.findByScanId(runId);
        assertThat(groups).hasSize(1);
        assertThat(groups.getFirst().getMembers()).hasSize(2);
    }

    @Test
    void noPairsProducesNoGroups() throws IOException {
        createFile("a.txt", TestFixtures.CONTENT_A);
        createFile("b.txt", TestFixtures.CONTENT_B);

        long runId = runPipeline();
        List<CompareStage.ScoredPair> pairs = compareStage.execute(
            candidateStage.execute(runId));
        ClusterStage.ClusterResult result = clusterStage.execute(runId, pairs);

        assertThat(result.autoQueue()).isEmpty();
        assertThat(result.reviewQueue()).isEmpty();
    }

    private long runPipeline() {
        ScanRun run = new ScanRun();
        run.setPhase("SCANNING");
        run.setCreatedAt(LocalDateTime.now());
        run.setRunLabel("cluster-test");
        long runId = scanRepository.save(run).getId();
        scanStage.execute(runId, List.of(tempDir.toString()));
        return runId;
    }
}
