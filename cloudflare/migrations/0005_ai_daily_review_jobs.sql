CREATE TABLE IF NOT EXISTS ai_daily_review_jobs (
  job_id TEXT PRIMARY KEY,
  input_digest TEXT NOT NULL,
  state TEXT NOT NULL CHECK (state IN ('RUNNING', 'SUCCEEDED', 'FAILED')),
  review_json TEXT,
  usage_json TEXT,
  finish_reason TEXT,
  upstream_requests INTEGER NOT NULL DEFAULT 0,
  error_code TEXT,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_ai_daily_review_jobs_state_updated
  ON ai_daily_review_jobs(state, updated_at);
