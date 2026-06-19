PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS schema_migrations (
  version TEXT PRIMARY KEY,
  applied_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS cloud_records (
  entity TEXT NOT NULL,
  id TEXT NOT NULL,
  revision INTEGER NOT NULL DEFAULT 1,
  device_id TEXT,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  deleted_at TEXT,
  data_json TEXT NOT NULL DEFAULT '{}',
  PRIMARY KEY (entity, id)
);

CREATE INDEX IF NOT EXISTS idx_cloud_records_updated
  ON cloud_records(updated_at);

CREATE INDEX IF NOT EXISTS idx_cloud_records_entity_updated
  ON cloud_records(entity, updated_at);

CREATE TABLE IF NOT EXISTS cloud_events (
  id TEXT PRIMARY KEY,
  entity TEXT NOT NULL,
  entity_id TEXT NOT NULL,
  operation TEXT NOT NULL,
  device_id TEXT,
  base_revision INTEGER,
  next_revision INTEGER,
  happened_at TEXT NOT NULL,
  payload_json TEXT NOT NULL DEFAULT '{}'
);

CREATE INDEX IF NOT EXISTS idx_cloud_events_entity
  ON cloud_events(entity, entity_id, happened_at);

CREATE INDEX IF NOT EXISTS idx_cloud_events_happened
  ON cloud_events(happened_at);

INSERT OR IGNORE INTO schema_migrations(version)
VALUES ('2026-06-20-001-cloud-records');
