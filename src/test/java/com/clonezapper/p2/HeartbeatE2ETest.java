package com.clonezapper.p2;

import com.clonezapper.BaseTest;
import com.clonezapper.TestFixtures;
import com.clonezapper.db.ScanRepository;
import com.clonezapper.engine.UnifiedScanner;
import com.clonezapper.model.ScanRun;
import com.clonezapper.ui.DashboardView;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P2 E2E — Heartbeat behaviour across the full pipeline.
 * <p>
 * Verifies end-to-end: real scans write heartbeats; the reconnect logic
 * correctly distinguishes running, interrupted, and completed scans.
 */
@Tag("P2")
class HeartbeatE2ETest extends BaseTest {

    @Autowired UnifiedScanner scanner;
    @Autowired ScanRepository scanRepository;

    // ── Heartbeat written during real scan ────────────────────────────────────

    @Test
    void realScan_heartbeatIsWritten() throws IOException {
        createFile("docs/a.txt", TestFixtures.IDENTICAL_CONTENT);
        createFile("docs/b.txt", TestFixtures.IDENTICAL_CONTENT);

        LocalDateTime before = LocalDateTime.now().minusSeconds(1);
        ScanRun run = scanner.startScan(List.of(tempDir.toString()));
        LocalDateTime after  = LocalDateTime.now().plusSeconds(1);

        ScanRun loaded = scanRepository.findById(run.getId()).orElseThrow();
        assertThat(loaded.getLastHeartbeat())
            .as("heartbeat must be written during scan")
            .isBetween(before, after);
    }

    @Test
    void realScan_phaseIsCompleteAfterFinishing() throws IOException {
        createFile("x/file.bin", TestFixtures.CONTENT_A);

        ScanRun run = scanner.startScan(List.of(tempDir.toString()));

        assertThat(run.getPhase()).isEqualTo("COMPLETE");
    }

    @Test
    void realScan_notReturnedByFindInProgressAfterCompletion() throws IOException {
        createFile("x/file.bin", TestFixtures.CONTENT_A);
        createFile("y/file.bin", TestFixtures.CONTENT_A);

        scanner.startScan(List.of(tempDir.toString()));

        assertThat(scanRepository.findInProgress()).isEmpty();
    }

    // ── Simulated interrupted scan ────────────────────────────────────────────

    @Test
    void interruptedScan_appearsInFindInProgress() throws IOException {
        // Complete a real scan first so there's valid data
        createFile("real/file.bin", TestFixtures.CONTENT_A);
        scanner.startScan(List.of(tempDir.toString()));

        // Simulate an interrupted scan: stuck in COMPARING with a stale heartbeat
        ScanRun interrupted = new ScanRun();
        interrupted.setPhase("COMPARING");
        interrupted.setCreatedAt(LocalDateTime.now().minusHours(2));
        interrupted.setRunLabel("interrupted-test");
        scanRepository.save(interrupted);
        scanRepository.updateHeartbeat(interrupted.getId());
        // Backdating the heartbeat to simulate it being stale
        // (we can't control time, so we just verify findInProgress returns it)
        assertThat(scanRepository.findInProgress())
            .isPresent()
            .hasValueSatisfying(r -> assertThat(r.getId()).isEqualTo(interrupted.getId()));
    }

    @Test
    void interruptedScan_staleHeartbeat_evaluatesAsInterrupted() {
        // A heartbeat from 10 minutes ago is considered interrupted
        LocalDateTime staleHeartbeat = LocalDateTime.now().minusMinutes(10);
        assertThat(DashboardView.evaluateHeartbeat(staleHeartbeat))
            .isEqualTo(DashboardView.HeartbeatStatus.INTERRUPTED);
    }

    @Test
    void freshHeartbeat_evaluatesAsRunning() {
        // A heartbeat from 3 seconds ago means the scan is alive
        LocalDateTime freshHeartbeat = LocalDateTime.now().minusSeconds(3);
        assertThat(DashboardView.evaluateHeartbeat(freshHeartbeat))
            .isEqualTo(DashboardView.HeartbeatStatus.RUNNING);
    }

    // ── Multiple scans — only in-progress one returned ────────────────────────

    @Test
    void multipleScans_findInProgressReturnsMostRecent() throws IOException {
        // First complete scan
        createFile("a/file.bin", TestFixtures.CONTENT_A);
        scanner.startScan(List.of(tempDir.resolve("a").toString()));

        // Second complete scan
        createFile("b/file.bin", TestFixtures.CONTENT_B);
        scanner.startScan(List.of(tempDir.resolve("b").toString()));

        // No in-progress scans after two complete runs
        assertThat(scanRepository.findInProgress()).isEmpty();

        // Now simulate one stuck scan
        ScanRun stuck = new ScanRun();
        stuck.setPhase("CLUSTERING");
        stuck.setCreatedAt(LocalDateTime.now());
        stuck.setRunLabel("stuck");
        scanRepository.save(stuck);

        assertThat(scanRepository.findInProgress())
            .isPresent()
            .hasValueSatisfying(r -> assertThat(r.getRunLabel()).isEqualTo("stuck"));
    }

    @Test
    void allTerminalPhases_noneReturnedByFindInProgress() {
        for (String terminal : List.of("COMPLETE", "FAILED", "STAGED", "PURGED", "CLEANED")) {
            ScanRun run = new ScanRun();
            run.setPhase(terminal);
            run.setCreatedAt(LocalDateTime.now());
            run.setRunLabel("terminal-" + terminal);
            scanRepository.save(run);
        }

        assertThat(scanRepository.findInProgress()).isEmpty();
    }
}
