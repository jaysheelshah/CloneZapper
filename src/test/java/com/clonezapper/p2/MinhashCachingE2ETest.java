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
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2E: minhash_signature is written by the first scan and reused by the second scan.
 * Two runs over the same HTML near-duplicate files:
 *   Run 1 — computes and persists minhash_signature for both files
 *   Run 2 — ScanStage copies cached minhash_signature; executeNearDup skips recomputing
 * Requires: OS probeContentType returns "text/html" for .html files (Windows ✓).
 */
@Tag("P2")
class MinhashCachingE2ETest extends BaseTest {

    @Autowired UnifiedScanner scanner;
    @Autowired FileRepository fileRepository;
    @Autowired DuplicateGroupRepository groupRepository;

    private static final String SHARED_BODY =
        "The quarterly earnings report reveals substantial revenue growth across all divisions. " +
        "Profit margins improved following targeted cost optimisation initiatives in Q2 and Q3. " +
        "The executive leadership team approved a new capital allocation strategy for the next cycle. " +
        "Customer acquisition metrics exceeded targets set at the beginning of the fiscal year. " +
        "International operations contributed a growing share of total consolidated revenue.";

    @Test
    void minhashSignaturePersistedAfterFirstScan() throws IOException {
        createFile("a.html", html(SHARED_BODY + " Document alpha."));
        createFile("b.html", html(SHARED_BODY + " Document beta."));

        ScanRun run1 = scanner.startScan(List.of(tempDir.toString()));
        assertThat(run1.getPhase()).isEqualTo("COMPLETE");

        // Both HTML files must have minhash_signature written after run 1
        List<ScannedFile> files1 = fileRepository.findByScanId(run1.getId());
        long withSig = files1.stream()
            .filter(f -> f.getMinhashSignature() != null && f.getMinhashSignature().length > 0)
            .count();
        assertThat(withSig).isEqualTo(2);
    }

    @Test
    void nearDupGroupDetectedOnBothRuns() throws IOException {
        createFile("a.html", html(SHARED_BODY + " Edition A."));
        createFile("b.html", html(SHARED_BODY + " Edition B."));

        ScanRun run1 = scanner.startScan(List.of(tempDir.toString()));
        ScanRun run2 = scanner.startScan(List.of(tempDir.toString()));

        // A near-dup group should be produced on both runs
        assertThat(groupRepository.countByScanId(run1.getId()))
            .as("Run 1 should detect the near-dup group").isGreaterThanOrEqualTo(1);
        assertThat(groupRepository.countByScanId(run2.getId()))
            .as("Run 2 should also detect the near-dup group").isGreaterThanOrEqualTo(1);
    }

    @Test
    void cachedSignatureMatchesRecomputedSignatureForSameContent() throws IOException {
        createFile("a.html", html(SHARED_BODY + " Version one."));
        createFile("b.html", html(SHARED_BODY + " Version two."));

        ScanRun run1 = scanner.startScan(List.of(tempDir.toString()));
        ScanRun run2 = scanner.startScan(List.of(tempDir.toString()));

        byte[] sigRun1 = fileRepository.findByScanId(run1.getId()).stream()
            .filter(f -> f.getPath().endsWith("a.html"))
            .findFirst().orElseThrow()
            .getMinhashSignature();

        byte[] sigRun2 = fileRepository.findByScanId(run2.getId()).stream()
            .filter(f -> f.getPath().endsWith("a.html"))
            .findFirst().orElseThrow()
            .getMinhashSignature();

        assertThat(sigRun1).isNotNull();
        assertThat(sigRun2).isNotNull();

        // Signatures must match: either same bytes (cached) or identical recomputation
        assertThat(sigRun2)
            .as("Signature for unchanged file must be consistent across runs")
            .isEqualTo(sigRun1);
    }

    @Test
    void secondScanReusesCachedSignatureForUnchangedFile() throws Exception {
        Path fileA = createFile("a.html", html(SHARED_BODY + " Alpha version."));
        createFile("b.html", html(SHARED_BODY + " Beta version."));

        // Run 1 — computes and persists minhash_signature
        ScanRun run1 = scanner.startScan(List.of(tempDir.toString()));
        assertThat(run1.getPhase()).isEqualTo("COMPLETE");

        byte[] sig1 = fileRepository.findByScanId(run1.getId()).stream()
            .filter(f -> f.getPath().endsWith("a.html"))
            .findFirst().orElseThrow()
            .getMinhashSignature();
        assertThat(sig1).isNotNull().isNotEmpty();

        // Overwrite file A with different content, but keep same size + restore modtime
        // so ScanStage's incremental check passes and copies the cached sig
        byte[] originalBytes = Files.readAllBytes(fileA);
        FileTime originalModTime = Files.getLastModifiedTime(fileA);
        byte[] poisoned = Arrays.copyOf("Z".repeat(originalBytes.length).getBytes(), originalBytes.length);
        Files.write(fileA, poisoned);
        Files.setLastModifiedTime(fileA, originalModTime);

        // Run 2 — should copy minhash_signature from run1 for file A
        ScanRun run2 = scanner.startScan(List.of(tempDir.toString()));
        assertThat(run2.getPhase()).isEqualTo("COMPLETE");

        byte[] sig2 = fileRepository.findByScanId(run2.getId()).stream()
            .filter(f -> f.getPath().endsWith("a.html"))
            .findFirst().orElseThrow()
            .getMinhashSignature();

        assertThat(sig2)
            .as("Run 2 should have reused the cached minhash_signature from run 1")
            .isEqualTo(sig1);
    }

    @Test
    void nonHtmlFilesHaveNoMinhashSignatureAfterFullPipeline() throws IOException {
        // Binary .dat files use GenericHandler — no minhash computed
        createFile("a.dat", "AAAAAAAAAAAAAAAAAAA".getBytes());
        createFile("b.dat", "AAAAAAAAAAAAAAAAAAA".getBytes());

        ScanRun run = scanner.startScan(List.of(tempDir.toString()));
        assertThat(run.getPhase()).isEqualTo("COMPLETE");

        fileRepository.findByScanId(run.getId())
            .forEach(f -> assertThat(f.getMinhashSignature()).isNull());
    }

    // ── helper ───────────────────────────────────────────────────────────────

    private String html(String body) {
        return "<html><body><p>" + body + "</p></body></html>";
    }
}
