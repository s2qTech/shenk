-- 身刻 / home-training-timer shared Cloudflare D1 schema
-- Target: Cloudflare D1 SQLite
-- Last updated: 2026-06-19

PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS schema_migrations (
  version TEXT PRIMARY KEY,
  applied_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS app_meta (
  key TEXT PRIMARY KEY,
  value_json TEXT NOT NULL,
  updated_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS devices (
  id TEXT PRIMARY KEY,
  label TEXT NOT NULL,
  platform TEXT NOT NULL, -- web_desktop | web_mobile | android_shell | timer_web
  last_seen_at TEXT,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS plan_templates (
  id TEXT PRIMARY KEY,
  version TEXT NOT NULL,
  title TEXT NOT NULL,
  status TEXT NOT NULL DEFAULT 'draft', -- draft | active | archived
  created_by TEXT NOT NULL DEFAULT 'coach',
  effective_from TEXT,
  effective_to TEXT,
  goal_json TEXT NOT NULL DEFAULT '[]',
  rules_json TEXT NOT NULL DEFAULT '{}',
  notes_json TEXT NOT NULL DEFAULT '[]',
  data_json TEXT NOT NULL DEFAULT '{}',
  revision INTEGER NOT NULL DEFAULT 1,
  device_id TEXT,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  deleted_at TEXT
);

CREATE INDEX IF NOT EXISTS idx_plan_templates_status
  ON plan_templates(status, effective_from);

CREATE TABLE IF NOT EXISTS routine_templates (
  id TEXT PRIMARY KEY,
  version TEXT NOT NULL,
  title TEXT NOT NULL,
  training_type TEXT NOT NULL,
  estimated_minutes INTEGER,
  default_options_json TEXT NOT NULL DEFAULT '{}',
  steps_json TEXT NOT NULL DEFAULT '[]',
  data_json TEXT NOT NULL DEFAULT '{}',
  revision INTEGER NOT NULL DEFAULT 1,
  device_id TEXT,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  deleted_at TEXT,
  UNIQUE(id, version)
);

CREATE INDEX IF NOT EXISTS idx_routine_templates_type
  ON routine_templates(training_type, updated_at);

CREATE TABLE IF NOT EXISTS daily_plan_items (
  id TEXT PRIMARY KEY,
  date TEXT NOT NULL,
  source_plan_id TEXT,
  source_plan_version TEXT,
  training_type TEXT NOT NULL,
  title TEXT NOT NULL,
  goal TEXT,
  estimated_minutes INTEGER,
  intensity INTEGER,
  needs_timer INTEGER NOT NULL DEFAULT 0,
  routine_id TEXT,
  routine_version TEXT,
  timer_options_json TEXT NOT NULL DEFAULT '{}',
  notes_json TEXT NOT NULL DEFAULT '[]',
  snapshot_json TEXT NOT NULL DEFAULT '{}',
  status TEXT NOT NULL DEFAULT 'planned',
  sort_order INTEGER NOT NULL DEFAULT 0,
  revision INTEGER NOT NULL DEFAULT 1,
  device_id TEXT,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  deleted_at TEXT,
  FOREIGN KEY (source_plan_id) REFERENCES plan_templates(id),
  FOREIGN KEY (routine_id) REFERENCES routine_templates(id)
);

CREATE INDEX IF NOT EXISTS idx_daily_plan_items_date
  ON daily_plan_items(date, sort_order);

CREATE INDEX IF NOT EXISTS idx_daily_plan_items_type
  ON daily_plan_items(training_type, date);

CREATE TABLE IF NOT EXISTS plan_adjustments (
  id TEXT PRIMARY KEY,
  date TEXT NOT NULL,
  target_daily_plan_item_id TEXT NOT NULL,
  adjusted_by TEXT NOT NULL DEFAULT 'coach',
  adjusted_at TEXT NOT NULL,
  reason TEXT,
  from_snapshot_json TEXT NOT NULL DEFAULT '{}',
  to_snapshot_json TEXT NOT NULL DEFAULT '{}',
  revision INTEGER NOT NULL DEFAULT 1,
  device_id TEXT,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  deleted_at TEXT,
  FOREIGN KEY (target_daily_plan_item_id) REFERENCES daily_plan_items(id)
);

CREATE INDEX IF NOT EXISTS idx_plan_adjustments_date
  ON plan_adjustments(date, adjusted_at);

CREATE INDEX IF NOT EXISTS idx_plan_adjustments_target
  ON plan_adjustments(target_daily_plan_item_id, adjusted_at);

CREATE TABLE IF NOT EXISTS timer_sessions (
  id TEXT PRIMARY KEY,
  date TEXT NOT NULL,
  daily_plan_item_id TEXT,
  routine_id TEXT NOT NULL,
  routine_version TEXT,
  started_at TEXT NOT NULL,
  ended_at TEXT,
  actual_seconds INTEGER,
  completion TEXT NOT NULL DEFAULT 'completed',
  step_results_json TEXT NOT NULL DEFAULT '[]',
  notes TEXT,
  source TEXT NOT NULL DEFAULT 'home_training_timer',
  revision INTEGER NOT NULL DEFAULT 1,
  device_id TEXT,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  deleted_at TEXT,
  FOREIGN KEY (daily_plan_item_id) REFERENCES daily_plan_items(id),
  FOREIGN KEY (routine_id) REFERENCES routine_templates(id)
);

CREATE INDEX IF NOT EXISTS idx_timer_sessions_date
  ON timer_sessions(date, started_at);

CREATE INDEX IF NOT EXISTS idx_timer_sessions_plan
  ON timer_sessions(daily_plan_item_id, started_at);

CREATE TABLE IF NOT EXISTS training_logs (
  id TEXT PRIMARY KEY,
  date TEXT NOT NULL,
  daily_plan_item_id TEXT,
  timer_session_id TEXT,
  type TEXT NOT NULL,
  status TEXT NOT NULL,
  source TEXT NOT NULL DEFAULT 'manual', -- manual | timer | screenshot | import | coach_adjusted
  duration_sec INTEGER,
  distance_km REAL,
  avg_pace_sec_per_km INTEGER,
  best_pace_sec_per_km INTEGER,
  avg_heart_rate INTEGER,
  max_heart_rate INTEGER,
  steps INTEGER,
  cadence INTEGER,
  stride_cm INTEGER,
  training_effect REAL,
  training_load INTEGER,
  recovery_hours INTEGER,
  laps_json TEXT NOT NULL DEFAULT '[]',
  notes TEXT,
  raw_json TEXT NOT NULL DEFAULT '{}',
  revision INTEGER NOT NULL DEFAULT 1,
  device_id TEXT,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  deleted_at TEXT,
  FOREIGN KEY (daily_plan_item_id) REFERENCES daily_plan_items(id),
  FOREIGN KEY (timer_session_id) REFERENCES timer_sessions(id)
);

CREATE INDEX IF NOT EXISTS idx_training_logs_date
  ON training_logs(date);

CREATE INDEX IF NOT EXISTS idx_training_logs_type
  ON training_logs(type, date);

CREATE TABLE IF NOT EXISTS body_metrics (
  id TEXT PRIMARY KEY,
  date TEXT NOT NULL UNIQUE,
  weight_kg REAL,
  waist_cm REAL,
  body_fat_pct REAL,
  muscle_kg REAL,
  body_water_pct REAL,
  basal_metabolism_kcal INTEGER,
  sleep_quality TEXT,
  energy INTEGER,
  fatigue INTEGER,
  pain_json TEXT NOT NULL DEFAULT '{}',
  notes TEXT,
  raw_json TEXT NOT NULL DEFAULT '{}',
  revision INTEGER NOT NULL DEFAULT 1,
  device_id TEXT,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  deleted_at TEXT
);

CREATE INDEX IF NOT EXISTS idx_body_metrics_date
  ON body_metrics(date);

CREATE TABLE IF NOT EXISTS weather_logs (
  id TEXT PRIMARY KEY,
  date TEXT NOT NULL,
  location_name TEXT,
  latitude REAL,
  longitude REAL,
  temperature_c REAL,
  humidity_pct REAL,
  precipitation_mm REAL,
  wind_speed_kmh REAL,
  condition TEXT,
  source TEXT NOT NULL DEFAULT 'manual',
  raw_json TEXT NOT NULL DEFAULT '{}',
  revision INTEGER NOT NULL DEFAULT 1,
  device_id TEXT,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  deleted_at TEXT
);

CREATE INDEX IF NOT EXISTS idx_weather_logs_date
  ON weather_logs(date);

CREATE TABLE IF NOT EXISTS media_assets (
  id TEXT PRIMARY KEY,
  date TEXT,
  kind TEXT NOT NULL, -- workout_screenshot | body_scale_screenshot | other
  local_ref TEXT,
  remote_ref TEXT,
  mime_type TEXT,
  size_bytes INTEGER,
  sha256 TEXT,
  recognition_status TEXT NOT NULL DEFAULT 'none', -- none | pending | parsed | failed | confirmed
  recognition_json TEXT NOT NULL DEFAULT '{}',
  notes TEXT,
  revision INTEGER NOT NULL DEFAULT 1,
  device_id TEXT,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  deleted_at TEXT
);

CREATE INDEX IF NOT EXISTS idx_media_assets_date
  ON media_assets(date, kind);

CREATE TABLE IF NOT EXISTS feedback_summaries (
  id TEXT PRIMARY KEY,
  period_from TEXT NOT NULL,
  period_to TEXT NOT NULL,
  summary_json TEXT NOT NULL,
  status TEXT NOT NULL DEFAULT 'draft', -- draft | exported | used_for_plan
  revision INTEGER NOT NULL DEFAULT 1,
  device_id TEXT,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  deleted_at TEXT
);

CREATE INDEX IF NOT EXISTS idx_feedback_summaries_period
  ON feedback_summaries(period_from, period_to);

CREATE TABLE IF NOT EXISTS sync_events (
  id TEXT PRIMARY KEY,
  entity TEXT NOT NULL,
  entity_id TEXT NOT NULL,
  operation TEXT NOT NULL, -- upsert | delete | conflict | resolve
  device_id TEXT,
  base_revision INTEGER,
  next_revision INTEGER,
  happened_at TEXT NOT NULL,
  payload_json TEXT NOT NULL DEFAULT '{}'
);

CREATE INDEX IF NOT EXISTS idx_sync_events_entity
  ON sync_events(entity, entity_id, happened_at);

CREATE INDEX IF NOT EXISTS idx_sync_events_happened
  ON sync_events(happened_at);

INSERT OR IGNORE INTO schema_migrations(version)
VALUES ('2026-06-19-001-initial-shared-schema');
