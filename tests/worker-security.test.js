"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");

function loadWorker() {
  const workerPath = path.join(__dirname, "..", "cloudflare", "worker.js");
  const source = fs.readFileSync(workerPath, "utf8")
    .replace("export default {", "globalThis.__worker = {");
  const context = {
    URL,
    Request,
    Response,
    Headers,
    TextEncoder,
    crypto: { randomUUID: () => "event_test" },
    globalThis: null
  };
  context.globalThis = context;
  vm.createContext(context);
  vm.runInContext(source, context, { filename: workerPath });
  return context.__worker;
}

function request(url, options = {}) {
  return new Request(url, options);
}

async function run() {
{
  const worker = loadWorker();
  const response = await worker.fetch(
    request("https://worker.example/api/sync-profiles/private_profile"),
    { SHENK_TOKEN: "valid" }
  );
  assert.equal(response.status, 401);
  assert.equal((await response.json()).error, "unauthorized");
}

{
  const worker = loadWorker();
  const response = await worker.fetch(
    request("https://worker.example/api/records/query", {
      method: "POST",
      headers: { Authorization: "Bearer valid", "Content-Type": "application/json" },
      body: JSON.stringify({ contractVersion: "1.0", entities: ["routine_templates"], cursor: "%" })
    }),
    { SHENK_TOKEN: "valid" }
  );
  assert.equal(response.status, 400);
  assert.equal((await response.json()).error, "invalid_query_cursor");
}

{
  const worker = loadWorker();
  const existing = {
    entity: "routine_templates",
    id: "routine_published",
    revision: 1,
    device_id: "coach",
    created_at: "2099-01-01T00:00:00.000Z",
    updated_at: "2099-01-01T00:00:00.000Z",
    deleted_at: null,
    data_json: JSON.stringify({ id: "routine_published", lifecycle: "published", title: "Original", steps: [] })
  };
  const response = await worker.fetch(
    request("https://worker.example/api/records/upsert", {
      method: "POST",
      headers: { Authorization: "Bearer valid", "Content-Type": "application/json" },
      body: JSON.stringify({
        contractVersion: "1.0",
        records: [{
          entity: "routine_templates",
          id: "routine_published",
          baseRevision: 1,
          data: { id: "routine_published", lifecycle: "published", title: "Changed", steps: [] }
        }]
      })
    }),
    {
      SHENK_TOKEN: "valid",
      DB: { prepare() { return { bind() { return { first: async () => existing }; } }; } }
    }
  );
  const body = await response.json();
  assert.equal(body.conflicts[0].reason, "immutable_template_requires_new_id");
}

{
  const worker = loadWorker();
  const response = await worker.fetch(
    request("https://worker.example/api/records/query", {
      method: "POST",
      headers: { Authorization: "Bearer valid", "Content-Type": "application/json" },
      body: JSON.stringify({ contractVersion: "9.9", entities: ["routine_templates"] })
    }),
    { SHENK_TOKEN: "valid" }
  );
  assert.equal(response.status, 400);
  assert.equal((await response.json()).error, "unsupported_contract_version");
}

{
  const worker = loadWorker();
  const response = await worker.fetch(
    request("https://worker.example/api/records/upsert", {
      method: "POST",
      headers: { Authorization: "Bearer valid", "Content-Type": "application/json" },
      body: JSON.stringify({
        records: [{
          entity: "timer_sessions",
          id: "session_one",
          data: { id: "other_id", date: "2099-01-01", completion: "completed", actualSeconds: 30 }
        }]
      })
    }),
    {
      TIMER_TOKEN: "valid",
      DB: { prepare() { throw new Error("invalid records must not query D1"); } }
    }
  );
  const body = await response.json();
  assert.equal(response.status, 200);
  assert.equal(body.conflicts[0].reason, "record_id_mismatch");
}

{
  const worker = loadWorker();
  const existing = {
    entity: "training_logs",
    id: "log_existing",
    revision: 4,
    device_id: "desktop",
    created_at: "2099-01-01T00:00:00.000Z",
    updated_at: "2099-01-01T00:00:00.000Z",
    deleted_at: null,
    data_json: JSON.stringify({ id: "log_existing", date: "2099-01-01", type: "strength" })
  };
  const response = await worker.fetch(
    request("https://worker.example/api/records/upsert", {
      method: "POST",
      headers: { Authorization: "Bearer valid", "Content-Type": "application/json" },
      body: JSON.stringify({
        records: [{
          entity: "training_logs",
          id: "log_existing",
          baseRevision: 0,
          data: { id: "log_existing", date: "2099-01-01", type: "easy_walk" }
        }]
      })
    }),
    {
      SHENK_TOKEN: "valid",
      DB: {
        prepare() {
          return { bind() { return { first: async () => existing }; } };
        }
      }
    }
  );
  const body = await response.json();
  assert.equal(body.conflicts[0].reason, "server_revision_mismatch");
  assert.equal(body.conflicts[0].serverRecord.revision, 4);
}

{
  const worker = loadWorker();
  const response = await worker.fetch(
    request("https://worker.example/api/records/upsert", {
      method: "POST",
      headers: { Authorization: "Bearer valid", "Content-Type": "application/json" },
      body: JSON.stringify({ records: Array.from({ length: 101 }, (_, index) => ({
        entity: "body_metrics",
        id: `metric_${index}`,
        data: { id: `metric_${index}`, date: "2099-01-01" }
      })) })
    }),
    { SHENK_TOKEN: "valid" }
  );
  assert.equal(response.status, 413);
  assert.equal((await response.json()).error, "too_many_records");
}

console.log("worker security tests passed");
}

run().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
