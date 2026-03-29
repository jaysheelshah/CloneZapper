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
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P2 — Special characters in filenames are handled without errors.
 * Spaces, unicode, brackets, dashes, dots, parentheses must all be indexed.
 */
@Tag("P2")
class SpecialCharFilenameTest extends BaseTest {

    @Autowired private ScanStage scanStage;
    @Autowired private ScanRepository scanRepository;
    @Autowired private FileRepository fileRepository;

    @Test
    @DisabledOnOs(OS.WINDOWS) // Unicode filenames behave differently on Windows FS
    void filesWithSpecialCharactersAreIndexed() throws IOException {
        createFile("files/résumé 2024.pdf", TestFixtures.CONTENT_A);
        createFile("files/Annual Report (Final) [v3].docx", TestFixtures.CONTENT_B);
        createFile("files/photo — holiday.jpg", TestFixtures.randomContent(512));

        long runId = createRun();
        scanStage.execute(runId, List.of(tempDir.resolve("files").toString()));

        assertThat(fileRepository.countByScanId(runId)).isEqualTo(3);
    }

    @Test
    void filesWithSpacesInNameAreIndexed() throws IOException {
        createFile("my documents/tax return 2024.pdf", TestFixtures.CONTENT_A);
        createFile("my documents/meeting notes jan.txt", TestFixtures.CONTENT_B);

        long runId = createRun();
        scanStage.execute(runId, List.of(tempDir.resolve("my documents").toString()));

        assertThat(fileRepository.countByScanId(runId)).isEqualTo(2);
    }

    private long createRun() {
        ScanRun run = new ScanRun();
        run.setPhase("SCANNING");
        run.setCreatedAt(LocalDateTime.now());
        run.setRunLabel("special-char-test");
        return scanRepository.save(run).getId();
    }
}
