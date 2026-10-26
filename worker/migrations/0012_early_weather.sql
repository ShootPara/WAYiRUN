CREATE TABLE run_weather_early (
 owner_id TEXT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
 run_id TEXT NOT NULL,
 snapshot_json TEXT,
 retry_after INTEGER NOT NULL,
 attempt TEXT NOT NULL,
 PRIMARY KEY(owner_id,run_id)
);
CREATE TRIGGER delete_early_weather AFTER INSERT ON run_deletions BEGIN
 DELETE FROM run_weather_early WHERE owner_id=NEW.owner_id AND run_id=NEW.run_id;
END;
