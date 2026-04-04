package com.clonezapper.p2;

import com.clonezapper.BaseTest;
import com.clonezapper.db.DuplicateGroupRepository;
import com.clonezapper.engine.UnifiedScanner;
import com.clonezapper.engine.pipeline.ClusterStage;
import com.clonezapper.model.DuplicateGroup;
import com.clonezapper.model.ScanRun;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2E: near-duplicate image detection.
 * <p>Two visually similar PNG images (same gradient, one row inverted) should be
 * detected as near-dup pair and land in the review queue (0.5 ≤ sim < 0.95).
 * <p>Requires: OS probeContentType returns "image/png" for .png files (Windows ✓).
 */
@Tag("P2")
class NearDupImageE2ETest extends BaseTest {

    @Autowired UnifiedScanner scanner;
    @Autowired DuplicateGroupRepository groupRepository;

    @Test
    void similarImagesDetectedAsNearDup() throws IOException {
        // Rows 0-6: horizontal gradient (same); row 7: inverted → dHash ~0.875
        writeGradient("img_a.png", false);
        writeGradient("img_b.png", true);   // last row inverted

        ScanRun run = scanner.startScan(List.of(tempDir.toString()));
        assertThat(run.getPhase()).isEqualTo("COMPLETE");

        List<DuplicateGroup> groups = groupRepository.findByScanId(run.getId());
        assertThat(groups).as("near-dup image group expected").hasSize(1);

        DuplicateGroup group = groups.getFirst();
        assertThat(group.getStrategy()).isEqualTo("near-dup-image");
        assertThat(group.getConfidence()).isBetween(0.5, 1.0);

        // Similarity < 1.0 → review queue
        List<DuplicateGroup> review = groupRepository.findReviewQueueByScanId(
            run.getId(), ClusterStage.DEFAULT_CONFIDENCE_THRESHOLD);
        assertThat(review).hasSize(1);
    }

    @Test
    void identicalImagesAreExactMatch() throws IOException {
        // Two PNG files with the exact same content → exact-hash finds them
        writeGradient("img_a.png", false);
        writeGradient("img_b.png", false);  // identical

        ScanRun run = scanner.startScan(List.of(tempDir.toString()));
        assertThat(run.getPhase()).isEqualTo("COMPLETE");

        List<DuplicateGroup> groups = groupRepository.findByScanId(run.getId());
        assertThat(groups).hasSize(1);
        assertThat(groups.getFirst().getConfidence()).isEqualTo(1.0);
    }

    @Test
    void totallyDifferentImagesProduceNoGroup() throws IOException {
        // Black→white gradient (dHash ≈ 0x00) vs white→black gradient (dHash ≈ 0xFF)
        // Hamming distance = 64 bits → similarity = 0.0 → no pair emitted
        writeGradient("img_a.png", false);
        writeFullyInvertedGradient("img_b.png");

        ScanRun run = scanner.startScan(List.of(tempDir.toString()));
        assertThat(run.getPhase()).isEqualTo("COMPLETE");
        assertThat(groupRepository.countByScanId(run.getId())).isZero();
    }

    @Test
    void threeImagesOneUniqueProducesOneGroup() throws IOException {
        // img_a and img_b share 7/8 dHash rows (row 7 inverted in img_b)
        writeGradient("img_a.png", false);
        writeGradient("img_b.png", true);
        // Fully inverted gradient: dHash ≈ all 0xFF → 0% similarity with the black→white gradients
        writeFullyInvertedGradient("unique.png");

        ScanRun run = scanner.startScan(List.of(tempDir.toString()));
        assertThat(run.getPhase()).isEqualTo("COMPLETE");

        List<DuplicateGroup> groups = groupRepository.findByScanId(run.getId());
        // Only img_a + img_b form a near-dup group; unique is not included
        assertThat(groups).hasSize(1);
        assertThat(groups.getFirst().getMembers()).hasSize(2);
    }

    // ── image helpers ─────────────────────────────────────────────────────────

    /**
     * Creates a 256×8 PNG with a horizontal gradient. If {@code invertLastRow},
     * row 7 has an inverted gradient — producing a known dHash difference.
     * Height = 8 matches dHash's output height exactly, so each source row maps
     * 1-to-1 to a dHash row and the inverted row 7 is reliably detected.
     * 256×8 = 2048 pixels > MIN_IMAGE_PIXELS (1024), so the pixel-dimension
     * guard in ImageHandler does not skip these images.
     */
    private void writeGradient(String name, boolean invertLastRow) throws IOException {
        BufferedImage img = new BufferedImage(256, 8, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < 256; x++) {
            int level = x; // 0..255 black→white gradient
            for (int y = 0; y < 8; y++) {
                int l = (invertLastRow && y == 7) ? (255 - level) : level;
                img.setRGB(x, y, new Color(l, l, l).getRGB());
            }
        }
        Path out = tempDir.resolve(name);
        ImageIO.write(img, "png", out.toFile());
    }

    /** White→black gradient: dHash ≈ all 0x00 — maximally different from black→white. */
    private void writeFullyInvertedGradient(String name) throws IOException {
        BufferedImage img = new BufferedImage(256, 8, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < 256; x++) {
            int level = 255 - x; // bright on left, dark on right
            for (int y = 0; y < 8; y++) {
                img.setRGB(x, y, new Color(level, level, level).getRGB());
            }
        }
        Path out = tempDir.resolve(name);
        ImageIO.write(img, "png", out.toFile());
    }
}
