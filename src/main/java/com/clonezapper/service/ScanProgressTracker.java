package com.clonezapper.service;

import org.springframework.stereotype.Service;

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

    /** Called at the start of every scan run. Resets all counters. */
    public void start(long scanId) {
        this.scanId = scanId;
        this.phase  = "SCANNING";
        this.filesIndexed.set(0);
        this.active = true;   // set last so readers see a consistent snapshot
    }

    /** Called at each pipeline phase transition. */
    public void updatePhase(String phase) {
        this.phase = phase;
    }

    /** Called once per file processed by ScanStage. */
    public void fileIndexed() {
        filesIndexed.incrementAndGet();
    }

    /** Called when the pipeline finishes successfully. */
    public void complete() {
        this.phase  = "COMPLETE";
        this.active = false;
    }

    /** Called when the pipeline fails. */
    public void fail() {
        this.phase  = "FAILED";
        this.active = false;
    }

    public boolean isActive()      { return active; }
    public String  getPhase()      { return phase; }
    public int     getFilesIndexed(){ return filesIndexed.get(); }
    public long    getScanId()     { return scanId; }
}
