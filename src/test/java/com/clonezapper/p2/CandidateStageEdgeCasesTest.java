package com.clonezapper.p2;

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
 * P2 — CandidateStage edge cases.
 */
@Tag("P2")
class CandidateStageEdgeCasesTest extends BaseTest {

    @Autowired private ScanStage scanStage;
    @Autowired private CandidateStage candidateStage;
    @Autowired private ScanRepository scanRepository;

    @Test
    void sameSizeDifferentContentIsNotACandidate() throws IOException {
        // Same byte count, different content → different partial hash → no candidate group
        createFile("a.txt", "aaaaaaaaaa");  // 10 bytes
        createFile("b.txt", "bbbbbbbbbb");  // 10 bytes

        long runId = scanAndGetRunId();

        List<List<Long>> groups = candidateStage.execute(runId);

        assertThat(groups).isEmpty();
    }

    @Test
    void twoIndependentDuplicatePairsProduceTwoGroups() throws IOException {
        // Pair 1
        createFile("pair1/a.txt", TestFixtures.IDENTICAL_CONTENT);
        createFile("pair1/b.txt", TestFixtures.IDENTICAL_CONTENT);
        // Pair 2 — different content, same size as each other
        createFile("pair2/a.txt", TestFixtures.CONTENT_A);
        createFile("pair2/b.txt", TestFixtures.CONTENT_A);

        long runId = scanAndGetRunId();

        List<List<Long>> groups = candidateStage.execute(runId);

        assertThat(groups).hasSize(2);
        assertThat(groups).allSatisfy(g -> assertThat(g).hasSize(2));
    }

    @Test
    void uniqueFileAmongDuplicatesIsNotIncludedInAnyGroup() throws IOException {
        // Two duplicates + one unique file
        createFile("dup/a.txt", TestFixtures.IDENTICAL_CONTENT);
        createFile("dup/b.txt", TestFixtures.IDENTICAL_CONTENT);
        createFile("unique.txt", TestFixtures.CONTENT_A);

        long runId = scanAndGetRunId();

        List<List<Long>> groups = candidateStage.execute(runId);

        // Only one group, containing exactly the two duplicates
        assertThat(groups).hasSize(1);
        assertThat(groups.get(0)).hasSize(2);
    }

    private long scanAndGetRunId() throws IOException {
        ScanRun run = new ScanRun();
        run.setPhase("SCANNING");
        run.setCreatedAt(LocalDateTime.now());
        run.setRunLabel("candidate-edge-test");
        long runId = scanRepository.save(run).getId();
        scanStage.execute(runId, List.of(tempDir.toString()));
        return runId;
    }
}
