PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS mcp_pairing_codes (
  code_hash TEXT PRIMARY KEY,
  expires_at TEXT NOT NULL,
  used_at TEXT,
  created_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS mcp_oauth_clients (
  client_id TEXT PRIMARY KEY,
  redirect_uris_json TEXT NOT NULL,
  client_name TEXT,
  created_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS mcp_oauth_authorization_codes (
  code_hash TEXT PRIMARY KEY,
  client_id TEXT NOT NULL,
  redirect_uri TEXT NOT NULL,
  code_challenge TEXT NOT NULL,
  scope TEXT NOT NULL,
  resource TEXT NOT NULL,
  expires_at TEXT NOT NULL,
  used_at TEXT,
  created_at TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_mcp_oauth_codes_client
  ON mcp_oauth_authorization_codes(client_id, expires_at);

CREATE TABLE IF NOT EXISTS mcp_oauth_tokens (
  access_token_hash TEXT PRIMARY KEY,
  refresh_token_hash TEXT NOT NULL UNIQUE,
  client_id TEXT NOT NULL,
  scope TEXT NOT NULL,
  resource TEXT NOT NULL,
  access_expires_at TEXT NOT NULL,
  refresh_expires_at TEXT NOT NULL,
  revoked_at TEXT,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_mcp_oauth_tokens_refresh
  ON mcp_oauth_tokens(refresh_token_hash, refresh_expires_at);

INSERT OR IGNORE INTO schema_migrations(version)
VALUES ('2026-07-22-004-mcp-oauth');
