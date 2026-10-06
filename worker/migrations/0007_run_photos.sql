CREATE TABLE run_photos (
 owner_id TEXT NOT NULL,
 run_id TEXT NOT NULL,
 revision TEXT NOT NULL,
 jpeg BLOB NOT NULL CHECK(length(jpeg) BETWEEN 4 AND 1000000),
 options TEXT NOT NULL,
 public_token TEXT UNIQUE,
 updated_at INTEGER NOT NULL,
 PRIMARY KEY(owner_id,run_id),
 FOREIGN KEY(owner_id,run_id) REFERENCES run_uploads(owner_id,run_id) ON DELETE CASCADE
);
