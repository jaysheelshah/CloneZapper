package com.clonezapper.p1;

import com.clonezapper.engine.pipeline.ExecuteStage;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P1 — ExecuteStage.buildArchivePath pure-function tests.
 * No Spring context needed — this is a static utility method.
 * Verifies that source paths are correctly mirrored under the archive root.
 */
@Tag("P1")
class ArchivePathBuilderTest {

    // ── Windows paths ─────────────────────────────────────────────────────────

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void windowsDriveLetter_isStrippedAndUsedAsSubfolder() {
        Path result = ExecuteStage.buildArchivePath(
            "D:\\Archive", 7L, "C:\\Users\\jaysh\\Documents\\report.pdf");

        // Structure: D:\Archive\run_7\C\Users\jaysh\Documents\report.pdf
        assertThat(result.toString()).contains("run_7");
        assertThat(result.getFileName().toString()).isEqualTo("report.pdf");
        // Drive letter C appears as a directory segment, colon stripped
        assertThat(result.toString()).contains("C");
        assertThat(result.toString()).doesNotContain("C:");
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void windowsPath_deeplyNested_mirroredCorrectly() {
        Path result = ExecuteStage.buildArchivePath(
            "E:\\Backup", 42L, "C:\\Users\\admin\\Pictures\\2024\\Holidays\\photo.jpg");

        assertThat(result.getFileName().toString()).isEqualTo("photo.jpg");
        assertThat(result.toString()).contains("run_42");
        assertThat(result.toString()).contains("photo.jpg");
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void archiveRootAndSourceOnSameDrive_pathStillMirrored() {
        Path result = ExecuteStage.buildArchivePath(
            "C:\\CloneZapperArchive", 1L, "C:\\Users\\me\\file.txt");

        assertThat(result.toString()).contains("run_1");
        assertThat(result.getFileName().toString()).isEqualTo("file.txt");
    }

    // ── Unix paths ────────────────────────────────────────────────────────────

    @Test
    @EnabledOnOs(OS.LINUX)
    void unixPath_noLeadingDriveSegment() {
        Path result = ExecuteStage.buildArchivePath(
            "/mnt/backup", 5L, "/home/user/documents/report.pdf");

        assertThat(result.toString()).contains("run_5");
        assertThat(result.getFileName().toString()).isEqualTo("report.pdf");
        // No drive prefix for Unix paths
        assertThat(result.toString()).doesNotContain("/home/user/documents/report.pdf");
    }

    // ── Scan run ID in path ───────────────────────────────────────────────────

    @Test
    void scanRunIdIsAlwaysInPath() {
        long runId = 999L;
        Path result = ExecuteStage.buildArchivePath(
            System.getProperty("java.io.tmpdir"), runId, System.getProperty("user.home") + "/file.txt");

        assertThat(result.toString()).contains("run_" + runId);
    }

    // ── Determinism ───────────────────────────────────────────────────────────

    @Test
    void sameInputs_alwaysProduceSameOutput() {
        String archiveRoot = System.getProperty("java.io.tmpdir") + "/archive";
        String originalPath = System.getProperty("user.home") + "/docs/file.txt";
        long runId = 123L;

        Path result1 = ExecuteStage.buildArchivePath(archiveRoot, runId, originalPath);
        Path result2 = ExecuteStage.buildArchivePath(archiveRoot, runId, originalPath);

        assertThat(result1).isEqualTo(result2);
    }

    @Test
    void differentRunIds_produceDifferentPaths() {
        String archiveRoot = System.getProperty("java.io.tmpdir") + "/archive";
        String originalPath = System.getProperty("user.home") + "/docs/file.txt";

        Path result1 = ExecuteStage.buildArchivePath(archiveRoot, 1L, originalPath);
        Path result2 = ExecuteStage.buildArchivePath(archiveRoot, 2L, originalPath);

        assertThat(result1).isNotEqualTo(result2);
    }
}
