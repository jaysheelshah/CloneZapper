# FileManagementSystem (FMS) — Design Document

> **Status:** Planning — pre-implementation
> **Author:** Drawing-board session, 2026-03-30
> **Relationship to CloneZapper:** CloneZapper becomes `fms-dedup`, one arm of the FMS umbrella.
> CloneZapper also remains buildable as a fully standalone application.

---

## Vision

A personal file intelligence layer — one place where you interact with your files by *meaning*,
not by path. You describe what you need; FMS finds, creates, organises, or cleans it up.

CloneZapper's dedup engine is the first and most mature arm. Search, creation, and organisation
arms follow. All arms share the same file index, storage layer, and Vaadin UI shell.

---

## The Four Arms

```
┌────────────────────────────────────────────────────────────┐
│                   FileManagementSystem                      │
│                                                            │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐  │
│  │  DEDUP   │  │  SEARCH  │  │  CREATE  │  │ ORGANISE │  │
│  │          │  │          │  │          │  │          │  │
│  │CloneZapper  │ Fuzzy    │  │Templates │  │ Move /   │  │
│  │ pipeline │  │ Finder   │  │ AI draft │  │ Rename   │  │
│  │          │  │ Semantic │  │ Quick    │  │ Tags     │  │
│  │          │  │ search   │  │ capture  │  │ Notes    │  │
│  └────┬─────┘  └────┬─────┘  └────┬─────┘  └────┬─────┘  │
│       └─────────────┴─────────────┴──────────────┘        │
│                           │                               │
│              ┌────────────▼───────────┐                   │
│              │      fms-core          │                   │
│              │  File Index · SQLite   │                   │
│              │  Tika · Scan pipeline  │                   │
│              │  Handlers · FTS5       │                   │
│              └────────────────────────┘                   │
└────────────────────────────────────────────────────────────┘
```

---

## Project Name

**FileManagementSystem** — internal project name and Maven `artifactId` root.
The user-facing name can be decided later; the code stays `com.fms.*`.

---

## Search Architecture — Option C: Local Embeddings via Ollama

All semantic search runs **fully offline**. No API key required for any feature.

### Why Option C over A (FTS5 only) and B (Claude API)

| | Option A — FTS5 | Option B — Claude API | Option C — Ollama |
|---|---|---|---|
| Query: "tax file 2024" | ✅ keyword match | ✅ Claude reformulates | ✅ semantic match |
| Query: "Nakamura contract" (file says "Nakamura Group agreement") | ❌ no match | ✅ Claude understands | ✅ semantic match |
| Query: "beach photo" (EXIF location = Malibu) | ❌ | ✅ | ✅ |
| Offline / no API key | ✅ | ❌ | ✅ |
| Ollama already installed | — | — | ✅ confirmed |
| Setup complexity for end user | Low | Medium | **Already done for this user** |

### How it works

```
INDEX TIME (background, after each scan):
  ScanStage indexes files
       ↓
  ContentExtractor (Tika) extracts text from each file
       ↓
  OllamaEmbeddingService sends text to Ollama → gets 768-dim vector
       ↓
  Vector stored in file_vectors table (BLOB, serialised float[])
  Text stored in file_content FTS5 virtual table

QUERY TIME (interactive):
  User types: "my tax file for 2024"
       ↓
  OllamaEmbeddingService embeds the query → query vector
       ↓
  VectorSearchService: cosine similarity against all stored vectors
  FTS5SearchService: keyword fallback for files with no extractable text
       ↓
  Results merged, ranked by combined score
       ↓
  SearchView shows ranked list with one-line reason per result
```

### Ollama models

| Model | Use | Size | Notes |
|---|---|---|---|
| `nomic-embed-text` | Text embeddings (documents, notes, filenames) | 274 MB | Fast, 768 dims, excellent for retrieval |
| `mxbai-embed-large` | Higher quality embeddings | 670 MB | Use if accuracy matters more than speed |
| `llama3.2:3b` or `phi3.5` | Optional: query intent parsing, result explanation | ~2 GB | Only needed for "why was this surfaced?" explanations |

**Default:** `nomic-embed-text` for embeddings (lightweight, already popular with Ollama users).
The LLM for explanations is opt-in — search works without it.

### Vector similarity in SQLite

No additional vector database required. Vectors stored as `BLOB` in SQLite.
Cosine similarity computed in Java over the full set at query time.

```sql
CREATE TABLE file_vectors (
    path       TEXT PRIMARY KEY,
    model      TEXT,           -- "nomic-embed-text" (for cache invalidation on model change)
    vector     BLOB NOT NULL,  -- float[] serialised as little-endian bytes
    indexed_at TEXT
);
```

This is fast enough for personal-scale file collections (10k–500k files) running on
commodity hardware. At 768 floats × 4 bytes = 3,072 bytes per file:
- 100k files = ~300 MB in-memory for a full similarity pass (~50ms on modern CPU)
- For larger collections, HNSW indexing can be layered in later without schema change.

---

## Maven Multi-Module Structure

```
file-management-system/                ← parent POM (packaging: pom)
├── pom.xml
│
├── fms-core/                          ← shared kernel — no Spring Boot bootstrap
│   ├── model/                         ← ScannedFile, ScanRun, DuplicateGroup, Action …
│   ├── db/                            ← FileRepository, ScanRepository, DuplicateGroupRepository …
│   ├── service/                       ← HashService, ContentExtractor, NearDupService, CopyPatternDetector
│   ├── handler/                       ← FileTypeHandler interface, HandlerRegistry, DocumentHandler,
│   │                                     ImageHandler, GenericHandler
│   └── provider/                      ← StorageProvider interface, LocalFilesystemProvider
│
├── fms-dedup/                         ← CloneZapper's engine, depends on fms-core
│   ├── engine/                        ← UnifiedScanner, ScanStage, CandidateStage,
│   │                                     CompareStage, ClusterStage, ExecuteStage
│   ├── cli/                           ← ScanCommand, StageCommand, CleanupCommand, PurgeCommand …
│   └── ui/                            ← ScanView, ResultsView, ReviewQueueView, DashboardView (dedup)
│
├── fms-search/                        ← fuzzy finder, depends on fms-core
│   ├── engine/                        ← OllamaEmbeddingService, VectorSearchService,
│   │                                     FtsSearchService, SearchService (facade)
│   ├── index/                         ← ContentIndexer (triggers embedding after scan)
│   └── ui/                            ← SearchView, FileDetailView
│
├── fms-operations/                    ← file operations, depends on fms-core
│   ├── service/                       ← FileOperationService, TemplateService
│   └── ui/                            ← OrganiseView, TemplatesView
│
├── fms-app/                           ← FULL FMS — Spring Boot assembly, all modules
│   ├── FmsApplication.java
│   ├── ui/                            ← MainLayout (FMS nav), AppShell, FmsDashboardView
│   └── resources/
│       └── application.properties     ← Ollama URL, archive root, scan paths
│
└── fms-dedup-standalone/              ← CloneZapper standalone — Spring Boot assembly,
    ├── CloneZapperApplication.java       fms-core + fms-dedup ONLY
    └── resources/
        └── application.properties     ← identical to current CloneZapper config
```

### Dependency graph

```
fms-core          ← no FMS dependencies
    ↑
fms-dedup         ← depends on fms-core
fms-search        ← depends on fms-core
fms-operations    ← depends on fms-core

fms-app           ← depends on fms-dedup + fms-search + fms-operations
fms-dedup-standalone ← depends on fms-core + fms-dedup ONLY
```

No circular dependencies. Adding a new arm = new module, depends on `fms-core`, plugs into `fms-app`.

---

## How CloneZapper Stays Standalone

`fms-dedup-standalone` is a thin Spring Boot bootstrap:

```java
@SpringBootApplication(scanBasePackages = {"com.fms.core", "com.fms.dedup"})
public class CloneZapperApplication {
    public static void main(String[] args) {
        SpringApplication.run(CloneZapperApplication.class, args);
    }
}
```

- Same `application.properties` as the current CloneZapper
- Same Vaadin views (ScanView, ResultsView, ReviewQueueView)
- Same CLI commands
- Built with: `mvn package -pl fms-dedup-standalone -am`
- Produces: `clonezapper-standalone.jar` — functionally identical to today's CloneZapper

The current CloneZapper test suite moves to `fms-dedup` and `fms-core`. All 183 tests continue to pass against those modules.

---

## Database Schema Additions

FMS adds four new tables to the existing CloneZapper schema.
No existing tables are modified — full backwards compatibility.

```sql
-- Full-text search over Tika-extracted file content
CREATE VIRTUAL TABLE file_content USING fts5(
    path      UNINDEXED,   -- joins to files.path
    content,               -- raw extracted text (Tika output, truncated to 64KB)
    tokenize = 'porter ascii'
);

-- Ollama embedding vectors (for semantic / cosine similarity search)
CREATE TABLE file_vectors (
    path       TEXT PRIMARY KEY,
    model      TEXT NOT NULL,     -- "nomic-embed-text" — invalidate on model change
    vector     BLOB NOT NULL,     -- float[] as little-endian bytes (768 floats = 3072 bytes)
    indexed_at TEXT NOT NULL
);

-- User-attached notes per file (persists across scans, keyed by path)
CREATE TABLE file_notes (
    path       TEXT PRIMARY KEY,
    note       TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

-- User-defined tags per file
CREATE TABLE file_tags (
    path TEXT NOT NULL,
    tag  TEXT NOT NULL,
    PRIMARY KEY (path, tag)
);
```

---

## New Services

### `OllamaEmbeddingService`
Calls Ollama's REST API (`localhost:11434/api/embeddings`) with the configured model.
Returns `float[]`. Handles connection failure gracefully (search degrades to FTS5-only).

### `ContentIndexer`
Runs after each `ScanStage` completes. For each new or changed file:
1. Calls `ContentExtractor.extract()` to get text
2. Stores text in `file_content` FTS5
3. Calls `OllamaEmbeddingService.embed()` to get vector
4. Stores vector in `file_vectors`

Runs on a background thread — does not block the scan result.

### `SearchService`
Facade over FTS5 + vector search. Two modes:

```
Full semantic (Ollama available):
  query → OllamaEmbeddingService → float[] → cosine similarity → ranked ids
       + FTS5 keyword match → additional ids
       → merge and deduplicate → top N results with score

Fallback (Ollama unavailable):
  query → FTS5 only → results (no semantic ranking)
```

Returns `List<SearchResult>` where each result has:
```java
record SearchResult(
    ScannedFile file,
    double score,        // combined similarity score [0,1]
    String reason,       // e.g. "filename match", "content match", "semantic similarity"
    String snippet       // short extracted text excerpt around the match
) {}
```

### `FileOperationService`
Wraps Java NIO for all mutating operations. Every operation writes to the existing `actions`
table — all moves and renames are undoable via the existing `ExecuteStage.cleanup()` mechanism.

```java
public interface FileOperationService {
    Path createFromTemplate(Template t, Path targetDir, String filename) throws IOException;
    void move(Path source, Path target) throws IOException;
    void rename(Path file, String newName) throws IOException;
    void delete(Path file) throws IOException;   // moves to archive, not permanent
    void attachNote(String filePath, String note);
    void addTag(String filePath, String tag);
    void removeTag(String filePath, String tag);
}
```

---

## New UI Views

### `SearchView` (replaces the old "Fuzzy File Finder")
- Single search bar, prominent, full-width
- Type-as-you-search (debounced 300ms)
- Result card per file: filename, path, MIME type icon, score badge, reason tag, snippet
- Click → `FileDetailView`
- Filter sidebar: file type, date range, tags

### `FileDetailView`
- File preview (text rendered inline for .txt/.md/.html; image thumbnail for images)
- Metadata panel: path, size, MIME type, last modified, scan date
- Notes editor (auto-save)
- Tags editor (chip input)
- Action buttons: Open in app · Move · Rename · Delete (to archive) · Find duplicates

### `OrganiseView`
- Tree or flat list of files filtered by folder / tag / type
- Multi-select with bulk actions: move, tag, delete
- "Suggest organisation" button: groups selected files by content similarity (uses existing MinHash)

### Updated `FmsDashboardView`
- Replaces CloneZapper's `DashboardView` in the full FMS app
- Shows: files indexed, last scan time, duplicates found, space recoverable
- Quick actions: New Scan · Search Files · Find Duplicates

---

## Migration Path from Current CloneZapper

The migration from the current monolith to the multi-module structure is a refactor,
not a rewrite. No logic changes — only package moves and POM splits.

### Phase 1 — Extract `fms-core`
Move to `fms-core` (package rename `com.clonezapper` → `com.fms.core`):
- All `model/` classes
- All `db/` repositories
- `service/HashService`, `service/ContentExtractor`, `service/NearDupService`, `service/CopyPatternDetector`
- `handler/` package (all handlers + registry)
- `provider/` package

### Phase 2 — Extract `fms-dedup`
Move to `fms-dedup` (package `com.fms.dedup`):
- `engine/` (UnifiedScanner + all pipeline stages)
- `cli/` (all CLI commands)
- `ui/` (ScanView, ResultsView, ReviewQueueView, DashboardView)

### Phase 3 — Create `fms-dedup-standalone`
Thin `@SpringBootApplication` bootstrap. All tests move here and to `fms-dedup`.
Verify: `mvn test -pl fms-dedup-standalone -am` — all 183 tests pass.

### Phase 4 — Build `fms-search`
New module. Adds `file_content`, `file_vectors` tables via schema migration.
Implements `OllamaEmbeddingService`, `ContentIndexer`, `SearchService`, `SearchView`, `FileDetailView`.

### Phase 5 — Build `fms-operations`
New module. Implements `FileOperationService`, `TemplateService`, `OrganiseView`, `TemplatesView`.

### Phase 6 — Assemble `fms-app`
Wire all modules into the full FMS Spring Boot application with unified navigation.

---

## Technology Stack

All additions are on top of the existing CloneZapper stack.

| Component | Technology | Notes |
|---|---|---|
| Embeddings | Ollama REST API (`localhost:11434`) | `nomic-embed-text` default model |
| Vector similarity | Java (cosine, pure JVM) | In-memory over SQLite BLOB vectors |
| Full-text search | SQLite FTS5 | Built into SQLite, no extra dependency |
| Text extraction | Apache Tika 2.9.2 | Already present |
| LLM (optional) | Ollama chat API | For result explanations; `llama3.2:3b` |
| Build | Maven multi-module | Consistent with existing tooling preference |
| UI | Vaadin 24 + Lumo dark | Already running |
| DB | SQLite (WAL mode) | Already running |
| HTTP client (Ollama) | Spring `RestClient` (built-in to Spring Boot 3.2) | No new dependency |

---

## Open Questions (deferred, not blockers)

| # | Question | Decision needed before |
|---|---|---|
| 1 | What is the user-facing product name? ("FileMind", "FileZen", other?) | Phase 4 (first user-visible feature) |
| 2 | Create: blank templates only, or AI-generated content via Ollama LLM? | Phase 5 |
| 3 | File watcher for auto-index (inotify/ReadDirectoryChanges) vs manual scan trigger? | Phase 4 |
| 4 | Should `fms-dedup-standalone` keep the brand name "CloneZapper" in its UI? | Phase 3 |
| 5 | HNSW approximate nearest-neighbour for >500k file collections? | Post-MVP |

---

## Milestones

| # | Milestone | Modules | Key deliverables |
|---|---|---|---|
| FMS-M1 | Multi-module restructure | fms-core, fms-dedup, fms-dedup-standalone | All 183 tests pass; CloneZapper standalone jar builds |
| FMS-M2 | Search — index | fms-search | ContentIndexer, OllamaEmbeddingService, file_content + file_vectors tables |
| FMS-M3 | Search — query | fms-search | SearchService, SearchView, FileDetailView; end-to-end search working |
| FMS-M4 | File operations | fms-operations | FileOperationService, notes, tags, OrganiseView |
| FMS-M5 | File creation | fms-operations | TemplateService, TemplatesView, optional AI draft via Ollama LLM |
| FMS-M6 | Full FMS assembly | fms-app | Unified navigation, FmsDashboardView, all arms integrated |

---

## Summary: What gets reused, what gets built new

### Reused from CloneZapper (zero rewrite)
- Entire file index pipeline (ScanStage → ContentExtractor → HashService → FileRepository)
- All file type handlers (DocumentHandler, ImageHandler, GenericHandler, HandlerRegistry)
- All dedup logic (CandidateStage, CompareStage, ClusterStage, ExecuteStage)
- SQLite schema (files, scans, duplicate_groups, duplicate_members, actions)
- Vaadin Lumo dark UI shell (AppShell, MainLayout)
- Incremental scan logic (fingerprint reuse)
- Archive exclusion, undo/restore model
- All 183 tests

### Built new for FMS
- `OllamaEmbeddingService` — vector generation via Ollama REST
- `ContentIndexer` — populates FTS5 and vector table after each scan
- `VectorSearchService` + `FtsSearchService` — search engines
- `SearchService` — merged facade
- `SearchView` + `FileDetailView` — search UI
- `FileOperationService` — create/move/rename/delete with undo
- `TemplateService` + `TemplatesView` — file creation
- `OrganiseView` — bulk organisation
- `FmsDashboardView` — unified FMS dashboard
- `fms-dedup-standalone` bootstrap — thin wrapper for standalone CloneZapper
- 4 new DB tables (file_content, file_vectors, file_notes, file_tags)
