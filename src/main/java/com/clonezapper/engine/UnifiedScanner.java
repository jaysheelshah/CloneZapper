package com.clonezapper.engine;

import com.clonezapper.db.ScanRepository;
import com.clonezapper.engine.pipeline.CandidateStage;
import com.clonezapper.engine.pipeline.ClusterStage;
import com.clonezapper.engine.pipeline.CompareStage;
import com.clonezapper.engine.pipeline.ScanStage;
import com.clonezapper.model.ScanRun;
import com.clonezapper.model.ScannedFile;
import com.clonezapper.service.ScanProgressTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
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
    private final ScanStage scanStage;
    private final CandidateStage candidateStage;
    private final CompareStage compareStage;
    private final ClusterStage clusterStage;
    private final ScanProgressTracker progressTracker;
    private final String archiveRoot;

    public UnifiedScanner(ScanRepository scanRepository,
                          ScanStage scanStage,
                          CandidateStage candidateStage,
                          CompareStage compareStage,
                          ClusterStage clusterStage,
                          ScanProgressTracker progressTracker,
                          @Value("${clonezapper.archive.root}") String archiveRoot) {
        this.scanRepository = scanRepository;
        this.scanStage = scanStage;
        this.candidateStage = candidateStage;
        this.compareStage = compareStage;
        this.clusterStage = clusterStage;
        this.progressTracker = progressTracker;
        this.archiveRoot = archiveRoot;
    }

    /** Runs the full pipeline with no progress callback. */
    public ScanRun startScan(List<String> rootPaths) {
        return startScan(rootPaths, phase -> {});
    }

    /**
     * Runs the full pipeline over the given local paths.
     *
     * @param rootPaths  list of absolute local paths to scan
     * @param onPhase    callback invoked with the phase name at each stage transition
     * @return           the completed ScanRun record
     */
    public ScanRun startScan(List<String> rootPaths, Consumer<String> onPhase) {
        ScanRun run = new ScanRun();
        run.setPhase("SCANNING");
        run.setCreatedAt(LocalDateTime.now());
        run.setRunLabel("run_" + LocalDateTime.now().format(RUN_LABEL_FORMAT));
        run.setArchiveRoot(archiveRoot);
        scanRepository.save(run);

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
                var pairs = new ArrayList<>(exactPairs);
                pairs.addAll(nearDupPairs);
                log.info("Stage ③ complete — {} exact + {} near-dup pair(s)",
                    exactPairs.size(), nearDupPairs.size());

                // Stage ④: Cluster
                phase(run.getId(), "CLUSTERING", onPhase);
                progressTracker.updatePhase("CLUSTERING");
                var result = clusterStage.execute(run.getId(), pairs);
                log.info("Stage ④ complete — {} auto-queue, {} review-queue",
                    result.autoQueue().size(), result.reviewQueue().size());

                scanRepository.markCompleted(run.getId());
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
