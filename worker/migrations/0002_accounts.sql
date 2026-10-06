CREATE TABLE accounts (
  id TEXT PRIMARY KEY NOT NULL,
  google_subject TEXT NOT NULL UNIQUE,
  display_name TEXT,
  picture_url TEXT,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL
);
CREATE TABLE login_challenges (
  nonce_hash TEXT PRIMARY KEY NOT NULL,
  expires_at INTEGER NOT NULL
);
CREATE INDEX login_challenges_expiry ON login_challenges(expires_at);
CREATE TABLE auth_sessions (
  token_hash TEXT PRIMARY KEY NOT NULL,
  owner_id TEXT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
  created_at INTEGER NOT NULL,
  expires_at INTEGER NOT NULL,
  revoked_at INTEGER,
  CHECK (expires_at > created_at)
);
CREATE INDEX auth_sessions_owner ON auth_sessions(owner_id);
CREATE INDEX auth_sessions_expiry ON auth_sessions(expires_at);
-- Keep bootstrap version 1 compatible with the already deployed health-only Worker.
