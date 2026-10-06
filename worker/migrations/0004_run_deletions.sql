CREATE TABLE run_deletions (
  owner_id TEXT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
  run_id TEXT NOT NULL,
  deleted_at INTEGER NOT NULL,
  PRIMARY KEY (owner_id, run_id)
);
CREATE TRIGGER reject_deleted_run BEFORE INSERT ON run_uploads
WHEN EXISTS (SELECT 1 FROM run_deletions WHERE owner_id = NEW.owner_id AND run_id = NEW.run_id)
BEGIN
  SELECT RAISE(IGNORE);
END;
