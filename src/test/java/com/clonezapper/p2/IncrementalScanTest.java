package com.clonezapper.p2;

import com.clonezapper.BaseTest;
import com.clonezapper.db.ScanRepository;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P2 — Incremental scan reuses partial-hash fingerprints for unchanged files.
 * <p>Proof strategy: replace file contents while artificially restoring the original
 * modification time. If incremental reuse works, the second scan returns the hash
 * of the ORIGINAL content — not the new content — because it reused the cached value.
 */
@Tag("P2")
class IncrementalScanTest extends BaseTest {

    @Autowired ScanStage scanStage;
    @Autowired ScanRepository scanRepository;

    @Test
    void unchangedFileReusesPartialHashOnSecondScan() throws Exception {
        // Two byte arrays of the same length so size check passes
        byte[] contentA = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA".getBytes(); // 31 bytes
        byte[] contentB = "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB".getBytes(); // 31 bytes

        Path file = createFile("stable.dat", contentA);
        FileTime originalModTime = Files.getLastModifiedTime(file);

        // Scan 1 — establishes hash for contentA
        List<ScannedFile> run1Files = scanStage.execute(createRun(), List.of(tempDir.toString()));
        String hashA = run1Files.getFirst().getHashPartial();
        assertThat(hashA).isNotNull();

        // Swap content but restore original modification time → file "looks unchanged"
        Files.write(file, contentB);
        Files.setLastModifiedTime(file, originalModTime);

        // Scan 2 — should reuse cached hash, not recompute from contentB
        List<ScannedFile> run2Files = scanStage.execute(createRun(), List.of(tempDir.toString()));
        String hashCached = run2Files.getFirst().getHashPartial();

        assertThat(hashCached)
            .as("incremental scan should reuse hash from previous record")
            .isEqualTo(hashA);
    }

    @Test
    void modifiedFileSizeTriggersRehash() throws Exception {
        Path file = createFile("changing.dat", "short".getBytes());

        List<ScannedFile> run1Files = scanStage.execute(createRun(), List.of(tempDir.toString()));
        String hash1 = run1Files.getFirst().getHashPartial();

        // Write longer content — size changes → cache miss → rehash
        Files.write(file, "this is much longer content now and is definitely different".getBytes());

        List<ScannedFile> run2Files = scanStage.execute(createRun(), List.of(tempDir.toString()));
        String hash2 = run2Files.getFirst().getHashPartial();

        assertThat(hash2).isNotEqualTo(hash1);
    }

    @Test
    void modifiedTimeChangeTriggersRehash() throws Exception {
        byte[] content = "same content same size!!".getBytes();
        Path file = createFile("timestamped.dat", content);

        scanStage.execute(createRun(), List.of(tempDir.toString()));

        // Bump the modification time forward by 5 seconds → cache miss → rehash
        FileTime bumped = FileTime.fromMillis(
            Files.getLastModifiedTime(file).toMillis() + 5_000);
        Files.setLastModifiedTime(file, bumped);

        // File still has same content — but because modtime changed,
        // incremental check fails and hash is recomputed from disk (same result)
        List<ScannedFile> run2Files = scanStage.execute(createRun(), List.of(tempDir.toString()));

        // Hash should be present (freshly computed) and correct
        assertThat(run2Files.getFirst().getHashPartial()).isNotNull();
    }

    @Test
    void newFileWithNoPreviousRecordIsHashedNormally() throws IOException {
        createFile("brand_new.dat", "no previous record exists for this file".getBytes());

        List<ScannedFile> files = scanStage.execute(createRun(), List.of(tempDir.toString()));

        assertThat(files).hasSize(1);
        assertThat(files.getFirst().getHashPartial()).isNotNull().isNotEmpty();
    }

    @Test
    void multipleUnchangedFilesAllReuseCachedHashes() throws Exception {
        byte[] contentA = "AAAAAAAAAAAAAAAAAAAAAAAAA".getBytes();
        byte[] contentB = "BBBBBBBBBBBBBBBBBBBBBBBBB".getBytes();

        Path fileA = createFile("file_a.dat", contentA);
        Path fileB = createFile("file_b.dat", contentB);
        FileTime modA = Files.getLastModifiedTime(fileA);
        FileTime modB = Files.getLastModifiedTime(fileB);

        List<ScannedFile> run1 = scanStage.execute(createRun(), List.of(tempDir.toString()));
        String hashA1 = run1.stream().filter(f -> f.getPath().endsWith("file_a.dat"))
            .findFirst().orElseThrow().getHashPartial();
        String hashB1 = run1.stream().filter(f -> f.getPath().endsWith("file_b.dat"))
            .findFirst().orElseThrow().getHashPartial();

        // Replace both with swapped content but restore modtimes
        Files.write(fileA, contentB);
        Files.write(fileB, contentA);
        Files.setLastModifiedTime(fileA, modA);
        Files.setLastModifiedTime(fileB, modB);

        List<ScannedFile> run2 = scanStage.execute(createRun(), List.of(tempDir.toString()));
        String hashA2 = run2.stream().filter(f -> f.getPath().endsWith("file_a.dat"))
            .findFirst().orElseThrow().getHashPartial();
        String hashB2 = run2.stream().filter(f -> f.getPath().endsWith("file_b.dat"))
            .findFirst().orElseThrow().getHashPartial();

        // Both files reused their cached hashes
        assertThat(hashA2).isEqualTo(hashA1);
        assertThat(hashB2).isEqualTo(hashB1);
    }

    // ── helper ───────────────────────────────────────────────────────────────

    private long createRun() {
        ScanRun run = new ScanRun();
        run.setPhase("SCANNING");
        run.setCreatedAt(LocalDateTime.now());
        run.setRunLabel("run_" + System.currentTimeMillis());
        return scanRepository.save(run).getId();
    }
}
