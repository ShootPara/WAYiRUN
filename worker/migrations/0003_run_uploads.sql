CREATE TABLE run_uploads (
  owner_id TEXT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
  run_id TEXT NOT NULL,
  operation_id TEXT NOT NULL,
  manifest_json TEXT NOT NULL,
  manifest_hash TEXT NOT NULL,
  chunk_count INTEGER NOT NULL CHECK (chunk_count BETWEEN 1 AND 128),
  created_at INTEGER NOT NULL,
  expires_at INTEGER NOT NULL,
  completed_at INTEGER,
  PRIMARY KEY (owner_id, run_id),
  UNIQUE (owner_id, operation_id),
  CHECK (expires_at > created_at)
);
CREATE INDEX run_uploads_history ON run_uploads(owner_id, completed_at, run_id);
CREATE INDEX run_uploads_pending ON run_uploads(owner_id, expires_at) WHERE completed_at IS NULL;
CREATE TABLE run_chunks (
  owner_id TEXT NOT NULL,
  run_id TEXT NOT NULL,
  chunk_index INTEGER NOT NULL CHECK (chunk_index BETWEEN 0 AND 127),
  sha256 TEXT NOT NULL,
  data BLOB NOT NULL CHECK (length(data) BETWEEN 1 AND 131072),
  PRIMARY KEY (owner_id, run_id, chunk_index),
  FOREIGN KEY (owner_id, run_id) REFERENCES run_uploads(owner_id, run_id) ON DELETE CASCADE
);
