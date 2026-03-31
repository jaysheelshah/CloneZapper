package com.clonezapper.p2;

import com.clonezapper.BaseTest;
import com.clonezapper.db.FileRepository;
import com.clonezapper.db.ScanRepository;
import com.clonezapper.engine.pipeline.CandidateStage;
import com.clonezapper.engine.pipeline.CompareStage;
import com.clonezapper.engine.pipeline.ScanStage;
import com.clonezapper.model.ScannedFile;
import com.clonezapper.model.ScanRun;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P2 — CompareStage reuses cached hash_full for unchanged files (incremental).
 * Proof strategy: run a full pipeline to populate hash_full, then poison the file
 * on disk while restoring the original modification time so ScanStage copies the
 * cached hash_full into the new record. A second CompareStage pass must return the
 * original hash — not the hash of the poisoned content.
 */
@Tag("P2")
class HashFullCachingTest extends BaseTest {

    @Autowired ScanStage scanStage;
    @Autowired CandidateStage candidateStage;
    @Autowired CompareStage compareStage;
    @Autowired ScanRepository scanRepository;
    @Autowired FileRepository fileRepository;

    @Test
    void compareStageReusesCachedHashFullForUnchangedFile() throws Exception {
        // Two identical files so CompareStage runs and writes hash_full
        byte[] content = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA".getBytes(); // 31 bytes
        createFile("a/doc.dat", content);
        createFile("b/doc.dat", content);

        // Run 1 — populates hash_full in the files table
        long run1Id = createRun("run1");
        scanStage.execute(run1Id, List.of(tempDir.toString()));
        List<List<Long>> candidates1 = candidateStage.execute(run1Id);
        compareStage.execute(candidates1);

        String hash1 = fileRepository.findByScanId(run1Id).getFirst().getHashFull();
        assertThat(hash1).isNotNull().isNotBlank();

        // Poison the file content but keep the same size and restore original modtime
        // so ScanStage's incremental check passes and copies the cached hash_full
        Path fileA = tempDir.resolve("a/doc.dat");
        FileTime originalModTime = Files.getLastModifiedTime(fileA);
        byte[] poisoned = "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB".getBytes(); // same length
        Files.write(fileA, poisoned);
        Files.setLastModifiedTime(fileA, originalModTime);

        // Run 2 — ScanStage should copy hash_full from run1 for the "unchanged" file
        long run2Id = createRun("run2");
        List<ScannedFile> files2 = scanStage.execute(run2Id, List.of(tempDir.toString()));

        ScannedFile cachedFile = files2.stream()
            .filter(f -> f.getPath().contains("a" + java.io.File.separator + "doc.dat"))
            .findFirst().orElseThrow();

        assertThat(cachedFile.getHashFull())
            .as("ScanStage should have copied hash_full from the previous run")
            .isEqualTo(hash1);
    }

    @Test
    void hashFullCacheIsInvalidatedWhenSizeChanges() throws Exception {
        byte[] original = "short".getBytes();
        createFile("a/doc.dat", original);
        createFile("b/doc.dat", original);

        long run1Id = createRun("run1");
        scanStage.execute(run1Id, List.of(tempDir.toString()));
        List<List<Long>> candidates1 = candidateStage.execute(run1Id);
        compareStage.execute(candidates1);

        String hash1 = fileRepository.findByScanId(run1Id).stream()
            .map(ScannedFile::getHashFull)
            .filter(Objects::nonNull)
            .findFirst().orElse(null);
        assertThat(hash1).isNotNull();

        // Change file size — ScanStage must NOT copy cached hash_full
        Path fileA = tempDir.resolve("a/doc.dat");
        Files.write(fileA, "this is much longer content now".getBytes());

        long run2Id = createRun("run2");
        List<ScannedFile> files2 = scanStage.execute(run2Id, List.of(tempDir.toString()));

        ScannedFile rehashedFile = files2.stream()
            .filter(f -> f.getPath().contains("a" + java.io.File.separator + "doc.dat"))
            .findFirst().orElseThrow();

        // hash_full should be null — the file changed size so the cache was cleared
        assertThat(rehashedFile.getHashFull()).isNull();
    }

    @Test
    void compareStageWritesHashFullWhenCacheIsNull() throws IOException {
        byte[] content = "IDENTICAL_CONTENT_FOR_HASH".getBytes();
        createFile("x/doc.dat", content);
        createFile("y/doc.dat", content);

        long runId = createRun("fresh-run");
        List<ScannedFile> files = scanStage.execute(runId, List.of(tempDir.toString()));

        // Before CompareStage: hash_full should be null (fresh files)
        files.forEach(f -> assertThat(f.getHashFull()).isNull());

        List<List<Long>> candidates = candidateStage.execute(runId);
        compareStage.execute(candidates);

        // After CompareStage: hash_full must be written for files in candidate groups
        List<ScannedFile> after = fileRepository.findByScanId(runId);
        assertThat(after).allMatch(f -> f.getHashFull() != null && !f.getHashFull().isBlank());
    }

    // ── helper ───────────────────────────────────────────────────────────────

    private long createRun(String label) {
        ScanRun run = new ScanRun();
        run.setPhase("SCANNING");
        run.setCreatedAt(LocalDateTime.now());
        run.setRunLabel(label);
        return scanRepository.save(run).getId();
    }
}
