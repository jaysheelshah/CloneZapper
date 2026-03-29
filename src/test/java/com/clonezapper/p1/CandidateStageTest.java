package com.clonezapper.p1;

import com.clonezapper.BaseTest;
import com.clonezapper.TestFixtures;
import com.clonezapper.db.ScanRepository;
import com.clonezapper.engine.pipeline.CandidateStage;
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
 * P1 — CandidateStage correctness.
 * Identical files must be grouped; unique files must not appear as candidates.
 */
@Tag("P1")
class CandidateStageTest extends BaseTest {

    @Autowired private ScanStage scanStage;
    @Autowired private CandidateStage candidateStage;
    @Autowired private ScanRepository scanRepository;

    @Test
    void identicalFilesProduceOneCandidateGroup() throws IOException {
        createFile("docs/report.pdf",      TestFixtures.IDENTICAL_CONTENT);
        createFile("downloads/report.pdf", TestFixtures.IDENTICAL_CONTENT);

        long runId = scanAndGetRunId();

        List<List<Long>> groups = candidateStage.execute(runId);

        assertThat(groups).hasSize(1);
        assertThat(groups.get(0)).hasSize(2);
    }

    @Test
    void uniqueFilesProduceNoCandidateGroups() throws IOException {
        createFile("a.txt", TestFixtures.CONTENT_A);
        createFile("b.txt", TestFixtures.CONTENT_B);

        long runId = scanAndGetRunId();

        List<List<Long>> groups = candidateStage.execute(runId);

        assertThat(groups).isEmpty();
    }

    private long scanAndGetRunId() throws IOException {
        ScanRun run = new ScanRun();
        run.setPhase("SCANNING");
        run.setCreatedAt(LocalDateTime.now());
        run.setRunLabel("candidate-test");
        long runId = scanRepository.save(run).getId();
        scanStage.execute(runId, List.of(tempDir.toString()));
        return runId;
    }
}
