package com.clonezapper.model;

import java.time.LocalDateTime;

public class Action {

    public enum Type { MOVE, DELETE, SYMLINK, ARCHIVE }

    private Long id;
    private Long scanId;
    private Type actionType;
    private Long fileId;
    private String destination;
    private String originalPath;
    private LocalDateTime executedAt;
    private boolean undone;
    private boolean cleaned;
    private boolean purged;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getScanId() { return scanId; }
    public void setScanId(Long scanId) { this.scanId = scanId; }

    public Type getActionType() { return actionType; }
    public void setActionType(Type actionType) { this.actionType = actionType; }

    public Long getFileId() { return fileId; }
    public void setFileId(Long fileId) { this.fileId = fileId; }

    public String getDestination() { return destination; }
    public void setDestination(String destination) { this.destination = destination; }

    public String getOriginalPath() { return originalPath; }
    public void setOriginalPath(String originalPath) { this.originalPath = originalPath; }

    public LocalDateTime getExecutedAt() { return executedAt; }
    public void setExecutedAt(LocalDateTime executedAt) { this.executedAt = executedAt; }

    public boolean isUndone() { return undone; }
    public void setUndone(boolean undone) { this.undone = undone; }

    public boolean isCleaned() { return cleaned; }
    public void setCleaned(boolean cleaned) { this.cleaned = cleaned; }

    public boolean isPurged() { return purged; }
    public void setPurged(boolean purged) { this.purged = purged; }
}
