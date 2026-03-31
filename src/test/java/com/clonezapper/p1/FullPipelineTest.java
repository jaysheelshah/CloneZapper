package com.clonezapper.p1;

import com.clonezapper.BaseTest;
import com.clonezapper.TestFixtures;
import com.clonezapper.db.DuplicateGroupRepository;
import com.clonezapper.engine.UnifiedScanner;
import com.clonezapper.model.DuplicateGroup;
import com.clonezapper.model.ScanRun;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P1 — Full pipeline smoke test.
 * Verifies that UnifiedScanner runs all four stages end-to-end and produces
 * persisted duplicate groups from real files.
 */
@Tag("P1")
class FullPipelineTest extends BaseTest {

    @Autowired private UnifiedScanner scanner;
    @Autowired private DuplicateGroupRepository groupRepository;

    @Test
    void duplicateFilesAreDetectedEndToEnd() throws IOException {
        createFile("docs/report.pdf",      TestFixtures.IDENTICAL_CONTENT);
        createFile("downloads/report.pdf", TestFixtures.IDENTICAL_CONTENT);
        createFile("unique.txt",           TestFixtures.CONTENT_A);

        ScanRun run = scanner.startScan(List.of(tempDir.toString()));

        assertThat(run.getPhase()).isEqualTo("COMPLETE");

        List<DuplicateGroup> groups = groupRepository.findByScanId(run.getId());
        assertThat(groups).hasSize(1);

        DuplicateGroup group = groups.getFirst();
        assertThat(group.getMembers()).hasSize(2);
        assertThat(group.getConfidence()).isEqualTo(1.0);
        assertThat(group.getCanonicalFileId()).isNotNull();
    }

    @Test
    void noActualDuplicatesProducesNoGroups() throws IOException {
        createFile("a.txt", TestFixtures.CONTENT_A);
        createFile("b.txt", TestFixtures.CONTENT_B);

        ScanRun run = scanner.startScan(List.of(tempDir.toString()));

        assertThat(run.getPhase()).isEqualTo("COMPLETE");
        assertThat(groupRepository.countByScanId(run.getId())).isZero();
    }
}
