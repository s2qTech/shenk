"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");
const test = require("node:test");
const nodeCrypto = require("node:crypto");

function loadWorkerContext() {
  const workerPath = path.join(__dirname, "..", "cloudflare", "worker.js");
  const source = fs.readFileSync(workerPath, "utf8").replace("export default {", "globalThis.__worker = {");
  const webCrypto = nodeCrypto.webcrypto;
  const context = {
    URL,
    URLSearchParams,
    Request,
    Response,
    Headers,
    TextEncoder,
    Intl,
    crypto: {
      randomUUID: nodeCrypto.randomUUID,
      subtle: webCrypto.subtle,
      getRandomValues: webCrypto.getRandomValues.bind(webCrypto)
    },
    globalThis: null
  };
  context.globalThis = context;
  vm.createContext(context);
  vm.runInContext(source, context, { filename: workerPath });
  return context;
}

function tokenDb() {
  return {
    prepare(sql) {
      assert.match(sql, /mcp_oauth_tokens/);
      return {
        bind() {
          return {
            first: async () => ({
              client_id: "fixture_client",
              scope: "planning:read planning:draft",
              resource: "https://worker.example/mcp",
              access_expires_at: "2099-01-01T00:00:00.000Z",
              revoked_at: null
            })
          };
        }
      };
    }
  };
}

test("OAuth discovery advertises the MCP resource and PKCE", async () => {
  const { __worker: worker } = loadWorkerContext();
  const resourceResponse = await worker.fetch(new Request("https://worker.example/.well-known/oauth-protected-resource/mcp"), {});
  const resource = await resourceResponse.json();
  assert.equal(resource.resource, "https://worker.example/mcp");
  assert.deepEqual(resource.scopes_supported, ["planning:read", "planning:draft"]);

  const serverResponse = await worker.fetch(new Request("https://worker.example/.well-known/oauth-authorization-server"), {});
  const server = await serverResponse.json();
  assert.deepEqual(server.code_challenge_methods_supported, ["S256"]);
  assert.equal(server.registration_endpoint, "https://worker.example/oauth/register");
});

test("MCP rejects unauthenticated requests with protected resource metadata", async () => {
  const { __worker: worker } = loadWorkerContext();
  const response = await worker.fetch(new Request("https://worker.example/mcp", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ jsonrpc: "2.0", id: 1, method: "tools/list" })
  }), { DB: { prepare() { throw new Error("must not query without a token"); } } });
  assert.equal(response.status, 401);
  assert.match(response.headers.get("WWW-Authenticate"), /oauth-protected-resource\/mcp/);
});

test("MCP lists only the bounded planning tools", async () => {
  const { __worker: worker } = loadWorkerContext();
  const response = await worker.fetch(new Request("https://worker.example/mcp", {
    method: "POST",
    headers: { Authorization: "Bearer fixture", "Content-Type": "application/json" },
    body: JSON.stringify({ jsonrpc: "2.0", id: 1, method: "tools/list" })
  }), { DB: tokenDb() });
  const body = await response.json();
  assert.deepEqual(body.result.tools.map(tool => tool.name), ["get_planning_snapshot", "submit_coach_plan_patch"]);
  assert.equal(body.result.tools[0].annotations.readOnlyHint, true);
  assert.equal(body.result.tools[1].annotations.destructiveHint, false);
});

test("MCP role cannot write formal planning or health entities", () => {
  const context = loadWorkerContext();
  assert.equal(context.canWrite("mcp", "planning_runs"), true);
  assert.equal(context.canWrite("mcp", "coach_plan_patches"), true);
  for (const entity of ["daily_plan_items", "plan_adjustments", "routine_templates", "training_logs", "body_metrics", "timer_sessions"]) {
    assert.equal(context.canWrite("mcp", entity), false, `${entity} must remain read-only to MCP`);
  }
});

test("coach patch draft validation is merge-only, non-destructive, and non-empty", () => {
  const context = loadWorkerContext();
  const period = { historyFrom: "2098-12-19", historyTo: "2099-01-01", planThrough: "2099-01-15" };
  const valid = {
    schema: "coach_plan_patch",
    contractVersion: "2.0",
    effectiveFrom: "2099-01-02",
    planAdjustments: [{
      id: "adjustment_fixture_2099-01-02",
      date: "2099-01-02",
      reason: "Synthetic fixture",
      toSnapshot: { trainingType: "recovery", title: "Synthetic recovery" }
    }]
  };
  assert.equal(context.validateCoachPlanPatchDraft(valid, period), null);
  assert.equal(context.validateCoachPlanPatchDraft({ ...valid, replaceMode: true }, period), "replace_mode_not_allowed");
  assert.equal(context.validateCoachPlanPatchDraft({ ...valid, planAdjustments: [{ ...valid.planAdjustments[0], operation: "delete" }] }, period), "delete_not_allowed");
  assert.equal(context.validateCoachPlanPatchDraft({ schema: "coach_plan_patch", contractVersion: "2.0", effectiveFrom: "2099-01-02", dailyPlanItems: [] }, period), "empty_patch");
});

test("planning snapshot removes credential-shaped and raw fields", async () => {
  const context = loadWorkerContext();
  const rows = [{
    entity: "body_metrics",
    id: "metric_fixture",
    revision: 1,
    updated_at: new Date().toISOString(),
    deleted_at: null,
    data_json: JSON.stringify({
      id: "metric_fixture",
      date: context.dateInTimeZone(new Date(), "Asia/Shanghai"),
      weightKg: 100,
      providerToken: "must-not-leave-server",
      rawSource: { private: true }
    })
  }];
  const DB = {
    prepare(sql) {
      assert.match(sql, /FROM cloud_records/);
      return { bind() { return { all: async () => ({ results: rows }) }; } };
    }
  };
  const snapshot = await context.buildPlanningSnapshot(DB ? { DB } : {}, {}, { scopes: ["planning:read"] });
  const metric = snapshot.records.body_metrics[0].data;
  assert.equal(metric.weightKg, 100);
  assert.equal(metric.providerToken, undefined);
  assert.equal(metric.rawSource, undefined);
  assert.match(snapshot.snapshotDigest, /^sha256:[a-f0-9]{64}$/);
});

test("pairing endpoint returns a raw code once while writing only its hash", async () => {
  const context = loadWorkerContext();
  const writes = [];
  const DB = {
    prepare(sql) {
      return { bind(...args) { writes.push({ sql, args }); return { run: async () => ({}) }; } };
    }
  };
  const response = await context.__worker.fetch(new Request("https://worker.example/api/mcp/pairing-code", {
    method: "POST",
    headers: { Authorization: "Bearer fixture-shenk" }
  }), { SHENK_TOKEN: "fixture-shenk", DB });
  const body = await response.json();
  assert.equal(response.status, 201);
  assert.match(body.pairingCode, /^[A-Z2-9]{5}(?:-[A-Z2-9]{5}){3}$/);
  assert.equal(writes.length, 1);
  assert.notEqual(writes[0].args[0], body.pairingCode.replaceAll("-", ""));
  assert.match(writes[0].args[0], /^[a-f0-9]{64}$/);
});

function oauthAuthorizationRequest() {
  const form = new URLSearchParams({
    response_type: "code",
    client_id: "fixture_client",
    redirect_uri: "https://chatgpt.com/aip/callback",
    state: "fixture_state",
    code_challenge: "A".repeat(43),
    code_challenge_method: "S256",
    scope: "planning:read planning:draft",
    resource: "https://worker.example/mcp",
    pairing_code: "ABCDE-FGHJK-LMNPQ-RSTUV"
  });
  return new Request("https://worker.example/oauth/authorize", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: form
  });
}

test("invalid pairing code stays on a readable retry page", async () => {
  const { __worker: worker } = loadWorkerContext();
  const DB = {
    prepare(sql) {
      if (sql.includes("mcp_oauth_clients")) {
        return { bind() { return { first: async () => ({ client_id: "fixture_client", redirect_uris_json: '["https://chatgpt.com/aip/callback"]' }) }; } };
      }
      if (sql.includes("SELECT code_hash") && sql.includes("mcp_pairing_codes")) {
        return { bind() { return { first: async () => null }; } };
      }
      throw new Error(`unexpected SQL: ${sql}`);
    }
  };
  const response = await worker.fetch(oauthAuthorizationRequest(), { DB });
  const body = await response.text();
  assert.equal(response.status, 401);
  assert.match(response.headers.get("Content-Type"), /text\/html/);
  assert.match(body, /配对码无效、已使用或已经过期/);
  assert.match(body, /name="state" value="fixture_state"/);
  assert.doesNotMatch(body, /invalid_or_expired_pairing_code/);
});

test("authorization page loads a same-origin submit guard", async () => {
  const context = loadWorkerContext();
  const DB = {
    prepare(sql) {
      assert.match(sql, /mcp_oauth_clients/);
      return { bind() { return { first: async () => ({ client_id: "fixture_client", redirect_uris_json: '["https://chatgpt.com/aip/callback"]' }) }; } };
    }
  };
  const url = new URL("https://worker.example/oauth/authorize");
  for (const [name, value] of new URLSearchParams({
    response_type: "code",
    client_id: "fixture_client",
    redirect_uri: "https://chatgpt.com/aip/callback",
    state: "fixture_state",
    code_challenge: "A".repeat(43),
    code_challenge_method: "S256",
    scope: "planning:read planning:draft",
    resource: "https://worker.example/mcp"
  })) url.searchParams.set(name, value);
  const pageResponse = await context.__worker.fetch(new Request(url), { DB });
  const page = await pageResponse.text();
  assert.equal(pageResponse.status, 200);
  assert.match(page, /id="oauth-authorization-form"/);
  assert.match(page, /src="\/oauth\/authorize-ui\.js"/);
  assert.match(pageResponse.headers.get("Content-Security-Policy"), /script-src 'self'/);
  assert.match(pageResponse.headers.get("Content-Security-Policy"), /form-action 'self' https:\/\/chatgpt\.com/);

  const scriptResponse = await context.__worker.fetch(new Request("https://worker.example/oauth/authorize-ui.js"), {});
  const script = await scriptResponse.text();
  assert.equal(scriptResponse.status, 200);
  assert.match(scriptResponse.headers.get("Content-Type"), /application\/javascript/);
  assert.match(script, /event\.preventDefault\(\)/);
  assert.match(script, /submit\.disabled = true/);
});

test("a valid pairing approval uses a See Other redirect to the registered callback", async () => {
  const context = loadWorkerContext();
  const pairingHash = await context.sha256Hex("ABCDEFGHJKLMNPQRSTUV");
  const DB = {
    prepare(sql) {
      if (sql.includes("mcp_oauth_clients")) {
        return { bind() { return { first: async () => ({ client_id: "fixture_client", redirect_uris_json: '["https://chatgpt.com/aip/callback"]' }) }; } };
      }
      if (sql.includes("SELECT code_hash") && sql.includes("mcp_pairing_codes")) {
        return { bind(hash) { assert.equal(hash, pairingHash); return { first: async () => ({ code_hash: pairingHash, expires_at: "2099-01-01T00:00:00.000Z", used_at: null }) }; } };
      }
      if (sql.includes("UPDATE mcp_pairing_codes")) {
        return { bind() { return { run: async () => ({ meta: { changes: 1 } }) }; } };
      }
      if (sql.includes("INSERT INTO mcp_oauth_authorization_codes")) {
        return { bind() { return { run: async () => ({ meta: { changes: 1 } }) }; } };
      }
      throw new Error(`unexpected SQL: ${sql}`);
    }
  };
  const response = await context.__worker.fetch(oauthAuthorizationRequest(), { DB });
  assert.equal(response.status, 303);
  const redirect = new URL(response.headers.get("Location"));
  assert.equal(redirect.origin + redirect.pathname, "https://chatgpt.com/aip/callback");
  assert.equal(redirect.searchParams.get("state"), "fixture_state");
  assert.ok(redirect.searchParams.get("code"));
});

test("OAuth token errors use the standard error response shape", async () => {
  const { __worker: worker } = loadWorkerContext();
  const response = await worker.fetch(new Request("https://worker.example/oauth/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({ grant_type: "authorization_code" })
  }), { DB: { prepare() { throw new Error("must not query invalid token requests"); } } });
  assert.equal(response.status, 400);
  assert.deepEqual(await response.json(), { error: "invalid_request" });
  assert.equal(response.headers.get("Cache-Control"), "no-store");
  assert.equal(response.headers.get("Pragma"), "no-cache");
});

test("a pairing code rejected by the conditional consume cannot issue an authorization code", async () => {
  const { __worker: worker } = loadWorkerContext();
  let authorizationCodeInserted = false;
  const DB = {
    prepare(sql) {
      if (sql.includes("mcp_oauth_clients")) {
        return { bind() { return { first: async () => ({ client_id: "fixture_client", redirect_uris_json: '["https://chatgpt.com/aip/callback"]' }) }; } };
      }
      if (sql.includes("SELECT code_hash") && sql.includes("mcp_pairing_codes")) {
        return { bind() { return { first: async () => ({ code_hash: "fixture", expires_at: "2099-01-01T00:00:00.000Z", used_at: null }) }; } };
      }
      if (sql.includes("UPDATE mcp_pairing_codes")) {
        return { bind() { return { run: async () => ({ meta: { changes: 0 } }) }; } };
      }
      if (sql.includes("mcp_oauth_authorization_codes")) {
        authorizationCodeInserted = true;
      }
      throw new Error(`unexpected SQL: ${sql}`);
    }
  };
  const response = await worker.fetch(oauthAuthorizationRequest(), { DB });
  assert.equal(response.status, 401);
  assert.equal(authorizationCodeInserted, false);
  assert.match(await response.text(), /配对码已被使用或已经过期/);
});
