# CloneZapper

**A content-aware, cross-storage duplicate file detection engine for local drives and cloud services.**

CloneZapper goes beyond byte-level hashing to detect duplicates across Google Drive, OneDrive, Dropbox, and local storage — including files that contain the same content but differ in format (e.g., a PDF, a Word document, and a scanned image of the same invoice).

---

## Why CloneZapper?

Every existing tool falls into one of two buckets:

- **Local-only** open-source tools (Czkawka, fdupes, dupeGuru) — fast, but cloud-blind
- **Cloud-only** SaaS tools (Cloud Duplicate Finder) — ignore your local files entirely

No tool scans *across* storage boundaries, and none can detect content-equivalent documents in different formats. CloneZapper fills that gap.

---

## Key Features

| Feature | Description |
|---|---|
| **Exact deduplication** | BLAKE3-based progressive hashing (size → partial → full) |
| **Cross-storage scanning** | Compare local files against Google Drive, OneDrive, and Dropbox via native APIs — no syncing required |
| **Content-aware document matching** | Detect duplicate PDFs, Word docs, and scanned images containing the same text via Apache Tika + Tesseract OCR |
| **Near-duplicate detection** | MinHash + LSH for scalable Jaccard-based similarity; cosine/TF-IDF for verification |
| **Perceptual image hashing** | aHash, dHash, pHash via JImageHash — survives resize, compression, and format conversion |
| **Safe by design** | All results presented for human review before any deletion; full undo capability |
| **Plugin extensible** | Add new file-type handlers via Java ServiceLoader without touching core pipeline code |

---

## How It Works

CloneZapper runs a five-stage pipeline:

```
Scan → Candidate Generation → Comparison → Clustering → Action
```

1. **Scan** — Enumerate files from local paths and cloud APIs; persist metadata to SQLite
2. **Candidate Generation** — Size grouping + partial hashing + MinHash LSH to filter ~95% of non-candidates cheaply
3. **Comparison** — Full hashing, text similarity, or perceptual hashing depending on file type
4. **Clustering** — Group duplicates with Union-Find; select canonical version per configurable rules
5. **Action** — Review results, then delete / move / archive with undo support

---

## Tech Stack

- **Language:** Java 21+
- **Build:** Maven
- **Content extraction:** Apache Tika 3.3.0, Apache PDFBox 3.0.7, Apache POI 5.5.1
- **OCR:** Tess4j 5.18.0 (Tesseract LSTM)
- **Hashing:** Guava (BLAKE3/SHA-256), lz4-java (xxHash3), JImageHash
- **Near-dedup:** java-LSH (MinHash + Super-Bit)
- **Cloud APIs:** Google Drive v3, Microsoft Graph SDK 6.62.0, Dropbox SDK 7.0.0
- **CLI:** picocli 4.7.7
- **Storage:** SQLite JDBC 3.51.3.0

---

## Getting Started

> Prerequisites: Java 21+, Maven 3.9+, Tesseract 5.x (for OCR features)

```bash
# Clone the repo
git clone https://github.com/yourusername/CloneZapper.git
cd CloneZapper

# Build
mvn clean package

# Run a local scan
java -jar target/clonezapper.jar scan --path ~/Documents

# Scan with cloud (requires OAuth setup — see docs/cloud-setup.md)
java -jar target/clonezapper.jar scan --path ~/Documents --gdrive --onedrive
```

---

## Project Status

CloneZapper is in active early development. See [PROJECT_MANAGER.md](docs/PROJECT_MANAGER.md) for the current milestone and task progress.

---

## License

MIT — see [LICENSE](LICENSE)
