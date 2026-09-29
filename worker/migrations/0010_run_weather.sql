CREATE TABLE run_weather (
 owner_id TEXT NOT NULL,
 run_id TEXT NOT NULL,
 snapshot_json TEXT,
 retry_after INTEGER NOT NULL,
 attempt TEXT NOT NULL,
 PRIMARY KEY(owner_id,run_id),
 FOREIGN KEY(owner_id,run_id) REFERENCES run_uploads(owner_id,run_id) ON DELETE CASCADE
);

CREATE TABLE weather_lookup_gate (
 id INTEGER PRIMARY KEY CHECK(id=1),
 next_at INTEGER NOT NULL
);
INSERT INTO weather_lookup_gate VALUES (1,0);
