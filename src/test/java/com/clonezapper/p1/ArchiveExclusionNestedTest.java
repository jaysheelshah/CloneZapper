package com.clonezapper.p1;

import com.clonezapper.BaseTest;
import com.clonezapper.TestFixtures;
import com.clonezapper.db.FileRepository;
import com.clonezapper.db.ScanRepository;
import com.clonezapper.engine.pipeline.ScanStage;
import com.clonezapper.model.ScannedFile;
import com.clonezapper.model.ScanRun;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P1 — Archive root nested inside the scan root is excluded.
 * The real risk: user scans C:\Documents, but the archive lives at
 * C:\Documents\CloneZapperArchive — without exclusion, staged files
 * get re-indexed and matched against their originals.
 */
@Tag("P1")
class ArchiveExclusionNestedTest extends BaseTest {

    @Autowired ScanStage scanStage;
    @Autowired ScanRepository scanRepository;
    @Autowired FileRepository fileRepository;

    @Test
    void archiveSubdirIsExcludedWhenPassedAsExclusionSet() throws IOException {
        createFile("docs/report.pdf",               TestFixtures.CONTENT_A);
        createFile("docs/notes.txt",                TestFixtures.CONTENT_B);
        createFile("archive/run_1/report.pdf",      TestFixtures.CONTENT_A); // staged copy

        String archivePath = tempDir.resolve("archive").toString();
        long runId = createRun();

        // Scan the whole tempDir but exclude the archive subdir
        scanStage.execute(runId, List.of(tempDir.toString()), Set.of(archivePath));

        List<ScannedFile> files = fileRepository.findByScanId(runId);
        assertThat(files).hasSize(2);
        assertThat(files).noneMatch(f -> f.getPath().contains("archive"));
    }

    @Test
    void nonExcludedFilesAreStillIndexed() throws IOException {
        createFile("source/a.txt", TestFixtures.CONTENT_A);
        createFile("source/b.txt", TestFixtures.CONTENT_B);
        createFile("exclude/c.txt", TestFixtures.IDENTICAL_CONTENT);

        String excludePath = tempDir.resolve("exclude").toString();
        long runId = createRun();

        scanStage.execute(runId, List.of(tempDir.toString()), Set.of(excludePath));

        List<ScannedFile> files = fileRepository.findByScanId(runId);
        assertThat(files).hasSize(2);
        assertThat(files).allMatch(f -> f.getPath().contains("source"));
    }

    @Test
    void emptyExclusionSetIndexesEverything() throws IOException {
        createFile("a.txt", TestFixtures.CONTENT_A);
        createFile("b.txt", TestFixtures.CONTENT_B);
        long runId = createRun();

        scanStage.execute(runId, List.of(tempDir.toString()), Set.of());

        assertThat(fileRepository.countByScanId(runId)).isEqualTo(2);
    }

    private long createRun() {
        ScanRun run = new ScanRun();
        run.setPhase("SCANNING");
        run.setCreatedAt(LocalDateTime.now());
        run.setRunLabel("exclusion-test");
        return scanRepository.save(run).getId();
    }
}
