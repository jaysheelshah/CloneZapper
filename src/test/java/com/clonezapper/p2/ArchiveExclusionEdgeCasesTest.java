package com.clonezapper.p2;

import com.clonezapper.BaseTest;
import com.clonezapper.TestFixtures;
import com.clonezapper.db.FileRepository;
import com.clonezapper.db.ScanRepository;
import com.clonezapper.engine.pipeline.ScanStage;
import com.clonezapper.model.ScanRun;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("P2")
class ArchiveExclusionEdgeCasesTest extends BaseTest {

    @Autowired ScanStage scanStage;
    @Autowired ScanRepository scanRepository;
    @Autowired FileRepository fileRepository;

    @Test
    void deeplyNestedFileUnderArchiveRootIsExcluded() throws IOException {
        createFile("docs/a.txt", TestFixtures.CONTENT_A);
        createFile("archive/run_1/subdir/deep/file.txt", TestFixtures.CONTENT_A);

        String archivePath = tempDir.resolve("archive").toString();
        long runId = createRun();
        scanStage.execute(runId, List.of(tempDir.toString()), Set.of(archivePath));

        assertThat(fileRepository.countByScanId(runId)).isEqualTo(1);
        assertThat(fileRepository.findByScanId(runId).getFirst().getPath()).contains("docs");
    }

    @Test
    void multipleExclusionPathsAllRespected() throws IOException {
        createFile("keep/a.txt",    TestFixtures.CONTENT_A);
        createFile("exclude1/b.txt", TestFixtures.CONTENT_B);
        createFile("exclude2/c.txt", TestFixtures.IDENTICAL_CONTENT);

        Set<String> exclusions = Set.of(
            tempDir.resolve("exclude1").toString(),
            tempDir.resolve("exclude2").toString());

        long runId = createRun();
        scanStage.execute(runId, List.of(tempDir.toString()), exclusions);

        assertThat(fileRepository.countByScanId(runId)).isEqualTo(1);
    }

    @Test
    void scanningArchiveRootDirectlyIndexesNothing() throws IOException {
        // If the user explicitly scans the archive path (bad idea), it scans normally.
        // The exclusion only applies when archive is a sub-path of a broader scan.
        // Scanning archive root itself is excluded only if passed as an exclusion.
        createFile("archive/file.txt", TestFixtures.CONTENT_A);
        String archivePath = tempDir.resolve("archive").toString();

        long runId = createRun();
        // Scan the archive itself, but with the archive excluded — 0 files indexed
        scanStage.execute(runId, List.of(archivePath), Set.of(archivePath));

        assertThat(fileRepository.countByScanId(runId)).isZero();
    }

    @Test
    void excludingNonExistentPathHasNoEffect() throws IOException {
        createFile("docs/a.txt", TestFixtures.CONTENT_A);
        createFile("docs/b.txt", TestFixtures.CONTENT_B);

        Set<String> exclusions = Set.of("/does/not/exist/anywhere");
        long runId = createRun();
        scanStage.execute(runId, List.of(tempDir.toString()), exclusions);

        assertThat(fileRepository.countByScanId(runId)).isEqualTo(2);
    }

    private long createRun() {
        ScanRun run = new ScanRun();
        run.setPhase("SCANNING");
        run.setCreatedAt(LocalDateTime.now());
        run.setRunLabel("edge-test");
        return scanRepository.save(run).getId();
    }
}
