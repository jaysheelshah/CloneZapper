package com.clonezapper.p2;

import com.clonezapper.BaseTest;
import com.clonezapper.db.FileRepository;
import com.clonezapper.db.ScanRepository;
import com.clonezapper.engine.pipeline.CompareStage;
import com.clonezapper.engine.pipeline.ScanStage;
import com.clonezapper.model.ScanRun;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P2 — CompareStage.executeNearDup reuses cached minhash_signature for unchanged files.
 * Proof strategy: run near-dup to populate minhash_signature, then overwrite the file
 * content (same size + restored modtime so ScanStage copies the cached sig). A second
 * executeNearDup pass must emit the cached fingerprint — not a freshly computed one.
 * Requires: OS probeContentType returns "text/html" for .html files (Windows ✓).
 */
@Tag("P2")
class MinhashIncrementalTest extends BaseTest {

    @Autowired ScanStage scanStage;
    @Autowired CompareStage compareStage;
    @Autowired ScanRepository scanRepository;
    @Autowired FileRepository fileRepository;

    private static final String BODY_A =
        "Quarterly earnings show a record profit for the technology division. " +
        "Sales grew by twenty percent year over year. Management is optimistic.";

    private static final String BODY_B =
        "Annual financial review reveals significant growth in digital services. " +
        "Revenue climbed eighteen percent above forecast. Board approved expansion.";

    @Test
    void executeNearDupReusesCachedSignatureForUnchangedFile() throws Exception {
        Path fileA = createFile("a.html", html(BODY_A));
        createFile("b.html", html(BODY_B));

        // Run 1 — computes and persists minhash_signature for both files
        long run1Id = createRun("run1");
        scanStage.execute(run1Id, List.of(tempDir.toString()));
        compareStage.executeNearDup(run1Id);

        byte[] sig1 = fileRepository.findByScanId(run1Id).stream()
            .filter(f -> f.getPath().endsWith("a.html"))
            .findFirst().orElseThrow()
            .getMinhashSignature();
        assertThat(sig1).isNotNull().isNotEmpty();

        // Overwrite file A with entirely different content but keep same size + modtime
        String poisonBody = "Z".repeat(BODY_A.length());
        String poisonHtml = html(poisonBody);
        byte[] originalBytes = Files.readAllBytes(fileA);
        FileTime originalModTime = Files.getLastModifiedTime(fileA);

        // Pad or trim poison to match original byte length
        byte[] poisonBytes = Arrays.copyOf(poisonHtml.getBytes(), originalBytes.length);
        Files.write(fileA, poisonBytes);
        Files.setLastModifiedTime(fileA, originalModTime);

        // Run 2 — ScanStage should copy minhash_signature from run1 for file A
        long run2Id = createRun("run2");
        scanStage.execute(run2Id, List.of(tempDir.toString()));
        compareStage.executeNearDup(run2Id);

        byte[] sig2 = fileRepository.findByScanId(run2Id).stream()
            .filter(f -> f.getPath().endsWith("a.html"))
            .findFirst().orElseThrow()
            .getMinhashSignature();

        assertThat(sig2)
            .as("executeNearDup should reuse cached minhash_signature from run1")
            .isEqualTo(sig1);
    }

    @Test
    void minhashSignatureIsNullBeforeNearDupPassRuns() throws IOException {
        createFile("a.html", html(BODY_A));
        createFile("b.html", html(BODY_B));

        long runId = createRun("fresh");
        scanStage.execute(runId, List.of(tempDir.toString()));

        // Before executeNearDup — no signatures should exist yet
        fileRepository.findByScanId(runId)
            .forEach(f -> assertThat(f.getMinhashSignature()).isNull());
    }

    @Test
    void executeNearDupWritesSignaturesForBothFiles() throws IOException {
        createFile("a.html", html(BODY_A));
        createFile("b.html", html(BODY_B));

        long runId = createRun("persist");
        scanStage.execute(runId, List.of(tempDir.toString()));
        compareStage.executeNearDup(runId);

        long withSig = fileRepository.findByScanId(runId).stream()
            .filter(f -> f.getMinhashSignature() != null && f.getMinhashSignature().length > 0)
            .count();
        assertThat(withSig).isEqualTo(2);
    }

    @Test
    void minhashCacheIsInvalidatedWhenFileSizeChanges() throws IOException {
        createFile("a.html", html(BODY_A));
        createFile("b.html", html(BODY_B));

        long run1Id = createRun("run1");
        scanStage.execute(run1Id, List.of(tempDir.toString()));
        compareStage.executeNearDup(run1Id);

        byte[] sig1 = fileRepository.findByScanId(run1Id).stream()
            .filter(f -> f.getPath().endsWith("a.html"))
            .findFirst().orElseThrow()
            .getMinhashSignature();
        assertThat(sig1).isNotNull();

        // Change file size — cache must not be copied
        Path fileA = tempDir.resolve("a.html");
        Files.writeString(fileA, html(BODY_A + " Extra sentence added to change file size."));

        long run2Id = createRun("run2");
        scanStage.execute(run2Id, List.of(tempDir.toString()));
        compareStage.executeNearDup(run2Id);

        byte[] sig2 = fileRepository.findByScanId(run2Id).stream()
            .filter(f -> f.getPath().endsWith("a.html"))
            .findFirst().orElseThrow()
            .getMinhashSignature();

        // Signature should be freshly computed — not the same as the cached one
        // (We can only assert it's non-null; content will differ because text changed)
        assertThat(sig2).isNotNull().isNotEmpty();
        // The cached sig belonged to a shorter file — it should not carry over
        assertThat(sig2).isNotEqualTo(sig1);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private String html(String body) {
        return "<html><body><p>" + body + "</p></body></html>";
    }

    private long createRun(String label) {
        ScanRun run = new ScanRun();
        run.setPhase("SCANNING");
        run.setCreatedAt(LocalDateTime.now());
        run.setRunLabel(label);
        return scanRepository.save(run).getId();
    }
}
