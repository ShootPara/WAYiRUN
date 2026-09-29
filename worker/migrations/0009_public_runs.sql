CREATE TABLE public_runs (
 owner_id TEXT NOT NULL,
 run_id TEXT NOT NULL,
 public_token TEXT NOT NULL UNIQUE,
 short_token TEXT UNIQUE,
 shared INTEGER NOT NULL DEFAULT 0 CHECK(shared IN (0,1)),
 photo_visible INTEGER NOT NULL DEFAULT 1 CHECK(photo_visible IN (0,1)),
 revision INTEGER NOT NULL DEFAULT 0,
 last_operation TEXT,
 PRIMARY KEY(owner_id,run_id),
 FOREIGN KEY(owner_id,run_id) REFERENCES run_uploads(owner_id,run_id) ON DELETE CASCADE
);

-- Short tokens are minted with Web Crypto on the next authenticated read/mutation.
INSERT INTO public_runs(owner_id,run_id,public_token,shared,photo_visible)
 SELECT p.owner_id,p.run_id,p.public_token,1,1 FROM run_photos p
 JOIN run_uploads r ON r.owner_id=p.owner_id AND r.run_id=p.run_id
 WHERE p.public_token IS NOT NULL AND r.completed_at IS NOT NULL
 AND NOT EXISTS(SELECT 1 FROM run_deletions d WHERE d.owner_id=p.owner_id AND d.run_id=p.run_id);

CREATE TABLE publication_operations (
 owner_id TEXT NOT NULL,
 run_id TEXT NOT NULL,
 operation_id TEXT NOT NULL,
 request_json TEXT NOT NULL,
 PRIMARY KEY(owner_id,run_id,operation_id),
 FOREIGN KEY(owner_id,run_id) REFERENCES public_runs(owner_id,run_id) ON DELETE CASCADE
);
