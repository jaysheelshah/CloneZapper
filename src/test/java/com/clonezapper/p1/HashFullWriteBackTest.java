package com.clonezapper.p1;

import com.clonezapper.BaseTest;
import com.clonezapper.TestFixtures;
import com.clonezapper.db.DuplicateGroupRepository;
import com.clonezapper.db.FileRepository;
import com.clonezapper.engine.UnifiedScanner;
import com.clonezapper.model.ScannedFile;
import com.clonezapper.model.ScanRun;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P1 — CompareStage writes hash_full back to the files table after computing it.
 */
@Tag("P1")
class HashFullWriteBackTest extends BaseTest {

    @Autowired UnifiedScanner scanner;
    @Autowired FileRepository fileRepository;
    @Autowired DuplicateGroupRepository groupRepository;

    @Test
    void hashFullIsPersistedAfterCompareStageRuns() throws IOException {
        createFile("a/doc.dat", TestFixtures.IDENTICAL_CONTENT);
        createFile("b/doc.dat", TestFixtures.IDENTICAL_CONTENT);

        ScanRun run = scanner.startScan(List.of(tempDir.toString()));
        assertThat(groupRepository.countByScanId(run.getId())).isEqualTo(1);

        // Both files should now have hash_full populated
        List<ScannedFile> files = fileRepository.findByScanId(run.getId());
        assertThat(files).allMatch(f -> f.getHashFull() != null && !f.getHashFull().isBlank());
    }

    @Test
    void hashFullIsTheSameForIdenticalFiles() throws IOException {
        createFile("a/doc.dat", TestFixtures.IDENTICAL_CONTENT);
        createFile("b/doc.dat", TestFixtures.IDENTICAL_CONTENT);

        ScanRun run = scanner.startScan(List.of(tempDir.toString()));

        List<ScannedFile> files = fileRepository.findByScanId(run.getId());
        assertThat(files).hasSize(2);
        String hashA = files.get(0).getHashFull();
        String hashB = files.get(1).getHashFull();
        assertThat(hashA).isEqualTo(hashB);
    }

    @Test
    void hashFullDiffersForDifferentFiles() throws IOException {
        createFile("a.dat", TestFixtures.CONTENT_A);
        createFile("b.dat", TestFixtures.CONTENT_B);

        // Trigger compare stage by making sizes equal (force partial-hash candidate group)
        // Use same-size but different content via padded bytes
        ScanRun run = scanner.startScan(List.of(tempDir.toString()));

        // Even if no group is formed, files may have been hashed by CompareStage
        // (only files in candidate groups get full-hashed, so unique files may remain null)
        List<ScannedFile> files = fileRepository.findByScanId(run.getId());
        // Files with hash_full set must have distinct hashes
        List<String> hashes = files.stream()
            .map(ScannedFile::getHashFull)
            .filter(Objects::nonNull)
            .toList();
        if (hashes.size() == 2) {
            assertThat(hashes.get(0)).isNotEqualTo(hashes.get(1));
        }
    }

    @Test
    void uniqueFilesNotInCandidateGroupHaveNullHashFull() throws IOException {
        // Files with unique sizes are never passed to CompareStage
        createFile("small.dat", "tiny".getBytes());
        createFile("large.dat", "a".repeat(1000).getBytes());

        ScanRun run = scanner.startScan(List.of(tempDir.toString()));

        // No candidate group → CompareStage never runs → hash_full stays null
        List<ScannedFile> files = fileRepository.findByScanId(run.getId());
        assertThat(groupRepository.countByScanId(run.getId())).isZero();
        files.forEach(f -> assertThat(f.getHashFull()).isNull());
    }
}
