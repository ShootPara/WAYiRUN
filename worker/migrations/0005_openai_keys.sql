-- Deleted credentials retain only a revision to reject stale concurrent saves.
CREATE TABLE openai_keys (
  owner_id TEXT PRIMARY KEY NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
  revision TEXT NOT NULL,
  action TEXT NOT NULL CHECK (action IN ('PUT', 'DELETE')),
  ciphertext TEXT,
  nonce TEXT,
  key_version TEXT,
  checked_at INTEGER,
  updated_at INTEGER NOT NULL,
  CHECK ((ciphertext IS NULL AND nonce IS NULL AND key_version IS NULL AND checked_at IS NULL)
    OR (ciphertext IS NOT NULL AND nonce IS NOT NULL AND key_version IS NOT NULL AND checked_at IS NOT NULL))
);
