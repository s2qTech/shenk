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
