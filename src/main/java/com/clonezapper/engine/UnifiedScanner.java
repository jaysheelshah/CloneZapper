package com.clonezapper.engine;

import com.clonezapper.db.ScanRepository;
import com.clonezapper.engine.pipeline.ScanStage;
import com.clonezapper.model.ScanRun;
import com.clonezapper.model.ScannedFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Orchestrates the full deduplication pipeline across all configured providers.
 *
 * Currently executes Stage ① (Scan) only.
 * Stages ②–⑤ are wired but throw UnsupportedOperationException until implemented.
 */
@Service
public class UnifiedScanner {

    private static final Logger log = LoggerFactory.getLogger(UnifiedScanner.class);

    private final ScanRepository scanRepository;
    private final ScanStage scanStage;

    public UnifiedScanner(ScanRepository scanRepository, ScanStage scanStage) {
        this.scanRepository = scanRepository;
        this.scanStage = scanStage;
    }

    /**
     * Start a new scan over the given local paths.
     *
     * @param rootPaths list of absolute local paths to scan
     * @return the created ScanRun record
     */
    public ScanRun startScan(List<String> rootPaths) {
        ScanRun run = new ScanRun();
        run.setPhase("SCANNING");
        run.setCreatedAt(LocalDateTime.now());
        run.setRunLabel("run_" + System.currentTimeMillis());
        scanRepository.save(run);

        log.info("Starting scan run {} over {} path(s)", run.getId(), rootPaths.size());

        try {
            // Stage ①: Scan
            List<ScannedFile> files = scanStage.execute(run.getId(), rootPaths);
            scanRepository.updatePhase(run.getId(), "CANDIDATES");
            log.info("Stage ① complete — {} files indexed", files.size());

            // Stages ②–⑤ not yet implemented
            log.info("Stages ②–⑤ pending implementation. Run {} complete at Stage ①.", run.getId());
            scanRepository.markCompleted(run.getId());

        } catch (Exception e) {
            log.error("Scan run {} failed: {}", run.getId(), e.getMessage(), e);
            scanRepository.updatePhase(run.getId(), "FAILED");
        }

        return scanRepository.findById(run.getId()).orElse(run);
    }
}
