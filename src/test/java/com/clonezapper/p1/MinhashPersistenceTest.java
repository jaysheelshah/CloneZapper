package com.clonezapper.p1;

import com.clonezapper.BaseTest;
import com.clonezapper.db.FileRepository;
import com.clonezapper.engine.UnifiedScanner;
import com.clonezapper.model.ScannedFile;
import com.clonezapper.model.ScanRun;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P1 — CompareStage.executeNearDup writes minhash_signature back to the files table.
 * Requires: OS probeContentType returns "text/html" for .html files (Windows ✓).
 */
@Tag("P1")
class MinhashPersistenceTest extends BaseTest {

    @Autowired UnifiedScanner scanner;
    @Autowired FileRepository fileRepository;

    private static final String BODY =
        "The annual report shows strong growth across all product lines. " +
        "Revenue increased by fifteen percent compared to the previous fiscal year. " +
        "Operating margins improved due to strategic cost reduction initiatives.";

    @Test
    void minhashSignatureIsPersistedAfterNearDupPassRuns() throws IOException {
        createFile("a.html", html(BODY + " Version alpha."));
        createFile("b.html", html(BODY + " Version beta."));

        ScanRun run = scanner.startScan(List.of(tempDir.toString()));

        List<ScannedFile> files = fileRepository.findByScanId(run.getId());
        // Both HTML files should have minhash_signature written by executeNearDup
        long withMinhash = files.stream()
            .filter(f -> f.getMinhashSignature() != null && f.getMinhashSignature().length > 0)
            .count();
        assertThat(withMinhash).isEqualTo(2);
    }

    @Test
    void minhashSignatureHasExpectedLength() throws IOException {
        createFile("a.html", html(BODY + " Version A."));
        createFile("b.html", html(BODY + " Version B."));

        ScanRun run = scanner.startScan(List.of(tempDir.toString()));

        fileRepository.findByScanId(run.getId()).stream()
            .filter(f -> f.getMinhashSignature() != null)
            .forEach(f ->
                // 128 longs × 8 bytes = 1024 bytes
                assertThat(f.getMinhashSignature().length).isEqualTo(1024));
    }

    @Test
    void nonDocumentFilesDoNotHaveMinhashSignature() throws IOException {
        // Plain binary files go through GenericHandler which doesn't persist minhash
        createFile("a.dat", "AAAAAAAAAAAAAAAAAA".getBytes());
        createFile("b.dat", "AAAAAAAAAAAAAAAAAA".getBytes()); // identical

        ScanRun run = scanner.startScan(List.of(tempDir.toString()));

        fileRepository.findByScanId(run.getId())
            .forEach(f -> assertThat(f.getMinhashSignature()).isNull());
    }

    private String html(String body) {
        return "<html><body><p>" + body + "</p></body></html>";
    }
}
