package com.clonezapper.model;

import java.time.LocalDateTime;

public class ScannedFile {

    private Long id;
    private Long scanId;
    private String path;
    private String provider;
    private long size;
    private LocalDateTime modifiedAt;
    private String mimeType;
    private String hashPartial;
    private String hashFull;
    private byte[] minhashSignature;
    private String copyHint;

    private ScannedFile() {}

    // ── Builder ───────────────────────────────────────────────────────────────

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final ScannedFile f = new ScannedFile();

        public Builder id(Long id)                        { f.id = id; return this; }
        public Builder scanId(Long scanId)                { f.scanId = scanId; return this; }
        public Builder path(String path)                  { f.path = path; return this; }
        public Builder provider(String provider)          { f.provider = provider; return this; }
        public Builder size(long size)                    { f.size = size; return this; }
        public Builder modifiedAt(LocalDateTime t)        { f.modifiedAt = t; return this; }
        public Builder mimeType(String mimeType)          { f.mimeType = mimeType; return this; }
        public Builder hashPartial(String hash)           { f.hashPartial = hash; return this; }
        public Builder hashFull(String hash)              { f.hashFull = hash; return this; }
        public Builder minhashSignature(byte[] sig)       { f.minhashSignature = sig; return this; }
        public Builder copyHint(String hint)              { f.copyHint = hint; return this; }
        public ScannedFile build()                        { return f; }
    }

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getScanId() { return scanId; }
    public void setScanId(Long scanId) { this.scanId = scanId; }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public long getSize() { return size; }
    public void setSize(long size) { this.size = size; }

    public LocalDateTime getModifiedAt() { return modifiedAt; }
    public void setModifiedAt(LocalDateTime modifiedAt) { this.modifiedAt = modifiedAt; }

    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }

    public String getHashPartial() { return hashPartial; }
    public void setHashPartial(String hashPartial) { this.hashPartial = hashPartial; }

    public String getHashFull() { return hashFull; }
    public void setHashFull(String hashFull) { this.hashFull = hashFull; }

    public byte[] getMinhashSignature() { return minhashSignature; }
    public void setMinhashSignature(byte[] minhashSignature) { this.minhashSignature = minhashSignature; }

    public String getCopyHint() { return copyHint; }
    public void setCopyHint(String copyHint) { this.copyHint = copyHint; }

    @Override
    public String toString() {
        return "ScannedFile{id=" + id + ", path='" + path + "', size=" + size + "}";
    }
}
