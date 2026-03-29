package com.clonezapper.model;

import java.util.ArrayList;
import java.util.List;

public class DuplicateGroup {

    private Long id;
    private Long scanId;
    private Long canonicalFileId;
    private String strategy;
    private double confidence;
    private List<DuplicateMember> members = new ArrayList<>();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getScanId() { return scanId; }
    public void setScanId(Long scanId) { this.scanId = scanId; }

    public Long getCanonicalFileId() { return canonicalFileId; }
    public void setCanonicalFileId(Long canonicalFileId) { this.canonicalFileId = canonicalFileId; }

    public String getStrategy() { return strategy; }
    public void setStrategy(String strategy) { this.strategy = strategy; }

    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }

    public List<DuplicateMember> getMembers() { return members; }
    public void setMembers(List<DuplicateMember> members) { this.members = members; }
}
