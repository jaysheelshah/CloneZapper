package com.clonezapper.service;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Singleton that holds live scan progress visible to all UI components.
 * Updated by {@link com.clonezapper.engine.UnifiedScanner} on the scan thread;
 * read by {@link com.clonezapper.ui.ScanView} and {@link com.clonezapper.ui.MainLayout}
 * via Vaadin poll listeners.
 * <p>
 * All fields are thread-safe: volatile for single-writer flags, AtomicInteger for
 * the counter that is incremented once per file inside ScanStage.
 */
@Service
public class ScanProgressTracker {

    private volatile boolean active = false;
    private volatile String  phase  = "";
    private volatile long    scanId = -1;
    private final AtomicInteger filesIndexed = new AtomicInteger(0);

    /** Wall-clock time when the current scan started. */
    private volatile Instant scanStartTime = null;

    /** Start time of each pipeline stage, keyed by phase name (e.g. "SCANNING"). */
    private final ConcurrentHashMap<String, Instant> stageStartTimes = new ConcurrentHashMap<>();

    /** Called at the start of every scan run. Resets all counters. */
    public void start(long scanId) {
        this.scanId = scanId;
        this.phase  = "SCANNING";
        this.filesIndexed.set(0);
        this.scanStartTime = Instant.now();
        stageStartTimes.clear();
        stageStartTimes.put("SCANNING", Instant.now());
        this.active = true;   // set last so readers see a consistent snapshot
    }

    /** Called at each pipeline phase transition. */
    public void updatePhase(String phase) {
        this.phase = phase;
        stageStartTimes.putIfAbsent(phase, Instant.now());
    }

    /** Called once per file processed by ScanStage. */
    public void fileIndexed() {
        filesIndexed.incrementAndGet();
    }

    /** Called when the pipeline finishes successfully. */
    public void complete() {
        stageStartTimes.putIfAbsent("COMPLETE", Instant.now());
        this.phase  = "COMPLETE";
        this.active = false;
    }

    /** Called when the pipeline fails. */
    public void fail() {
        this.phase  = "FAILED";
        this.active = false;
    }

    public boolean isActive()       { return active; }
    public String  getPhase()       { return phase; }
    public int     getFilesIndexed(){ return filesIndexed.get(); }
    public long    getScanId()      { return scanId; }
    public Instant getScanStartTime() { return scanStartTime; }

    /** Returns an unmodifiable snapshot of per-stage start times. */
    public Map<String, Instant> getStageStartTimes() {
        return Collections.unmodifiableMap(stageStartTimes);
    }
}
