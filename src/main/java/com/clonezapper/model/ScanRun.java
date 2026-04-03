package com.clonezapper.model;

import java.time.LocalDateTime;

public class ScanRun {

    private Long id;
    private String phase;
    private String checkpointFileId;
    private String deltaToken;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
    private String archiveRoot;
    private String runLabel;
    private LocalDateTime lastHeartbeat;

    public ScanRun() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getPhase() { return phase; }
    public void setPhase(String phase) { this.phase = phase; }

    public String getCheckpointFileId() { return checkpointFileId; }
    public void setCheckpointFileId(String checkpointFileId) { this.checkpointFileId = checkpointFileId; }

    public String getDeltaToken() { return deltaToken; }
    public void setDeltaToken(String deltaToken) { this.deltaToken = deltaToken; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }

    public String getArchiveRoot() { return archiveRoot; }
    public void setArchiveRoot(String archiveRoot) { this.archiveRoot = archiveRoot; }

    public String getRunLabel() { return runLabel; }
    public void setRunLabel(String runLabel) { this.runLabel = runLabel; }

    public LocalDateTime getLastHeartbeat() { return lastHeartbeat; }
    public void setLastHeartbeat(LocalDateTime lastHeartbeat) { this.lastHeartbeat = lastHeartbeat; }

    @Override
    public String toString() {
        return "ScanRun{id=" + id + ", phase=" + phase + ", createdAt=" + createdAt + "}";
    }
}
