CREATE TABLE coaching_jobs (
  owner_id TEXT NOT NULL,
  run_id TEXT NOT NULL,
  operation_id TEXT NOT NULL,
  state TEXT NOT NULL CHECK (state IN ('preparing','text_pending','speech_pending','ready','failed','unknown')),
  current_hash TEXT,
  previous_run_id TEXT,
  previous_hash TEXT,
  key_revision TEXT NOT NULL,
  token_hash TEXT NOT NULL,
  message TEXT,
  error_code TEXT,
  audio_bytes INTEGER,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL,
  PRIMARY KEY (owner_id, run_id),
  UNIQUE (owner_id, operation_id),
  FOREIGN KEY (owner_id, run_id) REFERENCES run_uploads(owner_id, run_id) ON DELETE CASCADE
);
CREATE TABLE coaching_audio (
  owner_id TEXT NOT NULL,
  run_id TEXT NOT NULL,
  chunk_index INTEGER NOT NULL,
  data BLOB NOT NULL CHECK (length(data) BETWEEN 1 AND 131072),
  PRIMARY KEY (owner_id, run_id, chunk_index),
  FOREIGN KEY (owner_id, run_id) REFERENCES coaching_jobs(owner_id, run_id) ON DELETE CASCADE
);
CREATE TRIGGER remove_dependent_coaching AFTER DELETE ON run_uploads
BEGIN
  DELETE FROM coaching_audio WHERE owner_id=OLD.owner_id AND run_id IN
    (SELECT run_id FROM coaching_jobs WHERE owner_id=OLD.owner_id AND previous_run_id=OLD.run_id);
  UPDATE coaching_jobs SET state='failed',message=NULL,audio_bytes=NULL,error_code='context_deleted'
    WHERE owner_id=OLD.owner_id AND previous_run_id=OLD.run_id;
END;
