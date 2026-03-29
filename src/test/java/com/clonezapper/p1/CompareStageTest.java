package com.clonezapper.p1;

import com.clonezapper.BaseTest;
import com.clonezapper.TestFixtures;
import com.clonezapper.db.ScanRepository;
import com.clonezapper.engine.pipeline.CandidateStage;
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
 * P1 — CompareStage correctness.
 * Identical files must produce a confirmed pair; no candidates must produce no pairs.
 */
@Tag("P1")
class CompareStageTest extends BaseTest {

    @Autowired private ScanStage scanStage;
    @Autowired private CandidateStage candidateStage;
    @Autowired private CompareStage compareStage;
    @Autowired private ScanRepository scanRepository;

    @Test
    void identicalFilesProduceOneConfirmedPair() throws IOException {
        createFile("docs/report.pdf",      TestFixtures.IDENTICAL_CONTENT);
        createFile("downloads/report.pdf", TestFixtures.IDENTICAL_CONTENT);

        long runId = runPipeline();

        List<List<Long>> candidates = candidateStage.execute(runId);
        List<CompareStage.ScoredPair> pairs = compareStage.execute(candidates);

        assertThat(pairs).hasSize(1);
        assertThat(pairs.get(0).similarity()).isEqualTo(1.0);
        assertThat(pairs.get(0).strategy()).isEqualTo("exact-hash");
    }

    @Test
    void noCandidatesProducesNoPairs() throws IOException {
        createFile("a.txt", TestFixtures.CONTENT_A);
        createFile("b.txt", TestFixtures.CONTENT_B);

        long runId = runPipeline();

        List<List<Long>> candidates = candidateStage.execute(runId);
        List<CompareStage.ScoredPair> pairs = compareStage.execute(candidates);

        assertThat(pairs).isEmpty();
    }

    private long runPipeline() throws IOException {
        ScanRun run = new ScanRun();
        run.setPhase("SCANNING");
        run.setCreatedAt(LocalDateTime.now());
        run.setRunLabel("compare-test");
        long runId = scanRepository.save(run).getId();
        scanStage.execute(runId, List.of(tempDir.toString()));
        return runId;
    }
}
