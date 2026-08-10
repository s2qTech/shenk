const SCHEMA_VERSION = "2026-06-28-cloud-records-v2";
const ACTIVE_CONTRACT_VERSION = "1.0";
const SUPPORTED_CONTRACT_VERSIONS = new Set(["1.0", "2.0"]);
const MAX_UPSERT_RECORDS = 100;
const MAX_RECORD_DATA_BYTES = 256 * 1024;
const DEFAULT_QUERY_LIMIT = 200;
const MAX_QUERY_LIMIT = 500;
const MCP_PROTOCOL_VERSION = "2025-06-18";
const MCP_ACCESS_TOKEN_SECONDS = 60 * 60;
const MCP_REFRESH_TOKEN_SECONDS = 30 * 24 * 60 * 60;
const MCP_PAIRING_CODE_SECONDS = 10 * 60;
const MCP_AUTH_CODE_SECONDS = 5 * 60;
const MCP_SCOPES = ["planning:read", "planning:draft"];
const MCP_SNAPSHOT_ENTITIES = [
  "plan_templates",
  "routine_templates",
  "daily_plan_items",
  "plan_adjustments",
  "timer_sessions",
  "training_logs",
  "body_metrics",
  "feedback_summaries",
  "status_checkins",
  "daily_reviews",
  "goal_sets",
  "coach_strategies"
];

const V1_ENTITIES = [
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

const V2_ENTITIES = [
  ...V1_ENTITIES,
  "status_checkins",
  "daily_reviews",
  "plan_import_batches",
  "goal_sets",
  "coach_strategies",
  "planning_runs",
  "coach_plan_patches"
];

const ENTITIES = V2_ENTITIES;

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
    "status_checkins",
    "weather_logs",
    "media_assets",
    "feedback_summaries",
    "daily_reviews",
    "plan_import_batches",
    "goal_sets",
    "coach_strategies",
    "planning_runs",
    "coach_plan_patches"
  ]),
  timer: new Set(["timer_sessions"]),
  mcp: new Set(["planning_runs", "coach_plan_patches"])
};

const ROLE_READ_ENTITIES = {
  admin: new Set(ENTITIES),
  shenk: new Set(ENTITIES),
  timer: new Set(["routine_templates", "daily_plan_items", "timer_sessions"]),
  mcp: new Set([
    "plan_templates",
    "routine_templates",
    "daily_plan_items",
    "plan_adjustments",
    "timer_sessions",
    "training_logs",
    "body_metrics",
    "feedback_summaries",
    "status_checkins",
    "daily_reviews",
    "goal_sets",
    "coach_strategies",
    "planning_runs",
    "coach_plan_patches"
  ])
};

export default {
  async fetch(request, env) {
    if (request.method === "OPTIONS") return new Response(null, { headers: corsHeaders(request, env) });

    const url = new URL(request.url);

    try {
      if (url.pathname === "/.well-known/oauth-protected-resource" || url.pathname === "/.well-known/oauth-protected-resource/mcp") {
        return json(protectedResourceMetadata(url), 200, request, env);
      }

      if (url.pathname === "/.well-known/oauth-authorization-server" && request.method === "GET") {
        return json(authorizationServerMetadata(url), 200, request, env);
      }

      if (url.pathname === "/oauth/register" && request.method === "POST") {
        return json(await registerOAuthClient(request, env), 201, request, env);
      }

      if (url.pathname === "/oauth/authorize" && request.method === "GET") {
        return await renderOAuthAuthorization(request, env);
      }

      if (url.pathname === "/oauth/authorize-ui.js" && request.method === "GET") {
        return oauthAuthorizationUiScript();
      }

      if (url.pathname === "/oauth/authorize" && request.method === "POST") {
        return await approveOAuthAuthorization(request, env);
      }

      if (url.pathname === "/oauth/token" && request.method === "POST") {
        try {
          return oauthTokenResponse(await exchangeOAuthToken(request, env), 200, request, env);
        } catch (error) {
          if (!error.oauthError) throw error;
          return oauthTokenResponse({ error: error.message }, error.status || 400, request, env);
        }
      }

      if (url.pathname === "/mcp" && request.method === "POST") {
        return await handleMcpRequest(request, env);
      }

      if (!url.pathname.startsWith("/api")) return json({ ok: false, error: "not_found" }, 404, request, env);

      if (url.pathname === "/api/health" && request.method === "GET") {
        return json({
          ok: true,
          service: "shenke-cloud-db",
          schemaVersion: SCHEMA_VERSION,
          contractVersion: ACTIVE_CONTRACT_VERSION,
          supportedContractVersions: [...SUPPORTED_CONTRACT_VERSIONS],
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

      if (url.pathname === "/api/mcp/pairing-code" && request.method === "POST") {
        if (!["admin", "shenk"].includes(client.role)) {
          const error = new Error("forbidden_mcp_pairing_role");
          error.status = 403;
          throw error;
        }
        return json(await createMcpPairingCode(env), 201, request, env);
      }

      if (syncProfileMatch && request.method === "PUT") {
        const body = await readJson(request);
        return json(await upsertSyncProfile(env, syncProfileMatch[1], body, client), 200, request, env);
      }

      if (url.pathname === "/api/bootstrap" && request.method === "POST") {
        return json({
          ok: true,
          serverTime: new Date().toISOString(),
          schemaVersion: SCHEMA_VERSION,
          contractVersion: ACTIVE_CONTRACT_VERSION,
          records: {
            plan_templates: [],
            routine_templates: [],
            daily_plan_items: []
          }
        }, 200, request, env);
      }

      if (url.pathname === "/api/ai/connection-test" && request.method === "POST") {
        assertAiReviewRole(client);
        const body = await readJson(request);
        return json(await testAiProviderConnection(body), 200, request, env);
      }

      if (url.pathname === "/api/ai/daily-review" && request.method === "POST") {
        assertAiReviewRole(client);
        const body = await readJson(request);
        return json(await generateDailyReview(body), 200, request, env);
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

function protectedResourceMetadata(url) {
  return {
    resource: `${url.origin}/mcp`,
    authorization_servers: [url.origin],
    scopes_supported: MCP_SCOPES,
    bearer_methods_supported: ["header"]
  };
}

function authorizationServerMetadata(url) {
  return {
    issuer: url.origin,
    authorization_endpoint: `${url.origin}/oauth/authorize`,
    token_endpoint: `${url.origin}/oauth/token`,
    registration_endpoint: `${url.origin}/oauth/register`,
    response_types_supported: ["code"],
    grant_types_supported: ["authorization_code", "refresh_token"],
    token_endpoint_auth_methods_supported: ["none"],
    code_challenge_methods_supported: ["S256"],
    scopes_supported: MCP_SCOPES
  };
}

async function registerOAuthClient(request, env) {
  const body = await readJson(request);
  const redirectUris = Array.isArray(body.redirect_uris) ? body.redirect_uris.map(String) : [];
  if (!redirectUris.length || redirectUris.length > 10 || redirectUris.some(uri => !isAllowedOAuthRedirectUri(uri))) {
    const error = new Error("invalid_redirect_uris");
    error.status = 400;
    throw error;
  }
  if (body.token_endpoint_auth_method && body.token_endpoint_auth_method !== "none") {
    const error = new Error("unsupported_token_endpoint_auth_method");
    error.status = 400;
    throw error;
  }
  const clientId = `mcp_client_${randomOpaqueToken(18)}`;
  const now = new Date().toISOString();
  await env.DB.prepare(
    "INSERT INTO mcp_oauth_clients(client_id, redirect_uris_json, client_name, created_at) VALUES (?, ?, ?, ?)"
  ).bind(clientId, JSON.stringify(redirectUris), String(body.client_name || "ChatGPT").slice(0, 120), now).run();
  return {
    client_id: clientId,
    client_id_issued_at: Math.floor(Date.parse(now) / 1000),
    redirect_uris: redirectUris,
    client_name: String(body.client_name || "ChatGPT").slice(0, 120),
    token_endpoint_auth_method: "none",
    grant_types: ["authorization_code", "refresh_token"],
    response_types: ["code"]
  };
}

function isAllowedOAuthRedirectUri(value) {
  try {
    const url = new URL(String(value));
    if (url.protocol === "https:") return true;
    return url.protocol === "http:" && ["127.0.0.1", "localhost", "[::1]"].includes(url.hostname);
  } catch (error) {
    return false;
  }
}

async function renderOAuthAuthorization(request, env) {
  const url = new URL(request.url);
  const auth = await validateOAuthAuthorizationRequest(url.searchParams, env, url.origin);
  return renderOAuthAuthorizationPage(auth);
}

function renderOAuthAuthorizationPage(auth, errorMessage = "") {
  const callbackOrigin = new URL(auth.redirectUri).origin;
  const hidden = Object.entries(auth.formValues)
    .map(([name, value]) => `<input type="hidden" name="${escapeHtml(name)}" value="${escapeHtml(value)}">`)
    .join("");
  const errorNotice = errorMessage
    ? `<div class="error" role="alert">${escapeHtml(errorMessage)}</div>`
    : "";
  const body = `<!doctype html>
<html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>连接身刻</title><style>
body{margin:0;background:#f5f7f3;color:#17231e;font-family:system-ui,-apple-system,"Segoe UI",sans-serif;display:grid;min-height:100vh;place-items:center}
main{width:min(420px,calc(100vw - 40px));background:#fff;border:1px solid #dce5df;border-radius:16px;padding:28px;box-shadow:0 18px 50px rgba(25,56,43,.12)}
h1{font-size:26px;margin:0 0 10px}p{color:#5f7068;line-height:1.6;margin:0 0 22px}label{display:block;font-weight:650;margin-bottom:8px}
input[type=text]{box-sizing:border-box;width:100%;height:52px;border:1px solid #bac9c0;border-radius:10px;padding:0 14px;font-size:20px;letter-spacing:2px;text-transform:uppercase}
button{width:100%;height:52px;margin-top:16px;border:0;border-radius:10px;background:#426f59;color:#fff;font-size:17px;font-weight:650}button:disabled{cursor:wait;opacity:.72}
small{display:block;color:#7b8a82;margin-top:16px;line-height:1.5}.error{margin:0 0 18px;padding:12px 14px;border:1px solid #e6b8b2;border-radius:10px;background:#fff3f1;color:#9d3329;line-height:1.5}</style></head>
<body><main><h1>连接 ChatGPT 与身刻</h1><p>输入身刻生成的一次性配对码。授权后 ChatGPT 只能读取规划快照并提交待确认草案。</p>
${errorNotice}<form id="oauth-authorization-form" method="post" action="/oauth/authorize">${hidden}<label for="pairing_code">一次性配对码</label><input id="pairing_code" name="pairing_code" type="text" inputmode="text" autocomplete="one-time-code" required autofocus><button id="oauth-submit" type="submit">确认连接</button></form>
<small>配对码十分钟内有效且只能使用一次。正式计划仍需在身刻中确认。</small></main><script src="/oauth/authorize-ui.js" defer></script></body></html>`;
  return new Response(body, {
    status: errorMessage ? 401 : 200,
    headers: {
      "Content-Type": "text/html; charset=utf-8",
      "Cache-Control": "no-store",
      "Content-Security-Policy": `default-src 'none'; style-src 'unsafe-inline'; script-src 'self'; form-action 'self' ${callbackOrigin}; base-uri 'none'; frame-ancestors 'none'`,
      "Referrer-Policy": "no-referrer"
    }
  });
}

function oauthAuthorizationUiScript() {
  const source = `(() => {
  const form = document.getElementById("oauth-authorization-form");
  const submit = document.getElementById("oauth-submit");
  if (!form || !submit) return;
  let submitted = false;
  form.addEventListener("submit", event => {
    if (submitted) {
      event.preventDefault();
      return;
    }
    submitted = true;
    submit.disabled = true;
    submit.setAttribute("aria-busy", "true");
    submit.textContent = "正在连接…";
  });
})();`;
  return new Response(source, {
    status: 200,
    headers: {
      "Content-Type": "application/javascript; charset=utf-8",
      "Cache-Control": "no-store",
      "X-Content-Type-Options": "nosniff"
    }
  });
}

async function validateOAuthAuthorizationRequest(params, env, origin) {
  const responseType = String(params.get("response_type") || "");
  const clientId = String(params.get("client_id") || "");
  const redirectUri = String(params.get("redirect_uri") || "");
  const state = String(params.get("state") || "");
  const codeChallenge = String(params.get("code_challenge") || "");
  const codeChallengeMethod = String(params.get("code_challenge_method") || "");
  const resource = String(params.get("resource") || `${origin}/mcp`);
  const scope = normalizeOAuthScope(params.get("scope"));
  if (responseType !== "code" || !clientId || !redirectUri || !state || !/^[A-Za-z0-9_-]{43,128}$/.test(codeChallenge) || codeChallengeMethod !== "S256") {
    const error = new Error("invalid_authorization_request");
    error.status = 400;
    throw error;
  }
  if (resource !== `${origin}/mcp`) {
    const error = new Error("invalid_resource");
    error.status = 400;
    throw error;
  }
  const client = await env.DB.prepare(
    "SELECT client_id, redirect_uris_json FROM mcp_oauth_clients WHERE client_id = ?"
  ).bind(clientId).first();
  const redirectUris = safeJson(client?.redirect_uris_json || "[]", []);
  if (!client || !redirectUris.includes(redirectUri)) {
    const error = new Error("invalid_oauth_client");
    error.status = 400;
    throw error;
  }
  return {
    clientId,
    redirectUri,
    state,
    codeChallenge,
    scope,
    resource,
    formValues: {
      response_type: responseType,
      client_id: clientId,
      redirect_uri: redirectUri,
      state,
      code_challenge: codeChallenge,
      code_challenge_method: codeChallengeMethod,
      scope,
      resource
    }
  };
}

function normalizeOAuthScope(value) {
  const requested = String(value || MCP_SCOPES.join(" ")).split(/\s+/).filter(Boolean);
  if (!requested.length || requested.some(scope => !MCP_SCOPES.includes(scope))) {
    const error = new Error("invalid_scope");
    error.status = 400;
    throw error;
  }
  return [...new Set(requested)].join(" ");
}

async function approveOAuthAuthorization(request, env) {
  const form = await request.formData();
  const params = new URLSearchParams();
  for (const name of ["response_type", "client_id", "redirect_uri", "state", "code_challenge", "code_challenge_method", "scope", "resource"]) {
    params.set(name, String(form.get(name) || ""));
  }
  const url = new URL(request.url);
  const auth = await validateOAuthAuthorizationRequest(params, env, url.origin);
  const pairingCode = normalizePairingCode(form.get("pairing_code"));
  const codeHash = await sha256Hex(pairingCode);
  const row = await env.DB.prepare(
    "SELECT code_hash, expires_at, used_at FROM mcp_pairing_codes WHERE code_hash = ?"
  ).bind(codeHash).first();
  const now = new Date();
  if (!row || row.used_at || Date.parse(row.expires_at) <= now.getTime()) {
    return renderOAuthAuthorizationPage(auth, "配对码无效、已使用或已经过期。请回到身刻重新生成，再在十分钟内粘贴到这里。");
  }
  const authorizationCode = randomOpaqueToken(32);
  const authorizationCodeHash = await sha256Hex(authorizationCode);
  const createdAt = now.toISOString();
  const expiresAt = new Date(now.getTime() + MCP_AUTH_CODE_SECONDS * 1000).toISOString();
  const consumed = await env.DB.prepare(
    "UPDATE mcp_pairing_codes SET used_at = ? WHERE code_hash = ? AND used_at IS NULL AND expires_at > ?"
  ).bind(createdAt, codeHash, createdAt).run();
  if (!consumed?.meta?.changes) {
    return renderOAuthAuthorizationPage(auth, "配对码已被使用或已经过期。请回到身刻重新生成，再在十分钟内粘贴到这里。");
  }
  await env.DB.prepare(
    `INSERT INTO mcp_oauth_authorization_codes(code_hash, client_id, redirect_uri, code_challenge, scope, resource, expires_at, used_at, created_at)
     VALUES (?, ?, ?, ?, ?, ?, ?, NULL, ?)`
  ).bind(authorizationCodeHash, auth.clientId, auth.redirectUri, auth.codeChallenge, auth.scope, auth.resource, expiresAt, createdAt).run();
  const redirect = new URL(auth.redirectUri);
  redirect.searchParams.set("code", authorizationCode);
  redirect.searchParams.set("state", auth.state);
  return Response.redirect(redirect.toString(), 303);
}

function normalizePairingCode(value) {
  return String(value || "").toUpperCase().replace(/[^A-Z2-9]/g, "");
}

async function createMcpPairingCode(env) {
  const raw = randomBase32(20);
  const normalized = normalizePairingCode(raw);
  const codeHash = await sha256Hex(normalized);
  const now = new Date();
  const expiresAt = new Date(now.getTime() + MCP_PAIRING_CODE_SECONDS * 1000).toISOString();
  await env.DB.prepare(
    "INSERT INTO mcp_pairing_codes(code_hash, expires_at, used_at, created_at) VALUES (?, ?, NULL, ?)"
  ).bind(codeHash, expiresAt, now.toISOString()).run();
  return { ok: true, pairingCode: raw, expiresAt };
}

async function exchangeOAuthToken(request, env) {
  const form = await request.formData();
  const grantType = String(form.get("grant_type") || "");
  if (grantType === "authorization_code") return exchangeAuthorizationCode(form, env);
  if (grantType === "refresh_token") return exchangeRefreshToken(form, env);
  throw oauthTokenFailure("unsupported_grant_type", "unsupported_grant_type", { hasGrantType: Boolean(grantType) });
}

async function exchangeAuthorizationCode(form, env) {
  const code = String(form.get("code") || "");
  const clientId = String(form.get("client_id") || "");
  const redirectUri = String(form.get("redirect_uri") || "");
  const verifier = String(form.get("code_verifier") || "");
  const resource = String(form.get("resource") || "");
  if (!code || !clientId || !redirectUri || !/^[A-Za-z0-9._~-]{43,128}$/.test(verifier)) {
    throw oauthTokenFailure("invalid_request", "missing_or_invalid_authorization_code_fields", {
      hasCode: Boolean(code),
      hasClientId: Boolean(clientId),
      hasRedirectUri: Boolean(redirectUri),
      verifierLength: verifier.length,
      hasResource: Boolean(resource)
    });
  }
  const row = await env.DB.prepare(
    "SELECT code_hash, client_id, redirect_uri, code_challenge, scope, resource, expires_at, used_at FROM mcp_oauth_authorization_codes WHERE code_hash = ?"
  ).bind(await sha256Hex(code)).first();
  const now = new Date();
  const challenge = bytesToBase64Url(new Uint8Array(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(verifier))));
  if (!row) throw oauthTokenFailure("invalid_grant", "authorization_code_not_found");
  if (row.used_at) throw oauthTokenFailure("invalid_grant", "authorization_code_already_used");
  if (Date.parse(row.expires_at) <= now.getTime()) throw oauthTokenFailure("invalid_grant", "authorization_code_expired");
  if (row.client_id !== clientId) throw oauthTokenFailure("invalid_grant", "client_id_mismatch");
  if (row.redirect_uri !== redirectUri) throw oauthTokenFailure("invalid_grant", "redirect_uri_mismatch");
  if (row.code_challenge !== challenge) throw oauthTokenFailure("invalid_grant", "pkce_verifier_mismatch", { verifierLength: verifier.length });
  if (resource && row.resource !== resource) throw oauthTokenFailure("invalid_target", "resource_mismatch", { hasResource: true });
  await env.DB.prepare("UPDATE mcp_oauth_authorization_codes SET used_at = ? WHERE code_hash = ? AND used_at IS NULL")
    .bind(now.toISOString(), row.code_hash).run();
  return issueOAuthTokens(env, { clientId, scope: row.scope, resource: row.resource });
}

async function exchangeRefreshToken(form, env) {
  const refreshToken = String(form.get("refresh_token") || "");
  const clientId = String(form.get("client_id") || "");
  const resource = String(form.get("resource") || "");
  if (!refreshToken || !clientId) throw oauthTokenFailure("invalid_request", "missing_refresh_token_fields", {
    hasRefreshToken: Boolean(refreshToken),
    hasClientId: Boolean(clientId),
    hasResource: Boolean(resource)
  });
  const refreshHash = await sha256Hex(refreshToken);
  const row = await env.DB.prepare(
    "SELECT refresh_token_hash, client_id, scope, resource, refresh_expires_at, revoked_at FROM mcp_oauth_tokens WHERE refresh_token_hash = ?"
  ).bind(refreshHash).first();
  if (!row) throw oauthTokenFailure("invalid_grant", "refresh_token_not_found");
  if (row.revoked_at) throw oauthTokenFailure("invalid_grant", "refresh_token_revoked");
  if (Date.parse(row.refresh_expires_at) <= Date.now()) throw oauthTokenFailure("invalid_grant", "refresh_token_expired");
  if (row.client_id !== clientId) throw oauthTokenFailure("invalid_grant", "refresh_client_id_mismatch");
  if (resource && row.resource !== resource) throw oauthTokenFailure("invalid_target", "refresh_resource_mismatch", { hasResource: true });
  await env.DB.prepare("UPDATE mcp_oauth_tokens SET revoked_at = ?, updated_at = ? WHERE refresh_token_hash = ? AND revoked_at IS NULL")
    .bind(new Date().toISOString(), new Date().toISOString(), refreshHash).run();
  return issueOAuthTokens(env, { clientId, scope: row.scope, resource: row.resource });
}

function oauthInvalidGrant() {
  const error = new Error("invalid_grant");
  error.status = 400;
  return error;
}

function oauthTokenFailure(code, reason, details = {}) {
  globalThis.console?.warn?.("oauth_token_exchange_failed", JSON.stringify({ reason, ...details }));
  const error = new Error(code);
  error.status = 400;
  error.oauthError = true;
  return error;
}

async function issueOAuthTokens(env, details) {
  const accessToken = randomOpaqueToken(32);
  const refreshToken = randomOpaqueToken(40);
  const now = new Date();
  const accessExpiresAt = new Date(now.getTime() + MCP_ACCESS_TOKEN_SECONDS * 1000).toISOString();
  const refreshExpiresAt = new Date(now.getTime() + MCP_REFRESH_TOKEN_SECONDS * 1000).toISOString();
  await env.DB.prepare(
    `INSERT INTO mcp_oauth_tokens(access_token_hash, refresh_token_hash, client_id, scope, resource, access_expires_at, refresh_expires_at, revoked_at, created_at, updated_at)
     VALUES (?, ?, ?, ?, ?, ?, ?, NULL, ?, ?)`
  ).bind(
    await sha256Hex(accessToken),
    await sha256Hex(refreshToken),
    details.clientId,
    details.scope,
    details.resource,
    accessExpiresAt,
    refreshExpiresAt,
    now.toISOString(),
    now.toISOString()
  ).run();
  return {
    access_token: accessToken,
    token_type: "Bearer",
    expires_in: MCP_ACCESS_TOKEN_SECONDS,
    refresh_token: refreshToken,
    scope: details.scope
  };
}

async function handleMcpRequest(request, env) {
  const url = new URL(request.url);
  let client;
  try {
    client = await requireMcpAuth(request, env, `${url.origin}/mcp`);
  } catch (error) {
    return new Response(JSON.stringify({ jsonrpc: "2.0", error: { code: -32001, message: "Unauthorized" }, id: null }), {
      status: 401,
      headers: {
        "Content-Type": "application/json; charset=utf-8",
        "Cache-Control": "no-store",
        "WWW-Authenticate": `Bearer resource_metadata="${url.origin}/.well-known/oauth-protected-resource/mcp", scope="${MCP_SCOPES.join(" ")}"`
      }
    });
  }
  let rpc;
  try {
    rpc = await request.json();
  } catch (error) {
    return mcpJsonRpcError(null, -32700, "Parse error");
  }
  if (!isObject(rpc) || rpc.jsonrpc !== "2.0" || Array.isArray(rpc)) return mcpJsonRpcError(rpc?.id ?? null, -32600, "Invalid Request");
  if (rpc.method === "notifications/initialized") return new Response(null, { status: 202 });
  if (rpc.method === "initialize") {
    return mcpJsonRpcResult(rpc.id, {
      protocolVersion: MCP_PROTOCOL_VERSION,
      capabilities: { tools: { listChanged: false } },
      serverInfo: { name: "shenk-planning", version: "1.0.0" }
    });
  }
  if (rpc.method === "ping") return mcpJsonRpcResult(rpc.id, {});
  if (rpc.method === "tools/list") return mcpJsonRpcResult(rpc.id, { tools: mcpToolDefinitions() });
  if (rpc.method === "tools/call") {
    const name = String(rpc.params?.name || "");
    const args = isObject(rpc.params?.arguments) ? rpc.params.arguments : {};
    try {
      const result = name === "get_planning_snapshot"
        ? await buildPlanningSnapshot(env, args, client)
        : name === "submit_coach_plan_patch"
          ? await submitCoachPlanPatch(env, args, client)
          : null;
      if (!result) return mcpJsonRpcError(rpc.id, -32602, "Unknown tool");
      return mcpJsonRpcResult(rpc.id, {
        content: [{ type: "text", text: JSON.stringify(result) }],
        structuredContent: result,
        isError: false
      });
    } catch (error) {
      return mcpJsonRpcResult(rpc.id, {
        content: [{ type: "text", text: error.message || "Tool failed" }],
        isError: true
      });
    }
  }
  return mcpJsonRpcError(rpc.id, -32601, "Method not found");
}

async function requireMcpAuth(request, env, resource) {
  const auth = request.headers.get("Authorization") || "";
  const token = auth.startsWith("Bearer ") ? auth.slice(7) : "";
  if (!token) throw oauthInvalidGrant();
  const row = await env.DB.prepare(
    "SELECT client_id, scope, resource, access_expires_at, revoked_at FROM mcp_oauth_tokens WHERE access_token_hash = ?"
  ).bind(await sha256Hex(token)).first();
  if (!row || row.revoked_at || Date.parse(row.access_expires_at) <= Date.now() || row.resource !== resource) throw oauthInvalidGrant();
  return { role: "mcp", clientId: row.client_id, scopes: String(row.scope).split(/\s+/), deviceId: "chatgpt_mcp" };
}

function mcpToolDefinitions() {
  return [
    {
      name: "get_planning_snapshot",
      title: "Get Shenk planning snapshot",
      description: "Read a bounded, sanitized snapshot of recent training, body status, current plans, goals, and routines for review and planning. Use planning.effectiveDailyPlans as the authoritative formal plan for each date; raw daily plan items and adjustments are audit inputs, not competing plans.",
      inputSchema: {
        type: "object",
        properties: {
          historyDays: { type: "integer", minimum: 7, maximum: 30, default: 14 },
          trendDays: { type: "integer", minimum: 14, maximum: 90, default: 30 },
          futureDays: { type: "integer", minimum: 7, maximum: 28, default: 14 }
        },
        additionalProperties: false
      },
      annotations: { readOnlyHint: true, destructiveHint: false, idempotentHint: true, openWorldHint: false }
    },
    {
      name: "submit_coach_plan_patch",
      title: "Submit Shenk plan draft",
      description: "Store a Contract v2 coach_plan_patch as a pending draft. Shenk validates, previews, and asks the user to confirm before any formal plan changes.",
      inputSchema: {
        type: "object",
        required: ["snapshotDigest", "period", "patch"],
        properties: {
          snapshotDigest: { type: "string", minLength: 1, maxLength: 160 },
          period: {
            type: "object",
            required: ["historyFrom", "historyTo", "planThrough"],
            properties: {
              historyFrom: { type: "string", format: "date" },
              historyTo: { type: "string", format: "date" },
              planThrough: { type: "string", format: "date" }
            },
            additionalProperties: false
          },
          patch: { type: "object" }
        },
        additionalProperties: false
      },
      annotations: { readOnlyHint: false, destructiveHint: false, idempotentHint: true, openWorldHint: false }
    }
  ];
}

async function buildPlanningSnapshot(env, args, client) {
  if (!client.scopes.includes("planning:read")) throw new Error("missing_scope:planning:read");
  const historyDays = boundedInteger(args.historyDays, 14, 7, 30, "historyDays");
  const trendDays = boundedInteger(args.trendDays, 30, 14, 90, "trendDays");
  const futureDays = boundedInteger(args.futureDays, 14, 7, 28, "futureDays");
  const today = dateInTimeZone(new Date(), "Asia/Shanghai");
  const period = {
    historyFrom: addIsoDays(today, -(historyDays - 1)),
    historyTo: today,
    planThrough: addIsoDays(today, futureDays)
  };
  const trendFrom = addIsoDays(today, -(trendDays - 1));
  const placeholders = MCP_SNAPSHOT_ENTITIES.map(() => "?").join(",");
  const result = await env.DB.prepare(
    `SELECT entity, id, revision, device_id, created_at, updated_at, deleted_at, data_json
     FROM cloud_records
     WHERE entity IN (${placeholders}) AND deleted_at IS NULL
     ORDER BY updated_at DESC
     LIMIT 2000`
  ).bind(...MCP_SNAPSHOT_ENTITIES).all();
  const grouped = Object.fromEntries(MCP_SNAPSHOT_ENTITIES.map(entity => [entity, []]));
  for (const row of result.results || []) {
    const data = sanitizeMcpValue(safeJson(row.data_json, {}));
    if (!planningRecordInWindow(row.entity, data, { ...period, trendFrom })) continue;
    grouped[row.entity].push({
      id: row.id,
      revision: row.revision,
      updatedAt: row.updated_at,
      data
    });
  }
  const planning = {
    semantics: {
      authoritativeField: "planning.effectiveDailyPlans",
      resolution: "latest valid adjustment for a date replaces its daily plan snapshot; otherwise the latest daily plan snapshot applies",
      auditOnlyFields: ["records.daily_plan_items", "records.plan_adjustments"],
      conflictRule: "a daily plan snapshot and its resolved adjustment are not a conflict and must not be presented as parallel instructions"
    },
    effectiveDailyPlans: resolveEffectiveDailyPlans(grouped.daily_plan_items, grouped.plan_adjustments)
  };
  const snapshotCore = { contractVersion: "2.0", timezone: "Asia/Shanghai", period, planning, records: grouped };
  const snapshotDigest = `sha256:${await sha256Hex(canonicalStringify(snapshotCore))}`;
  return { ...snapshotCore, generatedAt: new Date().toISOString(), snapshotDigest };
}

function resolveEffectiveDailyPlans(planRecords = [], adjustmentRecords = []) {
  const plansByDate = newestPlanningRecordByDate(planRecords, record => record.updatedAt);
  const adjustmentsByDate = newestPlanningRecordByDate(
    adjustmentRecords,
    record => record.data?.adjustedAt || record.updatedAt
  );
  const dates = [...new Set([...plansByDate.keys(), ...adjustmentsByDate.keys()])].sort();
  return dates.map(date => {
    const plan = plansByDate.get(date) || null;
    const adjustment = adjustmentsByDate.get(date) || null;
    if (!adjustment) {
      return {
        date,
        source: "daily_plan_item",
        dailyPlanItemId: plan?.id || null,
        adjustmentId: null,
        data: withPlanningDate(plan?.data || {}, date)
      };
    }
    const adjustmentSnapshot = isObject(adjustment.data?.toSnapshot)
      ? adjustment.data.toSnapshot
      : adjustment.data;
    return {
      date,
      source: "adjustment",
      dailyPlanItemId: adjustment.data?.targetDailyPlanItemId || plan?.id || null,
      adjustmentId: adjustment.id,
      adjustedAt: adjustment.data?.adjustedAt || adjustment.updatedAt || null,
      adjustedBy: adjustment.data?.adjustedBy || null,
      reason: adjustment.data?.reason || null,
      data: withPlanningDate(adjustmentSnapshot || {}, date)
    };
  });
}

function newestPlanningRecordByDate(records, timestampFor) {
  const latest = new Map();
  for (const record of records || []) {
    const date = String(record?.data?.date || "").slice(0, 10);
    if (!isIsoDate(date)) continue;
    const previous = latest.get(date);
    if (!previous || comparePlanningRecords(record, previous, timestampFor) > 0) latest.set(date, record);
  }
  return latest;
}

function comparePlanningRecords(left, right, timestampFor) {
  const leftTime = String(timestampFor(left) || "");
  const rightTime = String(timestampFor(right) || "");
  if (leftTime !== rightTime) return leftTime.localeCompare(rightTime);
  return String(left.id || "").localeCompare(String(right.id || ""));
}

function withPlanningDate(data, date) {
  return { ...data, date: String(data?.date || date).slice(0, 10) };
}

function planningRecordInWindow(entity, data, period) {
  if (["plan_templates", "routine_templates", "goal_sets", "coach_strategies"].includes(entity)) return true;
  const date = String(data.date || data.effectiveFrom || data.observedAt || data.startedAt || data.generatedAt || "").slice(0, 10);
  if (!isIsoDate(date)) return false;
  if (["daily_plan_items", "plan_adjustments"].includes(entity)) return date >= period.historyFrom && date <= period.planThrough;
  return date >= period.trendFrom && date <= period.historyTo;
}

function sanitizeMcpValue(value, key = "") {
  if (/token|api[_-]?key|password|secret|credential|migration[_-]?code/i.test(key)) return undefined;
  if (["rawJson", "rawSource"].includes(key)) return undefined;
  if (Array.isArray(value)) return value.map(item => sanitizeMcpValue(item)).filter(item => item !== undefined);
  if (!isObject(value)) return value;
  const copy = {};
  for (const [childKey, childValue] of Object.entries(value)) {
    const sanitized = sanitizeMcpValue(childValue, childKey);
    if (sanitized !== undefined) copy[childKey] = sanitized;
  }
  return copy;
}

async function submitCoachPlanPatch(env, args, client) {
  if (!client.scopes.includes("planning:draft")) throw new Error("missing_scope:planning:draft");
  const snapshotDigest = String(args.snapshotDigest || "");
  const period = args.period;
  if (!/^sha256:[a-f0-9]{64}$/.test(snapshotDigest)) throw new Error("invalid_snapshot_digest");
  if (!isValidPlanningPeriod(period)) throw new Error("invalid_planning_period");
  const patch = args.patch;
  const validationError = validateCoachPlanPatchDraft(patch, period);
  if (validationError) throw new Error(validationError);
  const patchHash = await sha256Hex(canonicalStringify({ snapshotDigest, period, patch }));
  const patchId = `coach_patch_${patchHash.slice(0, 32)}`;
  const runId = `planning_run_${patchHash.slice(0, 32)}`;
  const existing = await env.DB.prepare(
    "SELECT entity, id, revision, device_id, created_at, updated_at, deleted_at, data_json FROM cloud_records WHERE entity = ? AND id = ?"
  ).bind("coach_plan_patches", patchId).first();
  if (existing && !existing.deleted_at) {
    return { ok: true, duplicate: true, status: "pending", patchId, runId, receivedAt: safeJson(existing.data_json, {}).receivedAt || existing.created_at };
  }
  const now = new Date().toISOString();
  const patchRecord = { id: patchId, runId, status: "pending", receivedAt: now, snapshotDigest, patch };
  const runRecord = { id: runId, snapshotDigest, status: "submitted", requestedAt: now, submittedAt: now, period, source: "chatgpt_mcp", patchId };
  const result = await upsertRecords(env, {
    contractVersion: "2.0",
    deviceId: "chatgpt_mcp",
    records: [
      { entity: "coach_plan_patches", id: patchId, baseRevision: 0, data: patchRecord },
      { entity: "planning_runs", id: runId, baseRevision: 0, data: runRecord }
    ]
  }, { role: "mcp", deviceId: "chatgpt_mcp" });
  if (result.conflicts.length) throw new Error(`draft_store_conflict:${result.conflicts[0].reason}`);
  return { ok: true, duplicate: false, status: "pending", patchId, runId, receivedAt: now };
}

function validateCoachPlanPatchDraft(patch, period) {
  if (!isObject(patch) || patch.schema !== "coach_plan_patch" || patch.contractVersion !== "2.0") return "invalid_coach_plan_patch";
  if (!isIsoDate(patch.effectiveFrom) || (patch.effectiveTo && !isIsoDate(patch.effectiveTo))) return "invalid_patch_effective_date";
  if (patch.replaceMode === true) return "replace_mode_not_allowed";
  if (containsDeleteIntent(patch)) return "delete_not_allowed";
  const entities = [
    ["planTemplates", "plan_templates"],
    ["routineTemplates", "routine_templates"],
    ["dailyPlanItems", "daily_plan_items"],
    ["planAdjustments", "plan_adjustments"]
  ];
  let count = 0;
  for (const [field, entity] of entities) {
    if (patch[field] === undefined) continue;
    if (!Array.isArray(patch[field]) || patch[field].length > 100) return `invalid_patch_array:${field}`;
    for (const item of patch[field]) {
      count += 1;
      if (!isObject(item) || !item.id) return `${field}:invalid_record`;
      const error = validateRecordForUpsert("2.0", entity, item?.id, item, null);
      if (error) return `${field}:${error}`;
      if (item.date && (item.date < patch.effectiveFrom || item.date > period.planThrough)) return `${field}:date_outside_period`;
    }
  }
  if (!count) return "empty_patch";
  return null;
}

function containsDeleteIntent(value) {
  if (Array.isArray(value)) return value.some(containsDeleteIntent);
  if (!isObject(value)) return false;
  if (String(value.operation || "").toLowerCase() === "delete") return true;
  if (value.deletedAt !== undefined && value.deletedAt !== null && value.deletedAt !== "") return true;
  return Object.values(value).some(containsDeleteIntent);
}

function isValidPlanningPeriod(period) {
  return isObject(period)
    && isIsoDate(period.historyFrom)
    && isIsoDate(period.historyTo)
    && isIsoDate(period.planThrough)
    && period.historyFrom <= period.historyTo
    && period.historyTo <= period.planThrough;
}

function boundedInteger(value, fallback, minimum, maximum, name) {
  const number = value === undefined ? fallback : Number(value);
  if (!Number.isInteger(number) || number < minimum || number > maximum) throw new Error(`invalid_${name}`);
  return number;
}

function dateInTimeZone(date, timeZone) {
  const parts = new Intl.DateTimeFormat("en-CA", { timeZone, year: "numeric", month: "2-digit", day: "2-digit" })
    .formatToParts(date)
    .reduce((result, part) => ({ ...result, [part.type]: part.value }), {});
  return `${parts.year}-${parts.month}-${parts.day}`;
}

function addIsoDays(value, days) {
  const date = new Date(`${value}T00:00:00.000Z`);
  date.setUTCDate(date.getUTCDate() + days);
  return date.toISOString().slice(0, 10);
}

function canonicalStringify(value) {
  if (Array.isArray(value)) return `[${value.map(canonicalStringify).join(",")}]`;
  if (isObject(value)) return `{${Object.keys(value).sort().map(key => `${JSON.stringify(key)}:${canonicalStringify(value[key])}`).join(",")}}`;
  return JSON.stringify(value);
}

async function sha256Hex(value) {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(String(value)));
  return Array.from(new Uint8Array(digest)).map(byte => byte.toString(16).padStart(2, "0")).join("");
}

function randomOpaqueToken(byteLength) {
  const bytes = new Uint8Array(byteLength);
  crypto.getRandomValues(bytes);
  return bytesToBase64Url(bytes);
}

function randomBase32(length) {
  const alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
  const bytes = new Uint8Array(length);
  crypto.getRandomValues(bytes);
  const raw = Array.from(bytes, byte => alphabet[byte % alphabet.length]).join("");
  return raw.match(/.{1,5}/g).join("-");
}

function bytesToBase64Url(bytes) {
  const alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";
  let result = "";
  for (let index = 0; index < bytes.length; index += 3) {
    const a = bytes[index];
    const b = index + 1 < bytes.length ? bytes[index + 1] : 0;
    const c = index + 2 < bytes.length ? bytes[index + 2] : 0;
    result += alphabet[a >> 2];
    result += alphabet[((a & 3) << 4) | (b >> 4)];
    if (index + 1 < bytes.length) result += alphabet[((b & 15) << 2) | (c >> 6)];
    if (index + 2 < bytes.length) result += alphabet[c & 63];
  }
  return result;
}

function escapeHtml(value) {
  return String(value).replace(/[&<>"']/g, character => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", "\"": "&quot;", "'": "&#39;" })[character]);
}

function mcpJsonRpcResult(id, result) {
  return new Response(JSON.stringify({ jsonrpc: "2.0", id, result }), {
    status: 200,
    headers: { "Content-Type": "application/json; charset=utf-8", "Cache-Control": "no-store" }
  });
}

function mcpJsonRpcError(id, code, message) {
  return new Response(JSON.stringify({ jsonrpc: "2.0", id, error: { code, message } }), {
    status: 200,
    headers: { "Content-Type": "application/json; charset=utf-8", "Cache-Control": "no-store" }
  });
}

async function queryRecords(env, body, client) {
  const contractVersion = resolveContractVersion(body.contractVersion);
  const entities = sanitizeEntities(body.entities, contractVersion).filter(entity => canRead(client.role, entity));
  const since = body.since ? String(body.since) : null;
  const page = normalizeQueryPage(body);
  if (!entities.length) {
    return { ok: true, contractVersion, serverTime: new Date().toISOString(), records: [], nextCursor: null };
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
    contractVersion,
    serverTime: new Date().toISOString(),
    records: visibleRows.map(row => rowToRecord(row, contractVersion)),
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
  const contractVersion = resolveContractVersion(body.contractVersion);
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
    if (!entitiesForContract(contractVersion).includes(entity) || !id || !record.data || typeof record.data !== "object") {
      conflicts.push({ entity, id, reason: "invalid_record" });
      continue;
    }
    if (!canWrite(client.role, entity)) {
      conflicts.push({ entity, id, reason: "forbidden_entity_for_role", role: client.role });
      continue;
    }
    const validationError = validateRecordForUpsert(contractVersion, entity, id, record.data, record.deletedAt);
    if (validationError) {
      conflicts.push({ entity, id, reason: validationError });
      continue;
    }

    const existing = await env.DB.prepare(
      "SELECT entity, id, revision, device_id, created_at, updated_at, deleted_at, data_json FROM cloud_records WHERE entity = ? AND id = ?"
    ).bind(entity, id).first();

    if (
      existing
      && isPublishedTemplateRecord(entity, existing)
      && !record.deletedAt
      && !hasSameTemplateDefinition(existing, record.data)
    ) {
      conflicts.push({
        entity,
        id,
        reason: "immutable_template_requires_new_id",
        serverRecord: rowToRecord(existing, contractVersion),
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
        serverRecord: rowToRecord(existing, contractVersion),
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
    contractVersion,
    serverTime: new Date().toISOString(),
    accepted,
    conflicts
  };
}

function validateRecordForUpsert(contractVersion, entity, id, data, deletedAt) {
  if (data.contractVersion !== undefined && String(data.contractVersion) !== contractVersion) return "record_contract_version_mismatch";
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
  if (contractVersion === "2.0") return validateV2Record(entity, data);
  return null;
}

function resolveContractVersion(value) {
  if (value === undefined || value === null || value === "") return ACTIVE_CONTRACT_VERSION;
  const version = String(value);
  if (SUPPORTED_CONTRACT_VERSIONS.has(version)) return version;
  const error = new Error("unsupported_contract_version");
  error.status = 400;
  throw error;
}

function entitiesForContract(contractVersion) {
  return contractVersion === "2.0" ? V2_ENTITIES : V1_ENTITIES;
}

function validateV2Record(entity, data) {
  const requiredByEntity = {
    plan_templates: ["id", "title"],
    routine_templates: ["id", "title", "trainingType", "scene", "role", "lifecycle", "timerVisible", "calendarVisible", "countsTowardTraining", "steps"],
    daily_plan_items: ["id", "date", "trainingType", "title", "status"],
    plan_adjustments: ["id", "date", "reason", "toSnapshot"],
    timer_sessions: ["id", "date", "routineId", "routineVersion", "routineDigest", "startedAt", "completion", "calendarVisible", "countsTowardTraining", "activeSeconds", "elapsedSeconds", "pausedSeconds", "devicePlatform", "idempotencyKey"],
    training_logs: ["id", "date", "type", "status", "source", "calendarVisible", "countsTowardTraining"],
    body_metrics: ["id", "date", "observedAt", "context", "source"],
    status_checkins: ["id", "date", "kind", "observedAt"],
    daily_reviews: ["id", "date", "version", "status", "conclusion", "inputDigest", "provider", "model", "generatedAt"],
    plan_import_batches: ["id", "patchId", "patchSchema", "receivedAt", "status", "affectedEntityIds", "counts"],
    goal_sets: ["id", "version", "lifecycle", "effectiveFrom", "goals"],
    coach_strategies: ["id", "version", "lifecycle", "effectiveFrom", "boundaries"],
    planning_runs: ["id", "snapshotDigest", "status", "requestedAt", "period", "source"],
    coach_plan_patches: ["id", "runId", "status", "receivedAt", "snapshotDigest", "patch"]
  };
  const missing = (requiredByEntity[entity] || []).find(key => data[key] === undefined || data[key] === null || data[key] === "");
  if (missing) return `missing_v2_field:${missing}`;

  if (entity === "routine_templates") {
    if (!["home", "walk", "recovery", "travel"].includes(data.scene)) return "invalid_routine_scene";
    if (!["main", "warmup", "stretch", "cooldown", "recovery", "auxiliary"].includes(data.role)) return "invalid_routine_role";
    if (!["draft", "published", "archived"].includes(data.lifecycle)) return "invalid_template_lifecycle";
    if (!Array.isArray(data.steps) || data.steps.length === 0) return "invalid_routine_steps";
    if (![data.timerVisible, data.calendarVisible, data.countsTowardTraining].every(value => typeof value === "boolean")) return "invalid_routine_visibility";
    const invalidStep = data.steps.some(step => {
      if (!isObject(step) || !step.stepId || !isNumberInRange(step.durationSeconds, 0, 21600, false)) return true;
      if (step.execution === undefined) return false;
      if (!isObject(step.execution)) return true;
      return !["simple", "prepare_only", "alternating", "bilateral_hold", "bilateral_reps"].includes(step.execution.mode || "simple");
    });
    if (invalidStep) return "invalid_routine_step";
  }
  if (entity === "plan_templates") {
    if (data.status !== undefined && !["draft", "active", "archived"].includes(data.status)) return "invalid_plan_template_status";
    if (data.lifecycle !== undefined && !isPublishedLifecycle(data.lifecycle)) return "invalid_template_lifecycle";
  }
  if (entity === "daily_plan_items") {
    const types = ["strength", "easy_walk", "quality_walk", "indoor_cardio", "warmup", "cooldown", "recovery", "travel_strength", "seat_recovery", "stretch", "rest"];
    const statuses = ["planned", "completed", "short_version", "stretch_only", "skipped", "rested", "modified_by_user"];
    if (!types.includes(data.trainingType)) return "invalid_training_type";
    if (!statuses.includes(data.status)) return "invalid_completion_status";
  }
  if (entity === "plan_adjustments") {
    if (!isObject(data.toSnapshot) || typeof data.reason !== "string") return "invalid_plan_adjustment";
  }
  if (entity === "status_checkins") {
    if (!["morning", "pre_workout"].includes(data.kind)) return "invalid_checkin_kind";
    if (data.pain !== undefined && !Array.isArray(data.pain)) return "invalid_checkin_pain";
    if (!isIsoTimestamp(data.observedAt)) return "invalid_checkin_observed_at";
    if (["sleepDurationMinutes", "deepSleepMinutes"].some(key => data[key] !== undefined && !isNumberInRange(data[key], 0, 1440))) return "invalid_checkin_sleep";
    if (["sleepQuality", "energy", "fatigue", "workPressure"].some(key => data[key] !== undefined && !isNumberInRange(data[key], ["sleepQuality", "energy"].includes(key) ? 1 : 0, 5))) return "invalid_checkin_scale";
    if (Array.isArray(data.pain) && data.pain.some(item => !isValidPain(item))) return "invalid_checkin_pain";
  }
  if (entity === "body_metrics") {
    if (!["morning", "other"].includes(data.context)) return "invalid_metric_context";
    if (!["manual", "health_connect", "xiaomi_import", "scale_import", "legacy"].includes(data.source)) return "invalid_metric_source";
    if (!isIsoTimestamp(data.observedAt)) return "invalid_metric_observed_at";
    if (["weightKg", "waistCm", "bodyFatPct", "muscleKg"].some(key => data[key] !== undefined && !isNumberInRange(data[key], 0, key === "bodyFatPct" ? 100 : 500))) return "invalid_metric_value";
  }
  if (entity === "timer_sessions") {
    if (String(data.idempotencyKey).length > 160) return "invalid_idempotency_key";
    if (!["web", "android"].includes(data.devicePlatform)) return "invalid_timer_device_platform";
    if (![data.calendarVisible, data.countsTowardTraining].every(value => typeof value === "boolean")) return "invalid_timer_visibility";
    if (!isIsoTimestamp(data.startedAt) || (data.endedAt && !isIsoTimestamp(data.endedAt))) return "invalid_timer_timestamp";
    if (!["activeSeconds", "elapsedSeconds", "pausedSeconds"].every(key => isNumberInRange(data[key], 0, Number.MAX_SAFE_INTEGER))) return "invalid_timer_duration";
  }
  if (entity === "training_logs") {
    if (![data.calendarVisible, data.countsTowardTraining].every(value => typeof value === "boolean")) return "invalid_training_visibility";
    if (!["manual", "timer", "native_timer", "health_connect", "xiaomi_import", "wearable", "screenshot", "import", "coach_adjusted", "legacy"].includes(data.source)) return "invalid_training_source";
  }
  if (entity === "daily_reviews") {
    if (!["generated", "invalidated", "failed"].includes(data.status)) return "invalid_review_status";
    if (!isNumberInRange(data.version, 1, Number.MAX_SAFE_INTEGER) || !isIsoTimestamp(data.generatedAt)) return "invalid_review_metadata";
  }
  if (entity === "plan_import_batches") {
    if (data.patchSchema !== "coach_plan_patch" || !["previewed", "applied", "undone", "rejected"].includes(data.status)) return "invalid_plan_import_batch";
    if (!Array.isArray(data.affectedEntityIds) || !isObject(data.counts)) return "invalid_plan_import_batch";
    if (!["added", "updated", "deleted"].every(key => isNumberInRange(data.counts[key], 0, Number.MAX_SAFE_INTEGER))) return "invalid_plan_import_counts";
  }
  if (entity === "goal_sets") {
    if (!isPublishedLifecycle(data.lifecycle) || !Array.isArray(data.goals) || data.goals.length === 0 || !isIsoDate(data.effectiveFrom)) return "invalid_goal_set";
  }
  if (entity === "coach_strategies") {
    if (!isPublishedLifecycle(data.lifecycle) || !isObject(data.boundaries) || !isIsoDate(data.effectiveFrom)) return "invalid_coach_strategy";
  }
  if (entity === "planning_runs") {
    if (data.source !== "chatgpt_mcp" || !["submitted", "failed", "invalidated"].includes(data.status)) return "invalid_planning_run";
    if (!isIsoTimestamp(data.requestedAt) || !isValidPlanningPeriod(data.period)) return "invalid_planning_run";
  }
  if (entity === "coach_plan_patches") {
    if (!["pending", "confirmed", "applied", "rejected", "invalidated"].includes(data.status)) return "invalid_coach_plan_patch_status";
    if (!isIsoTimestamp(data.receivedAt) || !isObject(data.patch)) return "invalid_coach_plan_patch_record";
  }
  return null;
}

function isObject(value) {
  return Boolean(value && typeof value === "object" && !Array.isArray(value));
}

function isIsoTimestamp(value) {
  const text = String(value || "");
  return text.length >= 10 && !Number.isNaN(Date.parse(text));
}

function isNumberInRange(value, minimum, maximum, allowMinimum = true) {
  const number = Number(value);
  return Number.isFinite(number) && (allowMinimum ? number >= minimum : number > minimum) && number <= maximum;
}

function isValidPain(value) {
  if (!isObject(value)) return false;
  const regions = ["neck_shoulder", "wrist", "lower_back", "hip_glute", "thigh_knee", "calf_ankle", "other"];
  const sides = [undefined, "left", "right", "bilateral", "unspecified"];
  return regions.includes(value.region) && sides.includes(value.side) && isNumberInRange(value.severity, 0, 5);
}

function isPublishedLifecycle(value) {
  return ["draft", "published", "archived"].includes(value);
}

function hasSameStoredPayload(existing, data, deletedAt) {
  return String(existing.deleted_at || "") === String(deletedAt || "")
    && String(existing.data_json || "") === JSON.stringify(data);
}

const TEMPLATE_MANAGEMENT_FIELDS = new Set([
  "lifecycle",
  "status",
  "timerVisible",
  "timer_visible",
  "needsTimer",
  "needs_timer",
  "calendarVisible",
  "calendar_visible",
  "countsTowardTraining",
  "counts_toward_training",
  "archivedAt",
  "archived_at",
  "updatedAt",
  "updated_at",
  "deletedAt",
  "deleted_at"
]);

function hasSameTemplateDefinition(existing, data) {
  return JSON.stringify(templateDefinitionPayload(safeJson(existing.data_json, {})))
    === JSON.stringify(templateDefinitionPayload(data));
}

function templateDefinitionPayload(value) {
  const copy = { ...((value && typeof value === "object") ? value : {}) };
  TEMPLATE_MANAGEMENT_FIELDS.forEach((field) => delete copy[field]);
  return copy;
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

function rowToRecord(row, contractVersion = ACTIVE_CONTRACT_VERSION) {
  return {
    contractVersion,
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

function sanitizeEntities(value, contractVersion = ACTIVE_CONTRACT_VERSION) {
  const allowed = entitiesForContract(contractVersion);
  if (!Array.isArray(value) || !value.length) return allowed;
  return [...new Set(value.map(String).filter(entity => allowed.includes(entity)))];
}

function assertAiReviewRole(client) {
  if (["admin", "shenk"].includes(client.role)) return;
  const error = new Error("forbidden_ai_review_role");
  error.status = 403;
  throw error;
}

function validateAiProvider(value) {
  const provider = value?.provider || {};
  const id = String(provider.id || "").trim();
  const model = String(provider.model || "").trim();
  const apiKey = String(provider.apiKey || "").trim();
  let baseUrl;
  try {
    baseUrl = new URL(String(provider.baseUrl || "").trim());
  } catch {
    const error = new Error("invalid_ai_provider_url");
    error.status = 400;
    throw error;
  }
  if (baseUrl.protocol !== "https:" || baseUrl.username || baseUrl.password || !id || !model || !apiKey) {
    const error = new Error("invalid_ai_provider_configuration");
    error.status = 400;
    throw error;
  }
  const host = baseUrl.hostname.toLowerCase();
  if (
    host === "localhost" || host.endsWith(".localhost") || host.endsWith(".local") ||
    host === "0.0.0.0" || host === "127.0.0.1" || host === "::1" ||
    /^10\./.test(host) || /^192\.168\./.test(host) || /^169\.254\./.test(host) ||
    /^172\.(1[6-9]|2\d|3[01])\./.test(host)
  ) {
    const error = new Error("private_ai_provider_url_forbidden");
    error.status = 400;
    throw error;
  }
  return { id, model, apiKey, baseUrl: baseUrl.toString().replace(/\/$/, "") };
}

async function testAiProviderConnection(body) {
  const provider = validateAiProvider(body);
  await callCompatibleAi(provider, [
    { role: "system", content: "Reply with exactly OK." },
    { role: "user", content: "connection test" }
  ], 32);
  return { ok: true, provider: provider.id, model: provider.model };
}

async function generateDailyReview(body) {
  const provider = validateAiProvider(body);
  const snapshot = body?.snapshot;
  if (!snapshot || snapshot.schema !== "daily_review_snapshot" || !snapshot.date || !Array.isArray(snapshot.records)) {
    const error = new Error("invalid_daily_review_snapshot");
    error.status = 400;
    throw error;
  }
  const system = [
    "你是身刻的专业、克制、实事求是的每日运动复盘教练。",
    "你正在复盘 snapshot.date 这一天已经发生的执行结果，而不是在训练前指导这一天应该怎么做。即使复盘日期是今天，也必须使用事后评价语义。",
    "综合考虑身体测量、睡眠时长与深睡、睡眠感受、精力、疲劳、疼痛、最近训练、目标、教练策略和当前有效正式计划。",
    "缺失值保持缺失，不得推断为正常、休息或已完成；不得冒充医疗诊断。",
    "不得创建、修改或删除正式计划、计划调整、目标、教练策略或训练方案。",
    "可以生成 localSuggestion，但它只是身刻本地建议，只能在当天没有有效正式计划时提供；有正式计划时必须返回 null。",
    "localSuggestion 不得伪装成正式计划，且只能建议当天，不得安排未来日期。",
    "重点评价当天完成得怎么样、计划与实际是否匹配、存在什么问题及可能原因，并给出接下来或下一次如何修正；不要复述输入中的状态、测量和训练流水。",
    "conclusion 是完整、可独立阅读的事后评价结论，不超过 50 个汉字；只概括当天表现和最重要的问题，不写尚未执行的当日指令。assessment 是复盘分析，不超过 300 个汉字。",
    "actions 为 1 至 3 条后续修正措施，面向接下来、下次训练或有依据时的次日；不得把 reviewed date 上尚未发生的动作写成仍需完成的任务。evidence 只保留 1 至 4 条关键依据；cautions 只写真实风险，没有则为空数组。",
    "只输出 JSON，不要 Markdown：{\"conclusion\":\"...\",\"assessment\":\"...\",\"actions\":[\"...\"],\"evidence\":[\"...\"],\"cautions\":[\"...\"],\"localSuggestion\":null}。",
    "localSuggestion 非空时格式为：{\"date\":\"YYYY-MM-DD\",\"title\":\"...\",\"trainingType\":\"...\",\"estimatedMinutes\":30,\"reason\":\"...\"}。"
  ].join("\n");
  const content = await callCompatibleAi(provider, [
    { role: "system", content: system },
    { role: "user", content: JSON.stringify(snapshot) }
  ], 1400, { thinkingEnabled: true });
  const review = parseDailyReviewContent(content, snapshot.date);
  if (snapshotHasFormalPlan(snapshot)) review.localSuggestion = null;
  return { ok: true, review };
}

function snapshotHasFormalPlan(snapshot) {
  return snapshot.records.some(record => {
    if (!record || record.deletedAt) return false;
    if (record.entity !== "daily_plan_items" && record.entity !== "plan_adjustments") return false;
    const data = record.data && typeof record.data === "object" ? record.data : record;
    return data.date === snapshot.date && data.status !== "cancelled" && data.status !== "deleted";
  });
}

async function callCompatibleAi(provider, messages, maxTokens, options = {}) {
  let response;
  try {
    response = await fetch(`${provider.baseUrl}/chat/completions`, {
      method: "POST",
      headers: {
        "Authorization": `Bearer ${provider.apiKey}`,
        "Content-Type": "application/json"
      },
      body: JSON.stringify({
        model: provider.model,
        messages,
        max_tokens: maxTokens,
        temperature: 0.2,
        thinking: { type: options.thinkingEnabled ? "enabled" : "disabled" }
      })
    });
  } catch {
    const error = new Error("ai_provider_unreachable");
    error.status = 503;
    throw error;
  }
  if (!response.ok) {
    const error = new Error(`ai_provider_http_${response.status}`);
    error.status = response.status === 401 || response.status === 403 ? 502 : 503;
    throw error;
  }
  const payload = await response.json().catch(() => null);
  const content = payload?.choices?.[0]?.message?.content;
  if (typeof content !== "string" || !content.trim()) {
    const error = new Error("ai_provider_response_invalid");
    error.status = 502;
    throw error;
  }
  return content.trim();
}

function parseDailyReviewContent(content, expectedDate) {
  const clean = content.replace(/^```(?:json)?\s*/i, "").replace(/\s*```$/, "").trim();
  const value = safeJson(clean, null);
  if (!value || typeof value.conclusion !== "string" || !value.conclusion.trim()) {
    const error = new Error("ai_provider_review_invalid");
    error.status = 502;
    throw error;
  }
  const actions = sanitizeReviewLines(value.actions, 4);
  if (!actions.length) {
    const error = new Error("ai_provider_review_actions_missing");
    error.status = 502;
    throw error;
  }
  return {
    conclusion: value.conclusion.trim().slice(0, 120),
    assessment: typeof value.assessment === "string" ? value.assessment.trim().slice(0, 600) : "",
    actions,
    evidence: sanitizeReviewLines(value.evidence, 4),
    cautions: sanitizeReviewLines(value.cautions, 3),
    localSuggestion: sanitizeLocalSuggestion(value.localSuggestion, expectedDate)
  };
}

function sanitizeLocalSuggestion(value, expectedDate) {
  if (!value || typeof value !== "object" || Array.isArray(value)) return null;
  const date = typeof value.date === "string" ? value.date.trim() : "";
  const title = typeof value.title === "string" ? value.title.trim() : "";
  const trainingType = typeof value.trainingType === "string" ? value.trainingType.trim() : "";
  if (!expectedDate || date !== expectedDate || !title || !trainingType) return null;
  const minutes = Number(value.estimatedMinutes);
  return {
    date,
    title: title.slice(0, 80),
    trainingType: trainingType.slice(0, 64),
    estimatedMinutes: Number.isFinite(minutes) ? Math.max(0, Math.min(180, Math.round(minutes))) : null,
    reason: typeof value.reason === "string" ? value.reason.trim().slice(0, 300) : ""
  };
}

function sanitizeReviewLines(value, limit = 8) {
  return (Array.isArray(value) ? value : [])
    .filter(item => typeof item === "string" && item.trim())
    .slice(0, limit)
    .map(item => item.trim().slice(0, 300));
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

function oauthTokenResponse(payload, status, request, env) {
  return new Response(JSON.stringify(payload), {
    status,
    headers: {
      ...corsHeaders(request, env),
      "Content-Type": "application/json; charset=utf-8",
      "Cache-Control": "no-store",
      "Pragma": "no-cache"
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
