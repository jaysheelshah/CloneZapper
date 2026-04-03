package com.clonezapper.p1;

import com.clonezapper.BaseTest;
import com.clonezapper.TestFixtures;
import com.clonezapper.engine.UnifiedScanner;
import com.clonezapper.model.ScanRun;
import com.clonezapper.model.preview.GroupPreviewRow;
import com.clonezapper.model.preview.MemberPreviewRow;
import com.clonezapper.service.PreviewService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P1 — PreviewService.buildMembers correctness.
 * Verifies per-member fields: role flags, paths, sizes, proposed archive paths.
 */
@Tag("P1")
class PreviewMembersTest extends BaseTest {

    @Autowired UnifiedScanner scanner;
    @Autowired PreviewService previewService;

    // ── Helper — run pipeline, get first group's members ─────────────────────

    private List<MemberPreviewRow> membersForFirstGroup() {
        ScanRun run = scanner.startScan(List.of(tempDir.toString()));
        List<GroupPreviewRow> groups = previewService.buildGroups(run.getId());
        assertThat(groups).isNotEmpty();
        return previewService.buildMembers(groups.getFirst().groupId());
    }

    // ── Member count ──────────────────────────────────────────────────────────

    @Test
    void twoFiles_buildMembersReturnsTwoRows() throws IOException {
        createFile("a/report.txt", TestFixtures.IDENTICAL_CONTENT);
        createFile("b/report.txt", TestFixtures.IDENTICAL_CONTENT);

        List<MemberPreviewRow> members = membersForFirstGroup();

        assertThat(members).hasSize(2);
    }

    @Test
    void threeFiles_buildMembersReturnsThreeRows() throws IOException {
        createFile("x/f.txt", TestFixtures.IDENTICAL_CONTENT);
        createFile("y/f.txt", TestFixtures.IDENTICAL_CONTENT);
        createFile("z/f.txt", TestFixtures.IDENTICAL_CONTENT);

        List<MemberPreviewRow> members = membersForFirstGroup();

        assertThat(members).hasSize(3);
    }

    // ── Canonical flag ────────────────────────────────────────────────────────

    @Test
    void exactlyOneCanonicalMemberPerGroup() throws IOException {
        createFile("a/f.bin", TestFixtures.IDENTICAL_CONTENT);
        createFile("b/f.bin", TestFixtures.IDENTICAL_CONTENT);
        createFile("c/f.bin", TestFixtures.IDENTICAL_CONTENT);

        List<MemberPreviewRow> members = membersForFirstGroup();

        long canonicalCount = members.stream().filter(MemberPreviewRow::isCanonical).count();
        assertThat(canonicalCount).isEqualTo(1);
    }

    @Test
    void canonicalMemberIsListedFirst() throws IOException {
        createFile("a/data.bin", TestFixtures.IDENTICAL_CONTENT);
        createFile("b/data.bin", TestFixtures.IDENTICAL_CONTENT);

        List<MemberPreviewRow> members = membersForFirstGroup();

        assertThat(members.getFirst().isCanonical()).isTrue();
    }

    // ── Proposed archive path ─────────────────────────────────────────────────

    @Test
    void canonicalMember_proposedArchivePathIsNull() throws IOException {
        createFile("orig/data.txt", TestFixtures.IDENTICAL_CONTENT);
        createFile("copy/data.txt", TestFixtures.IDENTICAL_CONTENT);

        List<MemberPreviewRow> members = membersForFirstGroup();
        MemberPreviewRow canonical = members.stream()
            .filter(MemberPreviewRow::isCanonical).findFirst().orElseThrow();

        assertThat(canonical.proposedArchivePath()).isNull();
    }

    @Test
    void duplicateMembers_allHaveNonNullProposedArchivePath() throws IOException {
        createFile("a/f.bin", TestFixtures.IDENTICAL_CONTENT);
        createFile("b/f.bin", TestFixtures.IDENTICAL_CONTENT);
        createFile("c/f.bin", TestFixtures.IDENTICAL_CONTENT);

        List<MemberPreviewRow> members = membersForFirstGroup();
        List<MemberPreviewRow> dupes = members.stream()
            .filter(m -> !m.isCanonical()).toList();

        assertThat(dupes).isNotEmpty();
        dupes.forEach(m -> assertThat(m.proposedArchivePath()).isNotBlank());
    }

    @Test
    void proposedArchivePath_containsScanRunId() throws IOException {
        createFile("orig/data.txt", TestFixtures.IDENTICAL_CONTENT);
        createFile("copy/data.txt", TestFixtures.IDENTICAL_CONTENT);

        ScanRun run = scanner.startScan(List.of(tempDir.toString()));
        List<GroupPreviewRow> groups = previewService.buildGroups(run.getId());
        List<MemberPreviewRow> members = previewService.buildMembers(groups.getFirst().groupId());

        MemberPreviewRow dupe = members.stream()
            .filter(m -> !m.isCanonical()).findFirst().orElseThrow();

        assertThat(dupe.proposedArchivePath()).contains("run_" + run.getId());
    }

    @Test
    void proposedArchivePath_containsOriginalFilename() throws IOException {
        createFile("orig/uniquename.txt", TestFixtures.IDENTICAL_CONTENT);
        createFile("copy/uniquename.txt", TestFixtures.IDENTICAL_CONTENT);

        List<MemberPreviewRow> members = membersForFirstGroup();
        MemberPreviewRow dupe = members.stream()
            .filter(m -> !m.isCanonical()).findFirst().orElseThrow();

        assertThat(dupe.proposedArchivePath()).contains("uniquename.txt");
    }

    // ── File metadata ─────────────────────────────────────────────────────────

    @Test
    void memberRows_pathAndSizeBytesArePopulated() throws IOException {
        createFile("a/data.bin", TestFixtures.IDENTICAL_CONTENT);
        createFile("b/data.bin", TestFixtures.IDENTICAL_CONTENT);

        List<MemberPreviewRow> members = membersForFirstGroup();

        members.forEach(m -> {
            assertThat(m.path()).isNotBlank();
            assertThat(m.sizeBytes()).isEqualTo(TestFixtures.IDENTICAL_CONTENT.length);
        });
    }

    @Test
    void memberRows_confidenceIsOneForExactMatch() throws IOException {
        createFile("a/f.bin", TestFixtures.IDENTICAL_CONTENT);
        createFile("b/f.bin", TestFixtures.IDENTICAL_CONTENT);

        List<MemberPreviewRow> members = membersForFirstGroup();

        members.forEach(m -> assertThat(m.confidence()).isEqualTo(1.0));
    }
}
