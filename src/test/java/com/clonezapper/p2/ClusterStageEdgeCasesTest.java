package com.clonezapper.p2;

import com.clonezapper.BaseTest;
import com.clonezapper.TestFixtures;
import com.clonezapper.db.FileRepository;
import com.clonezapper.db.ScanRepository;
import com.clonezapper.engine.pipeline.CandidateStage;
import com.clonezapper.engine.pipeline.ClusterStage;
import com.clonezapper.engine.pipeline.CompareStage;
import com.clonezapper.engine.pipeline.ScanStage;
import com.clonezapper.model.DuplicateGroup;
import com.clonezapper.model.DuplicateMember;
import com.clonezapper.model.ScanRun;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P2 — ClusterStage edge cases.
 */
@Tag("P2")
class ClusterStageEdgeCasesTest extends BaseTest {

    @Autowired private ScanStage scanStage;
    @Autowired private CandidateStage candidateStage;
    @Autowired private CompareStage compareStage;
    @Autowired private ClusterStage clusterStage;
    @Autowired private FileRepository fileRepository;
    @Autowired private ScanRepository scanRepository;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void threeIdenticalFilesFormOneGroupNotThree() throws IOException {
        // (A,B), (A,C), (B,C) pairs must all merge into a single group via Union-Find
        createFile("dir1/file.txt", TestFixtures.IDENTICAL_CONTENT);
        createFile("dir2/file.txt", TestFixtures.IDENTICAL_CONTENT);
        createFile("dir3/file.txt", TestFixtures.IDENTICAL_CONTENT);

        long runId = runPipeline();
        List<CompareStage.ScoredPair> pairs = compareStage.execute(
            candidateStage.execute(runId));
        ClusterStage.ClusterResult result = clusterStage.execute(runId, pairs);

        assertThat(result.autoQueue()).hasSize(1);
        assertThat(result.autoQueue().getFirst().getMembers()).hasSize(3);
    }

    @Test
    void twoIndependentPairsFormTwoGroups() throws IOException {
        createFile("pair1/a.txt", TestFixtures.IDENTICAL_CONTENT);
        createFile("pair1/b.txt", TestFixtures.IDENTICAL_CONTENT);
        createFile("pair2/a.txt", TestFixtures.CONTENT_A);
        createFile("pair2/b.txt", TestFixtures.CONTENT_A);

        long runId = runPipeline();
        List<CompareStage.ScoredPair> pairs = compareStage.execute(
            candidateStage.execute(runId));
        ClusterStage.ClusterResult result = clusterStage.execute(runId, pairs);

        int total = result.autoQueue().size() + result.reviewQueue().size();
        assertThat(total).isEqualTo(2);
    }

    @Test
    void canonicalFileIsAMemberOfItsGroup() throws IOException {
        createFile("original/report.pdf", TestFixtures.IDENTICAL_CONTENT);
        createFile("copy/report.pdf",     TestFixtures.IDENTICAL_CONTENT);

        long runId = runPipeline();
        List<CompareStage.ScoredPair> pairs = compareStage.execute(
            candidateStage.execute(runId));
        ClusterStage.ClusterResult result = clusterStage.execute(runId, pairs);

        assertThat(result.autoQueue()).hasSize(1);
        DuplicateGroup group = result.autoQueue().getFirst();

        Set<Long> memberIds = group.getMembers().stream()
            .map(DuplicateMember::getFileId)
            .collect(Collectors.toSet());
        assertThat(group.getCanonicalFileId()).isIn(memberIds);
    }

    @Test
    void canonicalFavoursOlderModifiedAt() throws IOException {
        createFile("newer/report.pdf", TestFixtures.IDENTICAL_CONTENT);
        createFile("older/report.pdf", TestFixtures.IDENTICAL_CONTENT);

        long runId = runPipeline();

        // Patch the "older" file's modified_at to be definitively earlier
        var olderFile = fileRepository.findByScanId(runId).stream()
            .filter(f -> f.getPath().contains("older"))
            .findFirst().orElseThrow();
        jdbc.update("UPDATE files SET modified_at = ? WHERE id = ?",
            LocalDateTime.of(2000, 1, 1, 0, 0).toString(), olderFile.getId());

        List<CompareStage.ScoredPair> pairs = compareStage.execute(
            candidateStage.execute(runId));
        ClusterStage.ClusterResult result = clusterStage.execute(runId, pairs);

        assertThat(result.autoQueue()).hasSize(1);
        assertThat(result.autoQueue().getFirst().getCanonicalFileId())
            .isEqualTo(olderFile.getId());
    }

    @Test
    void emptyPairsReturnsEmptyResult() {
        ClusterStage.ClusterResult result = clusterStage.execute(99L, List.of());
        assertThat(result.autoQueue()).isEmpty();
        assertThat(result.reviewQueue()).isEmpty();
    }

    private long runPipeline() {
        ScanRun run = new ScanRun();
        run.setPhase("SCANNING");
        run.setCreatedAt(LocalDateTime.now());
        run.setRunLabel("cluster-edge-test");
        long runId = scanRepository.save(run).getId();
        scanStage.execute(runId, List.of(tempDir.toString()));
        return runId;
    }
}
