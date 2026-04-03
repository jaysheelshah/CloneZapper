package com.clonezapper.p1;

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
 * P1 — Heartbeat persistence and staleness evaluation.
 * <p>
 * Verifies:
 *  - last_heartbeat is written to the DB during a real scan
 *  - findInProgress returns a scan in a non-terminal phase
 *  - findInProgress returns empty for a completed scan
 *  - evaluateHeartbeat classifies timestamps correctly against thresholds
 */
@Tag("P1")
class HeartbeatPersistenceTest extends BaseTest {

    @Autowired UnifiedScanner scanner;
    @Autowired ScanRepository scanRepository;

    // ── Heartbeat written to DB ───────────────────────────────────────────────

    @Test
    void completedScan_lastHeartbeatIsPopulated() throws IOException {
        createFile("a/file.txt", TestFixtures.IDENTICAL_CONTENT);
        createFile("b/file.txt", TestFixtures.IDENTICAL_CONTENT);

        ScanRun run = scanner.startScan(List.of(tempDir.toString()));

        ScanRun loaded = scanRepository.findById(run.getId()).orElseThrow();
        assertThat(loaded.getLastHeartbeat())
            .as("last_heartbeat must be written during scan")
            .isNotNull();
    }

    @Test
    void completedScan_lastHeartbeatIsRecent() throws IOException {
        createFile("a/file.txt", TestFixtures.CONTENT_A);

        LocalDateTime before = LocalDateTime.now().minusSeconds(2);
        ScanRun run = scanner.startScan(List.of(tempDir.toString()));
        LocalDateTime after = LocalDateTime.now().plusSeconds(2);

        ScanRun loaded = scanRepository.findById(run.getId()).orElseThrow();
        assertThat(loaded.getLastHeartbeat())
            .isBetween(before, after);
    }

    // ── findInProgress ────────────────────────────────────────────────────────

    @Test
    void completedScan_notReturnedByFindInProgress() throws IOException {
        createFile("a/file.txt", TestFixtures.CONTENT_A);

        scanner.startScan(List.of(tempDir.toString()));

        // COMPLETE is terminal — should not appear in findInProgress
        assertThat(scanRepository.findInProgress()).isEmpty();
    }

    @Test
    void scanInNonTerminalPhase_returnedByFindInProgress() {
        // Manually insert a scan stuck in SCANNING phase (simulates interrupted scan)
        ScanRun stuck = new ScanRun();
        stuck.setPhase("SCANNING");
        stuck.setCreatedAt(LocalDateTime.now());
        stuck.setRunLabel("stuck-test");
        scanRepository.save(stuck);

        assertThat(scanRepository.findInProgress())
            .isPresent()
            .hasValueSatisfying(r -> assertThat(r.getPhase()).isEqualTo("SCANNING"));
    }

    @Test
    void failedScan_notReturnedByFindInProgress() throws IOException {
        createFile("a/file.txt", TestFixtures.CONTENT_A);

        ScanRun run = scanner.startScan(List.of(tempDir.toString()));
        // Manually force phase to FAILED to simulate a crash
        scanRepository.updatePhase(run.getId(), "FAILED");

        assertThat(scanRepository.findInProgress()).isEmpty();
    }

    // ── evaluateHeartbeat ─────────────────────────────────────────────────────

    @Test
    void evaluateHeartbeat_nullTimestamp_returnsUnknown() {
        assertThat(DashboardView.evaluateHeartbeat(null))
            .isEqualTo(DashboardView.HeartbeatStatus.UNKNOWN);
    }

    @Test
    void evaluateHeartbeat_veryRecent_returnsRunning() {
        LocalDateTime justNow = LocalDateTime.now().minusSeconds(5);
        assertThat(DashboardView.evaluateHeartbeat(justNow))
            .isEqualTo(DashboardView.HeartbeatStatus.RUNNING);
    }

    @Test
    void evaluateHeartbeat_fifteenSecondsAgo_returnsRunning() {
        LocalDateTime recent = LocalDateTime.now().minusSeconds(15);
        assertThat(DashboardView.evaluateHeartbeat(recent))
            .isEqualTo(DashboardView.HeartbeatStatus.RUNNING);
    }

    @Test
    void evaluateHeartbeat_justPastThirtySeconds_returnsResuming() {
        LocalDateTime borderline = LocalDateTime.now().minusSeconds(35);
        assertThat(DashboardView.evaluateHeartbeat(borderline))
            .isEqualTo(DashboardView.HeartbeatStatus.RESUMING);
    }

    @Test
    void evaluateHeartbeat_twoMinutesAgo_returnsResuming() {
        LocalDateTime twoMins = LocalDateTime.now().minusMinutes(2);
        assertThat(DashboardView.evaluateHeartbeat(twoMins))
            .isEqualTo(DashboardView.HeartbeatStatus.RESUMING);
    }

    @Test
    void evaluateHeartbeat_justPastFiveMinutes_returnsInterrupted() {
        LocalDateTime stale = LocalDateTime.now().minusSeconds(310);
        assertThat(DashboardView.evaluateHeartbeat(stale))
            .isEqualTo(DashboardView.HeartbeatStatus.INTERRUPTED);
    }

    @Test
    void evaluateHeartbeat_oneHourAgo_returnsInterrupted() {
        LocalDateTime veryStale = LocalDateTime.now().minusHours(1);
        assertThat(DashboardView.evaluateHeartbeat(veryStale))
            .isEqualTo(DashboardView.HeartbeatStatus.INTERRUPTED);
    }

    // ── updateHeartbeat ───────────────────────────────────────────────────────

    @Test
    void updateHeartbeat_writesCurrentTimestampToDb() {
        ScanRun run = new ScanRun();
        run.setPhase("SCANNING");
        run.setCreatedAt(LocalDateTime.now());
        run.setRunLabel("hb-write-test");
        scanRepository.save(run);

        LocalDateTime before = LocalDateTime.now().minusSeconds(1);
        scanRepository.updateHeartbeat(run.getId());
        LocalDateTime after = LocalDateTime.now().plusSeconds(1);

        ScanRun loaded = scanRepository.findById(run.getId()).orElseThrow();
        assertThat(loaded.getLastHeartbeat()).isBetween(before, after);
    }

    @Test
    void updateHeartbeat_canBeCalledMultipleTimes() {
        ScanRun run = new ScanRun();
        run.setPhase("SCANNING");
        run.setCreatedAt(LocalDateTime.now());
        run.setRunLabel("hb-multi-test");
        scanRepository.save(run);

        scanRepository.updateHeartbeat(run.getId());
        LocalDateTime first = scanRepository.findById(run.getId()).orElseThrow().getLastHeartbeat();

        scanRepository.updateHeartbeat(run.getId());
        LocalDateTime second = scanRepository.findById(run.getId()).orElseThrow().getLastHeartbeat();

        // Second heartbeat is same or later than first
        assertThat(second).isAfterOrEqualTo(first);
    }
}
