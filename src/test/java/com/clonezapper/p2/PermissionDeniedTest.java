package com.clonezapper.p2;

import com.clonezapper.BaseTest;
import com.clonezapper.TestFixtures;
import com.clonezapper.db.FileRepository;
import com.clonezapper.db.ScanRepository;
import com.clonezapper.engine.pipeline.ScanStage;
import com.clonezapper.model.ScanRun;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * P2 — Permission-denied files are skipped gracefully.
 * The scan must complete (not crash) and index all readable files.
 * Skipped on Windows since POSIX permissions aren't available there.
 */
@Tag("P2")
@DisabledOnOs(OS.WINDOWS)
class PermissionDeniedTest extends BaseTest {

    @Autowired private ScanStage scanStage;
    @Autowired private ScanRepository scanRepository;
    @Autowired private FileRepository fileRepository;

    @Test
    void scanCompletesWhenSomeFilesAreUnreadable() throws IOException {
        createFile("readable.txt", TestFixtures.CONTENT_A);

        // Create an unreadable file (POSIX only)
        Path unreadable = createFile("secret.txt", TestFixtures.CONTENT_B);
        Files.setPosixFilePermissions(unreadable, PosixFilePermissions.fromString("---------"));

        long runId = createRun();

        // Must not throw
        assertThatNoException().isThrownBy(
            () -> scanStage.execute(runId, List.of(tempDir.toString())));

        // Readable file should be indexed; unreadable should be skipped
        assertThat(fileRepository.countByScanId(runId)).isEqualTo(1);
    }

    private long createRun() {
        ScanRun run = new ScanRun();
        run.setPhase("SCANNING");
        run.setCreatedAt(LocalDateTime.now());
        run.setRunLabel("permission-test");
        return scanRepository.save(run).getId();
    }
}
