package com.clonezapper.p2;

import com.clonezapper.BaseTest;
import com.clonezapper.TestFixtures;
import com.clonezapper.db.DuplicateGroupRepository;
import com.clonezapper.db.FileRepository;
import com.clonezapper.db.ScanRepository;
import com.clonezapper.engine.pipeline.CandidateStage;
import com.clonezapper.engine.pipeline.ClusterStage;
import com.clonezapper.engine.pipeline.CompareStage;
import com.clonezapper.engine.pipeline.ScanStage;
import com.clonezapper.model.DuplicateGroup;
import com.clonezapper.model.ScanRun;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2E: archive exclusion prevents staged files from appearing as false duplicates.
 * <p>The real risk scenario:
 *   1. User has docs/report.pdf
 *   2. CloneZapper stages a copy to archive/run_1/report.pdf
 *   3. User re-scans docs/ (which contains the archive/ subdir)
 *   4. Without exclusion: staged copy re-indexed → false "duplicate" group created
 *   5. With exclusion: archive subdir skipped → only original indexed → no false groups
 * <p>Drives the pipeline stages directly so the exclusion set can be controlled precisely.
 */
@Tag("P2")
class ArchiveExclusionE2ETest extends BaseTest {

    @Autowired ScanStage scanStage;
    @Autowired CandidateStage candidateStage;
    @Autowired CompareStage compareStage;
    @Autowired ClusterStage clusterStage;
    @Autowired ScanRepository scanRepository;
    @Autowired FileRepository fileRepository;
    @Autowired DuplicateGroupRepository groupRepository;

    @Test
    void stagedFilesDoNotCreateFalseDuplicateGroupsWhenArchiveIsExcluded() throws IOException {
        // Source file + its staged copy in the archive subdir
        createFile("docs/report.pdf",        TestFixtures.IDENTICAL_CONTENT);
        createFile("archive/run_1/report.pdf", TestFixtures.IDENTICAL_CONTENT);

        String archivePath = tempDir.resolve("archive").toString();
        long runId = runFullPipeline(List.of(tempDir.toString()), Set.of(archivePath));

        // Only the original should be indexed
        assertThat(fileRepository.countByScanId(runId)).isEqualTo(1);
        // No duplicate groups — there is only one visible copy
        assertThat(groupRepository.countByScanId(runId)).isZero();
    }

    @Test
    void withoutExclusionStagedFilesFormDuplicateGroup() throws IOException {
        // Negative control: without exclusion, the staged copy IS detected as a duplicate
        createFile("docs/report.pdf",        TestFixtures.IDENTICAL_CONTENT);
        createFile("archive/run_1/report.pdf", TestFixtures.IDENTICAL_CONTENT);

        long runId = runFullPipeline(List.of(tempDir.toString()), Set.of());

        assertThat(fileRepository.countByScanId(runId)).isEqualTo(2);
        assertThat(groupRepository.countByScanId(runId)).isEqualTo(1);
    }

    @Test
    void excludingArchiveDoesNotRemoveLegitDuplicatesFromResults() throws IOException {
        // Two genuine duplicates in docs/
        createFile("docs/copy_a.dat",            TestFixtures.IDENTICAL_CONTENT);
        createFile("docs/copy_b.dat",            TestFixtures.IDENTICAL_CONTENT);
        // Staged copy — should be invisible
        createFile("archive/run_1/copy_a.dat",   TestFixtures.IDENTICAL_CONTENT);

        String archivePath = tempDir.resolve("archive").toString();
        long runId = runFullPipeline(List.of(tempDir.toString()), Set.of(archivePath));

        // Two source files indexed (archive excluded)
        assertThat(fileRepository.countByScanId(runId)).isEqualTo(2);
        // The two source duplicates still form a group
        assertThat(groupRepository.countByScanId(runId)).isEqualTo(1);
        // Both members are from docs/
        DuplicateGroup group = groupRepository.findByScanId(runId).getFirst();
        group.getMembers().forEach(m -> {
            String path = fileRepository.findById(m.getFileId()).orElseThrow().getPath();
            assertThat(path).contains("docs");
        });
    }

    @Test
    void deeplyNestedArchiveContentIsAlsoExcluded() throws IOException {
        createFile("keep/a.dat", TestFixtures.CONTENT_A);
        // Archive with several nesting levels
        createFile("archive/run_2/subdir/deep/nested/a.dat", TestFixtures.CONTENT_A);

        String archivePath = tempDir.resolve("archive").toString();
        long runId = runFullPipeline(List.of(tempDir.toString()), Set.of(archivePath));

        assertThat(fileRepository.countByScanId(runId)).isEqualTo(1);
        assertThat(fileRepository.findByScanId(runId).getFirst().getPath()).contains("keep");
        assertThat(groupRepository.countByScanId(runId)).isZero();
    }

    @Test
    void scanCompletesWithNoErrorsWhenArchivePathDoesNotExist() throws IOException {
        createFile("docs/a.dat", TestFixtures.CONTENT_A);
        createFile("docs/b.dat", TestFixtures.CONTENT_B);

        // Non-existent archive path — exclusion should be silently ignored
        long runId = runFullPipeline(
            List.of(tempDir.toString()),
            Set.of(tempDir.resolve("nonexistent-archive").toString()));

        assertThat(fileRepository.countByScanId(runId)).isEqualTo(2);
        assertThat(groupRepository.countByScanId(runId)).isZero();
    }

    @Test
    void multipleArchiveRunsAllExcluded() throws IOException {
        createFile("docs/report.pdf",        TestFixtures.IDENTICAL_CONTENT);
        // Simulate two archive runs staging the same file
        createFile("archive/run_1/report.pdf", TestFixtures.IDENTICAL_CONTENT);
        createFile("archive/run_2/report.pdf", TestFixtures.IDENTICAL_CONTENT);

        String archivePath = tempDir.resolve("archive").toString();
        long runId = runFullPipeline(List.of(tempDir.toString()), Set.of(archivePath));

        // Only the original; no false groups from either archive run
        assertThat(fileRepository.countByScanId(runId)).isEqualTo(1);
        assertThat(groupRepository.countByScanId(runId)).isZero();
    }

    // ── helper ───────────────────────────────────────────────────────────────

    private long runFullPipeline(List<String> roots, Set<String> exclusions) {
        ScanRun run = new ScanRun();
        run.setPhase("SCANNING");
        run.setCreatedAt(LocalDateTime.now());
        run.setRunLabel("e2e-exclusion");
        scanRepository.save(run);
        long runId = run.getId();

        scanStage.execute(runId, roots, exclusions);
        var candidates = candidateStage.execute(runId);
        var exactPairs = compareStage.execute(candidates);
        var nearDupPairs = compareStage.executeNearDup(runId);
        var pairs = new ArrayList<>(exactPairs);
        pairs.addAll(nearDupPairs);
        clusterStage.execute(runId, pairs);

        scanRepository.markCompleted(runId);
        return runId;
    }
}
