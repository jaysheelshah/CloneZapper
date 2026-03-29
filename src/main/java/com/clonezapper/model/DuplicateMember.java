package com.clonezapper.model;

public class DuplicateMember {

    private Long groupId;
    private Long fileId;
    private double confidence;

    public DuplicateMember() {}

    public DuplicateMember(Long groupId, Long fileId, double confidence) {
        this.groupId = groupId;
        this.fileId = fileId;
        this.confidence = confidence;
    }

    public Long getGroupId() { return groupId; }
    public void setGroupId(Long groupId) { this.groupId = groupId; }

    public Long getFileId() { return fileId; }
    public void setFileId(Long fileId) { this.fileId = fileId; }

    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }
}
