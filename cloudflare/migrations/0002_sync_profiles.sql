PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS sync_profiles (
  id TEXT PRIMARY KEY,
  revision INTEGER NOT NULL DEFAULT 1,
  device_id TEXT,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  profile_json TEXT NOT NULL DEFAULT '{}'
);

CREATE INDEX IF NOT EXISTS idx_sync_profiles_updated
  ON sync_profiles(updated_at);

INSERT OR IGNORE INTO schema_migrations(version)
VALUES ('2026-06-28-002-sync-profiles');
