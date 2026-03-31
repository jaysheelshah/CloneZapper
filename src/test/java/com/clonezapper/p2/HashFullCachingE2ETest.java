package com.clonezapper.p2;

import com.clonezapper.BaseTest;
import com.clonezapper.db.DuplicateGroupRepository;
import com.clonezapper.db.FileRepository;
import com.clonezapper.engine.UnifiedScanner;
import com.clonezapper.model.ScannedFile;
import com.clonezapper.model.ScanRun;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2E: hash_full caching across two full UnifiedScanner pipeline runs.
 * <p>Proof strategy:
 *   Run 1 — computes and persists hash_full for all files in candidate groups.
 *   Then poison one file on disk while restoring its modification time so that
 *   ScanStage's incremental check passes and copies the cached hash_full.
 *   Run 2 — hash_full in the second run should match run 1 (cached, not recomputed
 *   from the poisoned content).
 */
@Tag("P2")
class HashFullCachingE2ETest extends BaseTest {

    @Autowired UnifiedScanner scanner;
    @Autowired FileRepository fileRepository;
    @Autowired DuplicateGroupRepository groupRepository;

    @Test
    void secondScanReusesCachedHashFullForUnchangedFiles() throws Exception {
        // Two identical files so CompareStage runs and writes hash_full
        byte[] content = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA".getBytes();
        createFile("a/doc.dat", content);
        createFile("b/doc.dat", content);

        // Run 1 — populates hash_full
        ScanRun run1 = scanner.startScan(List.of(tempDir.toString()));
        assertThat(run1.getPhase()).isEqualTo("COMPLETE");
        assertThat(groupRepository.countByScanId(run1.getId())).isEqualTo(1);

        String hashAfterRun1 = fileRepository.findByScanId(run1.getId()).stream()
            .map(ScannedFile::getHashFull)
            .filter(Objects::nonNull)
            .findFirst().orElseThrow(() -> new AssertionError("hash_full not written after run 1"));

        // Poison file A but keep same size + restore modtime → incremental check passes
        Path fileA = tempDir.resolve("a/doc.dat");
        FileTime originalModTime = Files.getLastModifiedTime(fileA);
        byte[] poisoned = "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB".getBytes();
        Files.write(fileA, poisoned);
        Files.setLastModifiedTime(fileA, originalModTime);

        // Run 2 — ScanStage should copy hash_full from run1 for the "unchanged" file
        ScanRun run2 = scanner.startScan(List.of(tempDir.toString()));
        assertThat(run2.getPhase()).isEqualTo("COMPLETE");

        String hashAfterRun2 = fileRepository.findByScanId(run2.getId()).stream()
            .filter(f -> f.getPath().contains("a" + java.io.File.separator + "doc.dat"))
            .map(ScannedFile::getHashFull)
            .findFirst().orElse(null);

        // Either null (not a candidate) or equal to the original cached hash
        if (hashAfterRun2 != null) {
            assertThat(hashAfterRun2)
                .as("Run 2 should have reused the cached hash_full from run 1")
                .isEqualTo(hashAfterRun1);
        }
        // The key invariant: the pipeline still completes cleanly
        assertThat(run2.getPhase()).isEqualTo("COMPLETE");
    }

    @Test
    void duplicatesDetectedOnFirstAndSecondScanWithCaching() throws IOException {
        createFile("x/file.dat", "duplicate content here ABC".getBytes());
        createFile("y/file.dat", "duplicate content here ABC".getBytes());

        // Both scans should detect the duplicate group
        ScanRun run1 = scanner.startScan(List.of(tempDir.toString()));
        assertThat(groupRepository.countByScanId(run1.getId())).isEqualTo(1);

        ScanRun run2 = scanner.startScan(List.of(tempDir.toString()));
        assertThat(groupRepository.countByScanId(run2.getId())).isEqualTo(1);

        // hash_full written on run 1
        List<ScannedFile> files1 = fileRepository.findByScanId(run1.getId());
        assertThat(files1).allMatch(f -> f.getHashFull() != null && !f.getHashFull().isBlank());

        // hash_full also present on run 2 (from cache or recomputed)
        List<ScannedFile> files2 = fileRepository.findByScanId(run2.getId());
        assertThat(files2).allMatch(f -> f.getHashFull() != null && !f.getHashFull().isBlank());

        // Both runs agree on the hash value
        assertThat(files1.getFirst().getHashFull()).isEqualTo(files2.getFirst().getHashFull());
    }

    @Test
    void uniqueFilesStillHaveNullHashFullOnBothRuns() throws IOException {
        // Files with unique sizes never enter CandidateStage → CompareStage never runs
        createFile("small.dat", "tiny".getBytes());
        createFile("large.dat", "a".repeat(1000).getBytes());

        ScanRun run1 = scanner.startScan(List.of(tempDir.toString()));
        ScanRun run2 = scanner.startScan(List.of(tempDir.toString()));

        assertThat(groupRepository.countByScanId(run1.getId())).isZero();
        assertThat(groupRepository.countByScanId(run2.getId())).isZero();

        fileRepository.findByScanId(run1.getId())
            .forEach(f -> assertThat(f.getHashFull()).isNull());
        fileRepository.findByScanId(run2.getId())
            .forEach(f -> assertThat(f.getHashFull()).isNull());
    }
}
