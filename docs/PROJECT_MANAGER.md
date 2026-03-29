# CloneZapper — Project Manager

> Living document tracking milestones, stories, tasks, and progress.
> Status legend: `[ ]` Not started · `[~]` In progress · `[x]` Done · `[!]` Blocked

---

## Vision

Build the first content-aware, cross-storage duplicate file detection engine — one that detects duplicates across local drives and cloud services (Google Drive, OneDrive, Dropbox), including documents that contain the same content but exist in different formats.

---

## Milestones Overview

| # | Milestone | Status | Target |
|---|-----------|--------|--------|
| M1 | Project foundation & core infrastructure | `[ ]` | — |
| M2 | Local exact deduplication (MVP CLI) | `[ ]` | — |
| M3 | Near-duplicate detection (text & image) | `[ ]` | — |
| M4 | Cloud API integration | `[ ]` | — |
| M5 | Content-aware document matching (OCR + Tika) | `[ ]` | — |
| M6 | Cross-boundary comparison (local ↔ cloud) | `[ ]` | — |
| M7 | Action engine + undo | `[ ]` | — |
| M8 | Polish, packaging & distribution | `[ ]` | — |

---

## M1 — Project Foundation & Core Infrastructure

**Goal:** Skeleton project compiles, basic CLI runs, SQLite schema initialized, logging working.

### Stories

#### S1.1 — Maven project setup
- [ ] Create `pom.xml` with Java 21, picocli, SQLite JDBC, Guava, SLF4J/Logback
- [ ] Configure `maven-assembly-plugin` for fat JAR
- [ ] Add `.mvn/wrapper` for reproducible builds
- [ ] Verify `mvn clean package` produces a runnable JAR

#### S1.2 — CLI skeleton
- [ ] Define top-level `CloneZapper` picocli command
- [ ] Add `scan`, `results`, `clean` subcommands (stubs)
- [ ] Wire `--help` and `--version`
- [ ] ANSI color output working on Windows + macOS

#### S1.3 — Database layer
- [ ] Design SQLite schema:
  - `files` (id, path, provider, size, modified_at, mime_type, hash_partial, hash_full, minhash_signature, scan_id)
  - `duplicate_groups` (id, scan_id, canonical_file_id, strategy)
  - `duplicate_members` (group_id, file_id, confidence)
  - `scan_state` (scan_id, phase, checkpoint_file_id, delta_token, created_at)
  - `actions` (id, scan_id, action_type, file_id, destination, executed_at, undone)
- [ ] `DatabaseManager` class: open/close, migrations, WAL mode
- [ ] Basic DAO classes for `files` and `scan_state`

#### S1.4 — Logging & config
- [ ] Logback config (console + rolling file)
- [ ] `AppConfig` class loading from `~/.clonezapper/config.properties`
- [ ] `--verbose` / `--quiet` flags wired to log level

---

## M2 — Local Exact Deduplication (MVP CLI)

**Goal:** Scan a local directory, find exact duplicates by hash, output results to console and DB. This is the functional MVP.

### Stories

#### S2.1 — Filesystem scanner
- [ ] Recursive `FileTreeWalker` using `Files.walkFileTree`
- [ ] Respect `.clonezapperignore` patterns (gitignore syntax)
- [ ] Follow/skip symlinks option (`--follow-symlinks`)
- [ ] Persist file metadata to `files` table per scan
- [ ] Report scan progress (files/sec, total size)

#### S2.2 — Progressive hash pipeline
- [ ] **Stage 1:** Group files by size — skip unique sizes
- [ ] **Stage 2:** Compute partial hash (first 4 KB, BLAKE3 via Guava) for size-matched groups
- [ ] **Stage 3:** Compute full BLAKE3 hash only for partial-hash matches
- [ ] Store `hash_partial` and `hash_full` in DB
- [ ] Benchmark: target >500 MB/s on local SSD

#### S2.3 — Concurrency model
- [ ] Scanner thread → bounded `BlockingQueue` (capacity 1,000) → `ForkJoinPool` hashers
- [ ] Configurable thread count (`--threads`, default = CPU cores)
- [ ] Checkpoint after each batch (resumable scans via `scan_state`)
- [ ] Graceful shutdown on Ctrl+C with progress save

#### S2.4 — Results output
- [ ] CLI table output: duplicate groups, sizes, paths, wasted space total
- [ ] `--output json` and `--output csv` export modes
- [ ] `results` subcommand to query a previous scan from DB without re-scanning

#### S2.5 — Basic tests
- [ ] Unit tests: hash pipeline stages, size grouping
- [ ] Integration test: temp directory with known duplicates, assert groups found
- [ ] Benchmark test: 10,000 files, assert completes under 30s

---

## M3 — Near-Duplicate Detection (Text & Image)

**Goal:** Detect files that are not byte-identical but contain the same or very similar content.

### Stories

#### S3.1 — MinHash + LSH for text documents
- [ ] Add `java-LSH` dependency
- [ ] `TextFingerprintService`: extract text via Tika, normalize, compute k-gram shingles
- [ ] Generate MinHash signatures (128 permutations)
- [ ] Store signatures in `files.minhash_signature` (serialized)
- [ ] LSH banding (18 bands × 7 rows, threshold ~0.85 Jaccard)
- [ ] Candidate pair generation from shared LSH buckets
- [ ] Cosine/TF-IDF verification for candidate pairs

#### S3.2 — Perceptual image hashing
- [ ] Add `JImageHash` dependency
- [ ] `ImageFingerprintService`: compute dHash (primary) + pHash (verification)
- [ ] EXIF metadata pre-filter (DateTimeOriginal, camera model) via Tika
- [ ] Hamming distance threshold (configurable, default ≤ 10 bits on 128-bit hash)
- [ ] Handle HEIC/HEIF, RAW formats (Canon CR2, Nikon NEF) via Tika extraction

#### S3.3 — Dispatcher / plugin registry
- [ ] `FileTypeHandler` interface: `canHandle(mimeType)`, `computeFingerprint(File)`, `computeSimilarity(a, b)`
- [ ] `HandlerRegistry` loading via `ServiceLoader`
- [ ] Implementations: `DocumentHandler`, `ImageHandler`, `GenericHandler` (hash fallback)
- [ ] MIME type detection via Tika (not file extension)

#### S3.4 — Confidence scoring
- [ ] Assign confidence score per duplicate pair (0.0–1.0)
- [ ] Exact hash match → 1.0; perceptual image match → based on Hamming distance; text similarity → Jaccard score
- [ ] Store confidence in `duplicate_members` table
- [ ] `--min-confidence` filter flag in CLI

---

## M4 — Cloud API Integration

**Goal:** Scan Google Drive, OneDrive, and Dropbox via native APIs without requiring local sync.

### Stories

#### S4.1 — Google Drive connector
- [ ] OAuth 2.0 flow (`drive:readonly` scope), token stored in `~/.clonezapper/tokens/`
- [ ] Paginated `files.list` with `fields=id,name,size,md5Checksum,mimeType,modifiedTime`
- [ ] Incremental scan via `changes.list` + persisted page token
- [ ] Export Google Workspace files (Docs → DOCX, Sheets → XLSX, Slides → PPTX)
- [ ] Adaptive rate limiting: exponential backoff on 403/429 (max 20,000 req/100s)
- [ ] `gdrive auth`, `gdrive scan`, `gdrive status` CLI subcommands

#### S4.2 — OneDrive / Microsoft Graph connector
- [ ] MSAL OAuth 2.0 flow (`Files.Read` scope)
- [ ] Delta query (`/drives/{id}/root/delta`) for incremental scanning
- [ ] Persist `@odata.deltaLink` in `scan_state`
- [ ] Handle personal OneDrive + SharePoint document libraries
- [ ] `onedrive auth`, `onedrive scan` CLI subcommands

#### S4.3 — Dropbox connector
- [ ] OAuth 2.0 via Dropbox SDK (`files.metadata.read` scope)
- [ ] `files/list_folder` + cursor-based incremental updates (`files/list_folder/continue`)
- [ ] Handle Dropbox Paper files
- [ ] `dropbox auth`, `dropbox scan` CLI subcommands

#### S4.4 — Unified provider abstraction
- [ ] `StorageProvider` interface: `listFiles()`, `downloadContent(fileId)`, `getMetadata(fileId)`
- [ ] `ProviderRegistry` managing multiple authenticated accounts
- [ ] Cloud files persisted to `files` table with `provider` field (local / gdrive / onedrive / dropbox)
- [ ] Download-on-demand: only fetch file content when hash/fingerprint unavailable from metadata

---

## M5 — Content-Aware Document Matching

**Goal:** Detect that a PDF, a DOCX, and a scanned image contain the same document.

### Stories

#### S5.1 — Text extraction pipeline
- [ ] `ContentExtractor` wrapping Apache Tika for 1,000+ formats
- [ ] PDFBox fallback for malformed PDFs
- [ ] Text normalization: lowercase, strip punctuation, collapse whitespace, remove boilerplate headers/footers
- [ ] Language detection (Tika's `LanguageDetector`) for multi-language support

#### S5.2 — OCR for scanned documents
- [ ] Tess4j integration with Tesseract 5 LSTM engine
- [ ] Auto-detect scanned PDFs (image-only, no selectable text) via PDFBox's `PDPage` analysis
- [ ] Image pre-processing: deskew, denoise, binarize for accuracy
- [ ] OCR as opt-in fallback (`--enable-ocr` flag) due to 10–100× speed cost
- [ ] Cache OCR results in DB to avoid re-processing

#### S5.3 — Cross-format similarity matching
- [ ] Apply MinHash+LSH pipeline from M3 to OCR-extracted text
- [ ] Configurable similarity threshold per document type
- [ ] Handle version detection: same base document with minor edits (e.g., v1 vs v2 of a report)
- [ ] Report matched groups with extracted text snippets as evidence

---

## M6 — Cross-Boundary Comparison (Local ↔ Cloud)

**Goal:** Find duplicates that exist on local disk AND in cloud storage simultaneously.

### Stories

#### S6.1 — Unified scan across all providers
- [ ] `UnifiedScanner` orchestrates local + cloud scans in parallel
- [ ] Merge results into single `files` table keyed by content fingerprint
- [ ] Cross-provider duplicate group detection (local file = GDrive file)
- [ ] Progress dashboard: files scanned per provider, estimated time remaining

#### S6.2 — Cross-boundary reporting
- [ ] Results view filtered by: local-only dupes, cloud-only dupes, cross-boundary dupes
- [ ] Storage waste breakdown per provider
- [ ] "Sync gaps" report: files on local not in cloud (and vice versa)

---

## M7 — Action Engine + Undo

**Goal:** Let users act on duplicate results safely, with full undo capability.

### Stories

#### S7.1 — Action types
- [ ] `DELETE` — move to system trash (not permanent delete)
- [ ] `MOVE` — relocate to a specified archive folder
- [ ] `SYMLINK` — replace duplicate with symlink to canonical
- [ ] `MARK` — tag in DB without touching filesystem (review-only mode)

#### S7.2 — Canonical selection rules
- [ ] Configurable strategies: `oldest`, `newest`, `highest-quality` (image resolution), `preferred-provider` (e.g., prefer local over cloud)
- [ ] Manual override: user can designate canonical via CLI

#### S7.3 — Undo
- [ ] All actions logged to `actions` table with full inverse operation
- [ ] `clonezapper undo --scan-id <id>` replays all actions in reverse
- [ ] Undo blocked if files were permanently deleted externally

#### S7.4 — Dry-run mode
- [ ] `--dry-run` flag: simulate all actions and print plan without executing
- [ ] Dry-run report shows exact files that would be affected and total space freed

---

## M8 — Polish, Packaging & Distribution

**Goal:** Distributable, installable product with good UX.

### Stories

#### S8.1 — Installer & distribution
- [ ] Windows: NSIS or WiX installer (`.msi`)
- [ ] macOS: `.dmg` with code signing
- [ ] Linux: `.deb` / `.rpm` + AUR package
- [ ] GitHub Releases with binaries per platform

#### S8.2 — GraalVM native image (optional)
- [ ] Evaluate picocli + SQLite JDBC + Tika compatibility with native-image
- [ ] Build native binaries for instant startup (no JVM required)
- [ ] Fallback: bundle JRE in installer if native image not feasible

#### S8.3 — User experience
- [ ] First-run wizard: guided setup, cloud auth, first scan
- [ ] Progress bars with ETA (picocli built-in)
- [ ] Color-coded output: red = duplicates to delete, yellow = review, green = safe
- [ ] Man page + shell tab completion (picocli generates both)

#### S8.4 — Documentation
- [ ] `docs/cloud-setup.md` — OAuth setup guide per provider
- [ ] `docs/algorithm.md` — technical explanation of pipeline
- [ ] `docs/config.md` — all config options documented
- [ ] CHANGELOG.md

---

## Backlog (Unscheduled)

- [ ] GUI (JavaFX) — visual duplicate browser with side-by-side preview
- [ ] AI-powered canonical selection (LLM-based document understanding)
- [ ] Scheduled / daemon mode (watch folders, auto-flag new duplicates)
- [ ] SMB / NFS / NAS support
- [ ] S3 / Backblaze B2 / Wasabi connector
- [ ] Business tier: admin dashboard, team scanning, audit log export
- [ ] Semantic deduplication using LLM embeddings (SemDeDup-style)

---

## Decisions Log

| Date | Decision | Rationale |
|------|----------|-----------|
| 2026-03-29 | Java 21 + Maven | JVM ecosystem has all required libraries; picocli has native-image support for future distribution |
| 2026-03-29 | BLAKE3 for hashing | Best throughput/security tradeoff; xxHash3 too risky (non-cryptographic) |
| 2026-03-29 | SQLite for persistence | Single portable file; WAL mode for concurrent reads; FTS5 for text search |
| 2026-03-29 | MIT License | Permissive; compatible with all dependency licenses |
| 2026-03-29 | MinHash+LSH (java-LSH) | Scales to millions of files; proven production algorithm (used at Google, Yahoo) |
| 2026-03-29 | Apache Tika as extraction layer | Wraps 75+ parsers; single API for 1,000+ formats; integrates Tesseract |

---

## Notes

- **False positive policy:** Never auto-delete. All results require explicit user confirmation. Near-duplicate detection is for candidate generation only — precise comparison always runs before action.
- **Privacy:** Cloud scanning uses read-only OAuth scopes. File content is never sent to external servers. All processing is local.
- **OCR performance:** Tesseract is 10–100× slower than electronic text extraction. Enable only on demand (`--enable-ocr`) and cache results aggressively.