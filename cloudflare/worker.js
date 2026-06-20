const SCHEMA_VERSION = "2026-06-20-cloud-records-v1";

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
          time: new Date().toISOString()
        }, 200, request, env);
      }

      const client = requireAuth(request, env);

      if (url.pathname === "/api/bootstrap" && request.method === "POST") {
        return json({
          ok: true,
          serverTime: new Date().toISOString(),
          schemaVersion: SCHEMA_VERSION,
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
  const entities = sanitizeEntities(body.entities).filter(entity => canRead(client.role, entity));
  const since = body.since ? String(body.since) : null;
  if (!entities.length) {
    return { ok: true, serverTime: new Date().toISOString(), records: [] };
  }
  const placeholders = entities.map(() => "?").join(",");
  const where = [`entity IN (${placeholders})`];
  const params = [...entities];
  if (since) {
    where.push("updated_at > ?");
    params.push(since);
  }

  const result = await env.DB.prepare(
    `SELECT entity, id, revision, device_id, created_at, updated_at, deleted_at, data_json
     FROM cloud_records
     WHERE ${where.join(" AND ")}
     ORDER BY updated_at ASC, entity ASC, id ASC`
  ).bind(...params).all();

  return {
    ok: true,
    serverTime: new Date().toISOString(),
    records: (result.results || []).map(rowToRecord)
  };
}

async function upsertRecords(env, body, client) {
  const deviceId = String(body.deviceId || client.deviceId || "unknown_device");
  const records = Array.isArray(body.records) ? body.records : [];
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

    const existing = await env.DB.prepare(
      "SELECT entity, id, revision, device_id, created_at, updated_at, deleted_at, data_json FROM cloud_records WHERE entity = ? AND id = ?"
    ).bind(entity, id).first();

    const baseRevision = Number(record.baseRevision ?? record.revision ?? 0);
    if (existing && baseRevision > 0 && existing.revision > baseRevision) {
      conflicts.push({
        entity,
        id,
        reason: "server_revision_newer",
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
    serverTime: new Date().toISOString(),
    accepted,
    conflicts
  };
}

function rowToRecord(row) {
  return {
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
    "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
    "Access-Control-Allow-Headers": "Authorization, Content-Type, X-Shenke-Cloud-Key, X-Shenke-Device-Id, X-Shenke-Sync-Key",
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
