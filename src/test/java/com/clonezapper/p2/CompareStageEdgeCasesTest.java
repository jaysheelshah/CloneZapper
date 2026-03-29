package com.clonezapper.p2;

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
 * P2 — CompareStage edge cases.
 */
@Tag("P2")
class CompareStageEdgeCasesTest extends BaseTest {

    @Autowired private ScanStage scanStage;
    @Autowired private CandidateStage candidateStage;
    @Autowired private CompareStage compareStage;
    @Autowired private ScanRepository scanRepository;

    @Test
    void threeIdenticalFilesProduceThreePairs() throws IOException {
        // Three identical files → candidate group of 3 → 3 pairs: (A,B), (A,C), (B,C)
        createFile("dir1/file.txt", TestFixtures.IDENTICAL_CONTENT);
        createFile("dir2/file.txt", TestFixtures.IDENTICAL_CONTENT);
        createFile("dir3/file.txt", TestFixtures.IDENTICAL_CONTENT);

        long runId = runPipeline();

        List<List<Long>> candidates = candidateStage.execute(runId);
        List<CompareStage.ScoredPair> pairs = compareStage.execute(candidates);

        assertThat(pairs).hasSize(3);
        assertThat(pairs).allSatisfy(p -> {
            assertThat(p.similarity()).isEqualTo(1.0);
            assertThat(p.strategy()).isEqualTo("exact-hash");
        });
    }

    @Test
    void partialHashCollisionDoesNotProducePair() throws IOException {
        // Craft two files whose first 4 KB are identical but full content differs.
        // CandidateStage will group them (same size + partial hash).
        // CompareStage must NOT emit a pair because their full hashes differ.
        byte[] shared4kb  = TestFixtures.randomContent(4 * 1024);
        byte[] tailA      = "AAAA".getBytes();
        byte[] tailB      = "BBBB".getBytes();

        byte[] contentA = concat(shared4kb, tailA);
        byte[] contentB = concat(shared4kb, tailB);

        createFile("a.bin", contentA);
        createFile("b.bin", contentB);

        long runId = runPipeline();

        List<List<Long>> candidates = candidateStage.execute(runId);
        // Both files share size and the first 4 KB → they ARE candidates
        assertThat(candidates).hasSize(1);

        List<CompareStage.ScoredPair> pairs = compareStage.execute(candidates);
        // But their full hashes differ → CompareStage must reject them
        assertThat(pairs).isEmpty();
    }

    @Test
    void emptyInputProducesNoPairs() {
        List<CompareStage.ScoredPair> pairs = compareStage.execute(List.of());
        assertThat(pairs).isEmpty();
    }

    private long runPipeline() throws IOException {
        ScanRun run = new ScanRun();
        run.setPhase("SCANNING");
        run.setCreatedAt(LocalDateTime.now());
        run.setRunLabel("compare-edge-test");
        long runId = scanRepository.save(run).getId();
        scanStage.execute(runId, List.of(tempDir.toString()));
        return runId;
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] result = new byte[a.length + b.length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }
}
