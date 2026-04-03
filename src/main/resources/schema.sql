-- CloneZapper SQLite schema
-- All tables use IF NOT EXISTS so re-running on startup is safe.

CREATE TABLE IF NOT EXISTS scans (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    phase               TEXT    NOT NULL DEFAULT 'SCAN',
    checkpoint_file_id  TEXT,
    delta_token         TEXT,
    created_at          TEXT    NOT NULL,
    completed_at        TEXT,
    archive_root        TEXT,
    run_label           TEXT,
    last_heartbeat      TEXT
);

CREATE TABLE IF NOT EXISTS files (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    scan_id             INTEGER NOT NULL REFERENCES scans(id),
    path                TEXT    NOT NULL,
    provider            TEXT    NOT NULL DEFAULT 'local',
    size                INTEGER NOT NULL,
    modified_at         TEXT,
    mime_type           TEXT,
    hash_partial        TEXT,
    hash_full           TEXT,
    minhash_signature   BLOB,
    copy_hint           TEXT
);

CREATE TABLE IF NOT EXISTS duplicate_groups (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    scan_id             INTEGER NOT NULL REFERENCES scans(id),
    canonical_file_id   INTEGER REFERENCES files(id),
    strategy            TEXT,
    confidence          REAL
);

CREATE TABLE IF NOT EXISTS duplicate_members (
    group_id            INTEGER NOT NULL REFERENCES duplicate_groups(id),
    file_id             INTEGER NOT NULL REFERENCES files(id),
    confidence          REAL,
    PRIMARY KEY (group_id, file_id)
);

CREATE TABLE IF NOT EXISTS actions (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    scan_id             INTEGER NOT NULL REFERENCES scans(id),
    action_type         TEXT    NOT NULL,
    file_id             INTEGER NOT NULL REFERENCES files(id),
    destination         TEXT,
    original_path       TEXT,
    executed_at         TEXT,
    undone              INTEGER NOT NULL DEFAULT 0,
    cleaned             INTEGER NOT NULL DEFAULT 0,
    purged              INTEGER NOT NULL DEFAULT 0
);

-- Indexes for common query patterns
CREATE INDEX IF NOT EXISTS idx_files_scan    ON files(scan_id);
CREATE INDEX IF NOT EXISTS idx_files_hash    ON files(hash_partial);
CREATE INDEX IF NOT EXISTS idx_files_size    ON files(size);
CREATE INDEX IF NOT EXISTS idx_actions_scan  ON actions(scan_id);
CREATE INDEX IF NOT EXISTS idx_actions_file  ON actions(file_id);
