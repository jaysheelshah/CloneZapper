package com.clonezapper.engine;

import com.clonezapper.db.FileRepository;
import com.clonezapper.db.ScanRepository;
import com.clonezapper.engine.pipeline.CandidateStage;
import com.clonezapper.engine.pipeline.ClusterStage;
import com.clonezapper.engine.pipeline.CompareStage;
import com.clonezapper.engine.pipeline.ScanStage;
import com.clonezapper.model.ScanRun;
import com.clonezapper.model.ScannedFile;
import com.clonezapper.service.ScanProgressTracker;
import com.clonezapper.service.ScanSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Orchestrates the full deduplication pipeline across all configured providers.
 * Stages executed:
 *   ① Scan      — enumerate + index files, compute partial hashes
 *   ② Candidates — filter by size then partial hash
 *   ③ Compare   — confirm with full hash (exact) + content fingerprints (near-dup)
 *   ④ Cluster   — Union-Find grouping, canonical selection, persistence
 * The archive root is automatically excluded from all scans to prevent
 * re-indexing already-staged files.
 * An optional {@link Consumer}{@code <String>} callback receives phase names
 * as the pipeline progresses — used by the UI for live progress updates.
 */
@Service
public class UnifiedScanner {

    private static final Logger log = LoggerFactory.getLogger(UnifiedScanner.class);
    private static final DateTimeFormatter RUN_LABEL_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final ScanRepository scanRepository;
    private final FileRepository fileRepository;
    private final ScanStage scanStage;
    private final CandidateStage candidateStage;
    private final CompareStage compareStage;
    private final ClusterStage clusterStage;
    private final ScanProgressTracker progressTracker;
    private final ScanSettings scanSettings;
    private final String defaultArchiveRoot;

    public UnifiedScanner(ScanRepository scanRepository,
                          FileRepository fileRepository,
                          ScanStage scanStage,
                          CandidateStage candidateStage,
                          CompareStage compareStage,
                          ClusterStage clusterStage,
                          ScanProgressTracker progressTracker,
                          ScanSettings scanSettings,
                          @Value("${clonezapper.archive.root}") String defaultArchiveRoot) {
        this.scanRepository = scanRepository;
        this.fileRepository = fileRepository;
        this.scanStage = scanStage;
        this.candidateStage = candidateStage;
        this.compareStage = compareStage;
        this.clusterStage = clusterStage;
        this.progressTracker = progressTracker;
        this.scanSettings = scanSettings;
        this.defaultArchiveRoot = defaultArchiveRoot;
    }

    public String getDefaultArchiveRoot() { return defaultArchiveRoot; }

    /** Runs the full pipeline with no progress callback. */
    public ScanRun startScan(List<String> rootPaths) {
        return startScan(rootPaths, defaultArchiveRoot, phase -> {});
    }

    /** Runs the full pipeline with a progress callback and the configured default archive root. */
    public ScanRun startScan(List<String> rootPaths, Consumer<String> onPhase) {
        return startScan(rootPaths, defaultArchiveRoot, onPhase);
    }

    /**
     * Runs the full pipeline over the given local paths.
     *
     * @param rootPaths   list of absolute local paths to scan
     * @param archiveRoot destination folder for archived duplicates
     * @param onPhase     callback invoked with the phase name at each stage transition
     * @return            the completed ScanRun record
     */
    public ScanRun startScan(List<String> rootPaths, String archiveRoot, Consumer<String> onPhase) {
        ScanRun run = new ScanRun();
        run.setPhase("SCANNING");
        run.setCreatedAt(LocalDateTime.now());
        run.setRunLabel("run_" + LocalDateTime.now().format(RUN_LABEL_FORMAT));
        run.setArchiveRoot(archiveRoot);
        scanRepository.save(run);

        // ── Skip-if-unchanged check ───────────────────────────────────────────
        // Take a cheap filesystem snapshot (no hashes, no DB writes) and compare
        // it to the previous completed scan.  If nothing changed, discard the
        // stub ScanRun and return the previous result immediately.
        Optional<ScanRun> prevComplete = scanRepository.findLatestComplete();
        if (prevComplete.isPresent()) {
            log.info("Quick snapshot — checking for changes since scan {}", prevComplete.get().getId());
            Map<String, String> currentSnapshot  = scanStage.quickSnapshot(rootPaths, Set.of(archiveRoot));
            Map<String, String> previousSnapshot = fileRepository.loadSnapshotByScanId(prevComplete.get().getId());
            String currentSettingsHash  = scanSettings.getConfidenceThreshold() + "|" + scanSettings.getMinNearDupSimilarity();
            String previousSettingsHash = prevComplete.get().getSettingsHash();
            if (currentSnapshot.equals(previousSnapshot) && currentSettingsHash.equals(previousSettingsHash)) {
                log.info("No changes detected — skipping full pipeline, reusing scan {}", prevComplete.get().getId());
                scanRepository.deleteById(run.getId());
                // Start the tracker with the *previous* scan's ID so the UI resolves
                // results against the correct scan, not the deleted stub.
                progressTracker.start(prevComplete.get().getId());
                progressTracker.complete();
                onPhase.accept("COMPLETE");
                return prevComplete.get();
            }
            log.info("Changes detected ({} → {} files) — running full pipeline",
                previousSnapshot.size(), currentSnapshot.size());
        }

        log.info("Starting scan run {} over {} path(s)", run.getId(), rootPaths.size());
        progressTracker.start(run.getId());

        try (ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "clonezapper-heartbeat");
            t.setDaemon(true);
            return t;
        })) {
            heartbeat.scheduleAtFixedRate(
                () -> scanRepository.updateHeartbeat(run.getId()), 0, 10, TimeUnit.SECONDS);
            try {
                // Stage ①: Scan — exclude the archive root to avoid re-indexing staged files
                phase(run.getId(), "SCANNING", onPhase);
                List<ScannedFile> files = scanStage.execute(
                    run.getId(), rootPaths, Set.of(archiveRoot),
                    count -> progressTracker.fileIndexed());
                log.info("Stage ① complete — {} files indexed", files.size());

                // Stage ②: Candidates
                phase(run.getId(), "CANDIDATES", onPhase);
                progressTracker.updatePhase("CANDIDATES");
                var candidates = candidateStage.execute(run.getId());
                log.info("Stage ② complete — {} candidate group(s)", candidates.size());

                // Stage ③: Compare — exact-hash pass + near-dup pass
                phase(run.getId(), "COMPARING", onPhase);
                progressTracker.updatePhase("COMPARING");
                var exactPairs   = compareStage.execute(candidates);
                var nearDupPairs = compareStage.executeNearDup(run.getId());
                log.info("Stage ③ complete — {} exact + {} near-dup pair(s)",
                    exactPairs.size(), nearDupPairs.size());

                // Stage ④: Cluster — exact and near-dup pairs are clustered separately.
                // A single shared Union-Find would merge them transitively: if A==B (exact)
                // and A≈C (near-dup), B and C end up in the same group even though they are
                // unrelated. Keeping the two strategy passes isolated prevents that.
                phase(run.getId(), "CLUSTERING", onPhase);
                progressTracker.updatePhase("CLUSTERING");
                var exactResult   = clusterStage.execute(run.getId(), exactPairs);
                var nearDupResult = clusterStage.execute(run.getId(), nearDupPairs);
                log.info("Stage ④ complete — exact: {}/{} auto/review; near-dup: {}/{} auto/review",
                    exactResult.autoQueue().size(), exactResult.reviewQueue().size(),
                    nearDupResult.autoQueue().size(), nearDupResult.reviewQueue().size());

                scanRepository.markCompleted(run.getId());
                scanRepository.updateSettingsHash(run.getId(),
                    scanSettings.getConfidenceThreshold() + "|" + scanSettings.getMinNearDupSimilarity());
                progressTracker.complete();
                phase(run.getId(), "COMPLETE", onPhase);

            } catch (Exception e) {
                log.error("Scan run {} failed: {}", run.getId(), e.getMessage(), e);
                scanRepository.updatePhase(run.getId(), "FAILED");
                progressTracker.fail();
                onPhase.accept("FAILED");
            }
        } // heartbeat.close() shuts down the scheduler

        return scanRepository.findById(run.getId()).orElse(run);
    }

    private void phase(long runId, String phase, Consumer<String> onPhase) {
        scanRepository.updatePhase(runId, phase);
        onPhase.accept(phase);
    }
}
