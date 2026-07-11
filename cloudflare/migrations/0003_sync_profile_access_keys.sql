ALTER TABLE sync_profiles ADD COLUMN access_key_hash TEXT;

CREATE INDEX IF NOT EXISTS idx_sync_profiles_access_key_hash
  ON sync_profiles(access_key_hash);

INSERT OR IGNORE INTO schema_migrations(version)
VALUES ('2026-07-11-003-sync-profile-access-keys');
