const SCHEMA_VERSION = "2026-06-28-cloud-records-v2";
const CONTRACT_VERSION = "1.0";
const MAX_UPSERT_RECORDS = 100;
const MAX_RECORD_DATA_BYTES = 256 * 1024;
const DEFAULT_QUERY_LIMIT = 200;
const MAX_QUERY_LIMIT = 500;

const ENTITIES = [
  "plan_templates",
  "routine_templates",
  "daily_plan_items",
  "plan_adjustments",
  "timer_sessions",
  "timer_session_links",
  "training_logs",
  "body_metrics",
  "weather_logs",
  "media_assets",
  "feedback_summaries"
];

const ROLE_WRITE_ENTITIES = {
  admin: new Set(ENTITIES),
  shenk: new Set([
    "plan_templates",
    "routine_templates",
    "daily_plan_items",
    "plan_adjustments",
    "timer_session_links",
    "training_logs",
    "body_metrics",
    "weather_logs",
    "media_assets",
    "feedback_summaries"
  ]),
  timer: new Set(["timer_sessions"])
};

const ROLE_READ_ENTITIES = {
  admin: new Set(ENTITIES),
  shenk: new Set(ENTITIES),
  timer: new Set(["routine_templates", "daily_plan_items", "timer_sessions"])
};

export default {
  async fetch(request, env) {
    if (request.method === "OPTIONS") return new Response(null, { headers: corsHeaders(request, env) });

    const url = new URL(request.url);
    if (!url.pathname.startsWith("/api")) return json({ ok: false, error: "not_found" }, 404, request, env);

    try {
      if (url.pathname === "/api/health" && request.method === "GET") {
        return json({
          ok: true,
          service: "shenke-cloud-db",
          schemaVersion: SCHEMA_VERSION,
          contractVersion: CONTRACT_VERSION,
          time: new Date().toISOString()
        }, 200, request, env);
      }

      const syncProfileMatch = url.pathname.match(/^\/api\/sync-profiles\/([^/]+)$/);
      if (syncProfileMatch && request.method === "GET") {
        return json(await getSyncProfile(env, syncProfileMatch[1], {
          client: getOptionalAuthClient(request, env),
          profileAccessKey: request.headers.get("X-Shenke-Profile-Key") || ""
        }), 200, request, env);
      }

      const client = requireAuth(request, env);

      if (syncProfileMatch && request.method === "PUT") {
        const body = await readJson(request);
        return json(await upsertSyncProfile(env, syncProfileMatch[1], body, client), 200, request, env);
      }

      if (url.pathname === "/api/bootstrap" && request.method === "POST") {
        return json({
          ok: true,
          serverTime: new Date().toISOString(),
          schemaVersion: SCHEMA_VERSION,
          contractVersion: CONTRACT_VERSION,
          records: {
            plan_templates: [],
            routine_templates: [],
            daily_plan_items: []
          }
        }, 200, request, env);
      }

      if (url.pathname === "/api/records/query" && request.method === "POST") {
        const body = await readJson(request);
        return json(await queryRecords(env, body, client), 200, request, env);
      }

      if (url.pathname === "/api/records/upsert" && request.method === "POST") {
        const body = await readJson(request);
        return json(await upsertRecords(env, body, client), 200, request, env);
      }

      if (url.pathname === "/api/sync/pull" && request.method === "POST") {
        const body = await readJson(request);
        return json(await queryRecords(env, body, client), 200, request, env);
      }

      if (url.pathname === "/api/sync/push" && request.method === "POST") {
        const body = await readJson(request);
        return json(await upsertRecords(env, body, client), 200, request, env);
      }

      if (url.pathname === "/api/timer-sessions" && request.method === "POST") {
        assertCanWrite(client.role, "timer_sessions");
        const body = await readJson(request);
        const session = body.timerSession || body.data || body;
        return json(await upsertRecords(env, {
          contractVersion: body.contractVersion || session.contractVersion,
          deviceId: body.deviceId || session.deviceId || "timer_web",
          records: [{
            entity: "timer_sessions",
            id: session.id,
            baseRevision: body.baseRevision || session.revision || 0,
            data: session,
            createdAt: session.createdAt,
            updatedAt: session.updatedAt,
            deletedAt: session.deletedAt || null
          }]
        }, client), 200, request, env);
      }

      if (url.pathname === "/api/training-logs" && request.method === "POST") {
        assertCanWrite(client.role, "training_logs");
        const body = await readJson(request);
        const log = body.trainingLog || body.data || body;
        return json(await upsertRecords(env, {
          contractVersion: body.contractVersion || log.contractVersion,
          deviceId: body.deviceId || log.deviceId || "shenke_web",
          records: [{
            entity: "training_logs",
            id: log.id,
            baseRevision: body.baseRevision || log.revision || 0,
            data: log,
            createdAt: log.createdAt,
            updatedAt: log.updatedAt,
            deletedAt: log.deletedAt || null
          }]
        }, client), 200, request, env);
      }

      if (url.pathname === "/api/body-metrics" && request.method === "POST") {
        assertCanWrite(client.role, "body_metrics");
        const body = await readJson(request);
        const metric = body.bodyMetric || body.data || body;
        return json(await upsertRecords(env, {
          contractVersion: body.contractVersion || metric.contractVersion,
          deviceId: body.deviceId || metric.deviceId || "shenke_web",
          records: [{
            entity: "body_metrics",
            id: metric.id,
            baseRevision: body.baseRevision || metric.revision || 0,
            data: metric,
            createdAt: metric.createdAt,
            updatedAt: metric.updatedAt,
            deletedAt: metric.deletedAt || null
          }]
        }, client), 200, request, env);
      }

      if (url.pathname === "/api/daily-plan-items" && request.method === "POST") {
        assertCanWrite(client.role, "daily_plan_items");
        const body = await readJson(request);
        const item = body.dailyPlanItem || body.data || body;
        return json(await upsertRecords(env, {
          contractVersion: body.contractVersion || item.contractVersion,
          deviceId: body.deviceId || item.deviceId || "shenke_web",
          records: [{
            entity: "daily_plan_items",
            id: item.id,
            baseRevision: body.baseRevision || item.revision || 0,
            data: item,
            createdAt: item.createdAt,
            updatedAt: item.updatedAt,
            deletedAt: item.deletedAt || null
          }]
        }, client), 200, request, env);
      }

      return json({ ok: false, error: "not_found" }, 404, request, env);
    } catch (error) {
      const status = error.status || 500;
      return json({ ok: false, error: error.message || "server_error" }, status, request, env);
    }
  }
};

async function queryRecords(env, body, client) {
  assertSupportedContractVersion(body.contractVersion);
  const entities = sanitizeEntities(body.entities).filter(entity => canRead(client.role, entity));
  const since = body.since ? String(body.since) : null;
  const page = normalizeQueryPage(body);
  if (!entities.length) {
    return { ok: true, contractVersion: CONTRACT_VERSION, serverTime: new Date().toISOString(), records: [], nextCursor: null };
  }
  const placeholders = entities.map(() => "?").join(",");
  const where = [`entity IN (${placeholders})`];
  const params = [...entities];
  if (since) {
    where.push("updated_at > ?");
    params.push(since);
  }
  if (page.cursor) {
    where.push("(updated_at > ? OR (updated_at = ? AND (entity > ? OR (entity = ? AND id > ?))))");
    params.push(page.cursor.updatedAt, page.cursor.updatedAt, page.cursor.entity, page.cursor.entity, page.cursor.id);
  }

  const paginationSql = page.limit ? " LIMIT ?" : "";
  if (page.limit) params.push(page.limit + 1);

  const result = await env.DB.prepare(
    `SELECT entity, id, revision, device_id, created_at, updated_at, deleted_at, data_json
     FROM cloud_records
     WHERE ${where.join(" AND ")}
     ORDER BY updated_at ASC, entity ASC, id ASC${paginationSql}`
  ).bind(...params).all();

  const rows = result.results || [];
  const hasMore = Boolean(page.limit && rows.length > page.limit);
  const visibleRows = hasMore ? rows.slice(0, page.limit) : rows;
  const lastRow = visibleRows.at(-1);

  return {
    ok: true,
    contractVersion: CONTRACT_VERSION,
    serverTime: new Date().toISOString(),
    records: visibleRows.map(rowToRecord),
    nextCursor: hasMore && lastRow ? encodeQueryCursor(lastRow) : null
  };
}

function normalizeQueryPage(body) {
  const paginationRequested = body.limit !== undefined || body.cursor !== undefined;
  if (!paginationRequested) return { limit: null, cursor: null };
  const requested = Number(body.limit ?? DEFAULT_QUERY_LIMIT);
  if (!Number.isFinite(requested) || requested < 1 || requested > MAX_QUERY_LIMIT || Math.floor(requested) !== requested) {
    const error = new Error("invalid_query_limit");
    error.status = 400;
    throw error;
  }
  return { limit: requested, cursor: decodeQueryCursor(body.cursor) };
}

function encodeQueryCursor(row) {
  return encodeURIComponent(JSON.stringify({ updatedAt: row.updated_at, entity: row.entity, id: row.id }));
}

function decodeQueryCursor(value) {
  if (value === undefined || value === null || value === "") return null;
  try {
    const parsed = JSON.parse(decodeURIComponent(String(value)));
    if (!parsed || !parsed.updatedAt || !ENTITIES.includes(parsed.entity) || !parsed.id) throw new Error("invalid");
    return { updatedAt: String(parsed.updatedAt), entity: String(parsed.entity), id: String(parsed.id) };
  } catch (cause) {
    const error = new Error("invalid_query_cursor");
    error.status = 400;
    throw error;
  }
}

async function upsertRecords(env, body, client) {
  assertSupportedContractVersion(body.contractVersion);
  const deviceId = String(body.deviceId || client.deviceId || "unknown_device");
  const records = Array.isArray(body.records) ? body.records : [];
  if (records.length > MAX_UPSERT_RECORDS) {
    const error = new Error("too_many_records");
    error.status = 413;
    throw error;
  }
  const accepted = [];
  const conflicts = [];

  for (const record of records) {
    const entity = String(record.entity || "");
    const id = String(record.id || record.data?.id || "");
    if (!ENTITIES.includes(entity) || !id || !record.data || typeof record.data !== "object") {
      conflicts.push({ entity, id, reason: "invalid_record" });
      continue;
    }
    if (!canWrite(client.role, entity)) {
      conflicts.push({ entity, id, reason: "forbidden_entity_for_role", role: client.role });
      continue;
    }
    const validationError = validateRecordForUpsert(entity, id, record.data, record.deletedAt);
    if (validationError) {
      conflicts.push({ entity, id, reason: validationError });
      continue;
    }

    const existing = await env.DB.prepare(
      "SELECT entity, id, revision, device_id, created_at, updated_at, deleted_at, data_json FROM cloud_records WHERE entity = ? AND id = ?"
    ).bind(entity, id).first();

    if (existing && isPublishedTemplateRecord(entity, existing) && !hasSameStoredPayload(existing, record.data, record.deletedAt || null)) {
      conflicts.push({
        entity,
        id,
        reason: "immutable_template_requires_new_id",
        serverRecord: rowToRecord(existing),
        clientRecord: record
      });
      continue;
    }

    const baseRevision = Number(record.baseRevision ?? record.revision ?? 0);
    if (existing && baseRevision !== Number(existing.revision || 0)) {
      if (hasSameStoredPayload(existing, record.data, record.deletedAt || null)) {
        accepted.push({
          entity,
          id,
          revision: Number(existing.revision || 0),
          updatedAt: existing.updated_at
        });
        continue;
      }
      conflicts.push({
        entity,
        id,
        reason: "server_revision_mismatch",
        serverRecord: rowToRecord(existing),
        clientRecord: record
      });
      continue;
    }

    const now = new Date().toISOString();
    const nextRevision = existing ? Number(existing.revision || 0) + 1 : Math.max(1, Number(record.revision || 1));
    const createdAt = existing?.created_at || record.createdAt || record.data.createdAt || now;
    const updatedAt = now;
    const deletedAt = record.deletedAt || null;

    await env.DB.prepare(
      `INSERT INTO cloud_records(entity, id, revision, device_id, created_at, updated_at, deleted_at, data_json)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?)
       ON CONFLICT(entity, id) DO UPDATE SET
         revision = excluded.revision,
         device_id = excluded.device_id,
         updated_at = excluded.updated_at,
         deleted_at = excluded.deleted_at,
         data_json = excluded.data_json`
    ).bind(entity, id, nextRevision, deviceId, createdAt, updatedAt, deletedAt, JSON.stringify(record.data)).run();

    await env.DB.prepare(
      `INSERT INTO cloud_events(id, entity, entity_id, operation, device_id, base_revision, next_revision, happened_at, payload_json)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)`
    ).bind(crypto.randomUUID(), entity, id, deletedAt ? "delete" : "upsert", deviceId, baseRevision, nextRevision, now, JSON.stringify(record)).run();

    accepted.push({ entity, id, revision: nextRevision, updatedAt });
  }

  return {
    ok: true,
    contractVersion: CONTRACT_VERSION,
    serverTime: new Date().toISOString(),
    accepted,
    conflicts
  };
}

function validateRecordForUpsert(entity, id, data, deletedAt) {
  if (data.contractVersion !== undefined && data.contractVersion !== CONTRACT_VERSION) return "unsupported_contract_version";
  if (String(id).length > 160 || /[\u0000-\u001f]/.test(String(id))) return "invalid_record_id";
  if (data.id !== undefined && String(data.id) !== String(id)) return "record_id_mismatch";
  let serialized = "";
  try {
    serialized = JSON.stringify(data);
  } catch (error) {
    return "record_data_not_serializable";
  }
  if (!serialized || serialized.length > MAX_RECORD_DATA_BYTES) return "record_payload_too_large";
  if (deletedAt) return null;
  if (data.date !== undefined && !isIsoDate(data.date)) return "invalid_record_date";
  if (["daily_plan_items", "plan_adjustments", "training_logs", "body_metrics", "weather_logs", "timer_sessions"].includes(entity) && !isIsoDate(data.date)) {
    return "missing_record_date";
  }
  if (entity === "routine_templates" && data.steps !== undefined && !Array.isArray(data.steps)) return "invalid_routine_steps";
  if (entity === "timer_sessions") {
    const completion = String(data.completion || "");
    if (!["in_progress", "completed", "stopped"].includes(completion)) return "invalid_timer_completion";
    if (!["actualSeconds", "elapsedSeconds", "pausedSeconds", "plannedSeconds"].every((key) => data[key] === undefined || (Number.isFinite(Number(data[key])) && Number(data[key]) >= 0))) {
      return "invalid_timer_duration";
    }
  }
  return null;
}

function assertSupportedContractVersion(value) {
  if (value === undefined || value === null || value === "") return;
  if (String(value) === CONTRACT_VERSION) return;
  const error = new Error("unsupported_contract_version");
  error.status = 400;
  throw error;
}

function hasSameStoredPayload(existing, data, deletedAt) {
  return String(existing.deleted_at || "") === String(deletedAt || "")
    && String(existing.data_json || "") === JSON.stringify(data);
}

function isPublishedTemplateRecord(entity, row) {
  if (!["plan_templates", "routine_templates"].includes(entity) || row.deleted_at) return false;
  const data = safeJson(row.data_json, {});
  return data.immutable === true || data.lifecycle === "published" || Boolean(data.publishedAt || data.published_at);
}

function isIsoDate(value) {
  const text = String(value || "");
  if (!/^\d{4}-\d{2}-\d{2}$/.test(text)) return false;
  const date = new Date(`${text}T00:00:00.000Z`);
  return !Number.isNaN(date.getTime()) && date.toISOString().slice(0, 10) === text;
}

async function getSyncProfile(env, rawId, options = {}) {
  const client = options.client || null;
  const profileAccessKey = String(options.profileAccessKey || "");
  const canManage = Boolean(client && ["admin", "shenk"].includes(client.role));
  if (!canManage && !isValidProfileAccessKey(profileAccessKey)) {
    const error = new Error("unauthorized");
    error.status = 401;
    throw error;
  }
  await ensureSyncProfilesTable(env);
  const id = normalizeSyncProfileId(rawId);
  const row = await env.DB.prepare(
    "SELECT id, revision, device_id, created_at, updated_at, profile_json, access_key_hash FROM sync_profiles WHERE id = ?"
  ).bind(id).first();
  if (!row) {
    const error = new Error("sync_profile_not_found");
    error.status = 404;
    throw error;
  }
  if (!canManage && !row.access_key_hash) {
    const error = new Error("sync_profile_access_key_required");
    error.status = 401;
    throw error;
  }
  if (!canManage && (await hashProfileAccessKey(profileAccessKey)) !== row.access_key_hash) {
    const error = new Error("unauthorized");
    error.status = 401;
    throw error;
  }
  return {
    ok: true,
    id: row.id,
    revision: row.revision,
    deviceId: row.device_id || null,
    createdAt: row.created_at,
    updatedAt: row.updated_at,
    profile: safeJson(row.profile_json, {})
  };
}

async function upsertSyncProfile(env, rawId, body, client) {
  await ensureSyncProfilesTable(env);
  assertCanManageSyncProfiles(client.role);
  const id = normalizeSyncProfileId(rawId);
  const profile = body.profile || body.data || body;
  if (!isValidEncryptedSyncProfile(profile)) {
    const error = new Error("invalid_sync_profile");
    error.status = 400;
    throw error;
  }
  const profileAccessKey = String(body.profileAccessKey || "");
  if (!isValidProfileAccessKey(profileAccessKey)) {
    const error = new Error("invalid_sync_profile_access_key");
    error.status = 400;
    throw error;
  }
  const accessKeyHash = await hashProfileAccessKey(profileAccessKey);
  const existing = await env.DB.prepare(
    "SELECT revision, created_at FROM sync_profiles WHERE id = ?"
  ).bind(id).first();
  const now = new Date().toISOString();
  const nextRevision = existing ? Number(existing.revision || 0) + 1 : 1;
  await env.DB.prepare(
    `INSERT INTO sync_profiles(id, revision, device_id, created_at, updated_at, profile_json, access_key_hash)
     VALUES (?, ?, ?, ?, ?, ?, ?)
     ON CONFLICT(id) DO UPDATE SET
       revision = excluded.revision,
       device_id = excluded.device_id,
       updated_at = excluded.updated_at,
       profile_json = excluded.profile_json,
       access_key_hash = excluded.access_key_hash`
  ).bind(
    id,
    nextRevision,
    client.deviceId || String(body.deviceId || "shenke_web"),
    existing?.created_at || now,
    now,
    JSON.stringify(profile),
    accessKeyHash
  ).run();
  return { ok: true, id, revision: nextRevision, updatedAt: now };
}

function assertCanManageSyncProfiles(role) {
  if (["admin", "shenk"].includes(role)) return;
  const error = new Error("forbidden_sync_profile_role");
  error.status = 403;
  throw error;
}

async function ensureSyncProfilesTable(env) {
  await env.DB.prepare(
    `CREATE TABLE IF NOT EXISTS sync_profiles (
      id TEXT PRIMARY KEY,
      revision INTEGER NOT NULL DEFAULT 1,
      device_id TEXT,
      created_at TEXT NOT NULL,
      updated_at TEXT NOT NULL,
      profile_json TEXT NOT NULL DEFAULT '{}',
      access_key_hash TEXT
    )`
  ).run();
  await env.DB.prepare(
    "CREATE INDEX IF NOT EXISTS idx_sync_profiles_updated ON sync_profiles(updated_at)"
  ).run();
}

function normalizeSyncProfileId(value) {
  const id = String(value || "").trim();
  if (!/^[a-zA-Z0-9_-]{6,80}$/.test(id)) {
    const error = new Error("invalid_sync_profile_id");
    error.status = 400;
    throw error;
  }
  return id;
}

function isValidEncryptedSyncProfile(profile) {
  if (!profile || typeof profile !== "object") return false;
  if (profile.schema !== "shenk_sync_profile/v1") return false;
  if (profile.kdf !== "PBKDF2-SHA256") return false;
  if (profile.cipher !== "AES-GCM") return false;
  if (!Number.isFinite(Number(profile.iterations)) || Number(profile.iterations) < 100000) return false;
  return ["salt", "iv", "ciphertext"].every((key) => typeof profile[key] === "string" && profile[key].length >= 12);
}

function isValidProfileAccessKey(value) {
  return /^[A-Za-z0-9_-]{20,200}$/.test(String(value || ""));
}

async function hashProfileAccessKey(value) {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(String(value)));
  return Array.from(new Uint8Array(digest)).map((byte) => byte.toString(16).padStart(2, "0")).join("");
}

function rowToRecord(row) {
  return {
    contractVersion: CONTRACT_VERSION,
    entity: row.entity,
    id: row.id,
    revision: row.revision,
    deviceId: row.device_id || null,
    createdAt: row.created_at,
    updatedAt: row.updated_at,
    deletedAt: row.deleted_at || null,
    data: safeJson(row.data_json, {})
  };
}

function sanitizeEntities(value) {
  if (!Array.isArray(value) || !value.length) return ENTITIES;
  const entities = value.map(String).filter(entity => ENTITIES.includes(entity));
  return entities.length ? entities : ENTITIES;
}

async function readJson(request) {
  try {
    return await request.json();
  } catch (error) {
    const next = new Error("invalid_json");
    next.status = 400;
    throw next;
  }
}

function requireAuth(request, env) {
  requireAnyAuthConfigured(env);
  const auth = request.headers.get("Authorization") || "";
  const bearer = auth.startsWith("Bearer ") ? auth.slice(7) : "";
  const token = bearer || request.headers.get("X-Shenke-Cloud-Key") || request.headers.get("X-Shenke-Sync-Key") || "";
  const role = resolveTokenRole(token, env);
  if (!role) {
    const error = new Error("unauthorized");
    error.status = 401;
    throw error;
  }
  return {
    role,
    deviceId: request.headers.get("X-Shenke-Device-Id") || null
  };
}

function getOptionalAuthClient(request, env) {
  const auth = request.headers.get("Authorization") || "";
  const bearer = auth.startsWith("Bearer ") ? auth.slice(7) : "";
  const token = bearer || request.headers.get("X-Shenke-Cloud-Key") || request.headers.get("X-Shenke-Sync-Key") || "";
  const role = resolveTokenRole(token, env);
  return role ? { role, deviceId: request.headers.get("X-Shenke-Device-Id") || null } : null;
}

function resolveTokenRole(token, env) {
  if (!token) return "";
  if (env.ADMIN_TOKEN && token === env.ADMIN_TOKEN) return "admin";
  if (env.SHENK_TOKEN && token === env.SHENK_TOKEN) return "shenk";
  if (env.TIMER_TOKEN && token === env.TIMER_TOKEN) return "timer";
  if (env.SYNC_TOKEN && token === env.SYNC_TOKEN) return "admin";
  return "";
}

function canRead(role, entity) {
  return Boolean(ROLE_READ_ENTITIES[role]?.has(entity));
}

function canWrite(role, entity) {
  return Boolean(ROLE_WRITE_ENTITIES[role]?.has(entity));
}

function assertCanWrite(role, entity) {
  if (canWrite(role, entity)) return;
  const error = new Error("forbidden_entity_for_role");
  error.status = 403;
  throw error;
}

function requireAnyAuthConfigured(env) {
  if (!env.ADMIN_TOKEN && !env.SHENK_TOKEN && !env.TIMER_TOKEN && !env.SYNC_TOKEN) {
    const error = new Error("server_missing_auth_token");
    error.status = 500;
    throw error;
  }
}

function json(payload, status, request, env) {
  return new Response(JSON.stringify(payload), {
    status,
    headers: {
      ...corsHeaders(request, env),
      "Content-Type": "application/json; charset=utf-8",
      "Cache-Control": "no-store"
    }
  });
}

function corsHeaders(request, env) {
  const requestOrigin = request.headers.get("Origin") || "";
  const configured = String(env.ALLOWED_ORIGINS || "").split(",").map(item => item.trim()).filter(Boolean);
  const allowedOrigin = configured.length
    ? configured.includes(requestOrigin) ? requestOrigin : configured[0]
    : "*";
  return {
    "Access-Control-Allow-Origin": allowedOrigin,
    "Access-Control-Allow-Methods": "GET, POST, PUT, OPTIONS",
    "Access-Control-Allow-Headers": "Authorization, Content-Type, X-Shenke-Cloud-Key, X-Shenke-Device-Id, X-Shenke-Sync-Key, X-Shenke-Profile-Key",
    "Access-Control-Max-Age": "86400",
    "Vary": "Origin"
  };
}

function safeJson(value, fallback) {
  try {
    return JSON.parse(value);
  } catch (error) {
    return fallback;
  }
}
