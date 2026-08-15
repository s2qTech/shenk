"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");
const nodeCrypto = require("node:crypto");

function loadWorker(fetchImpl = fetch, runtime = {}) {
  const workerPath = path.join(__dirname, "..", "cloudflare", "worker.js");
  const source = fs.readFileSync(workerPath, "utf8")
    .replace('import { WorkflowEntrypoint } from "cloudflare:workers";', 'class WorkflowEntrypoint { constructor(_ctx, env) { this.env = env; } }')
    .replace("export default {", "globalThis.__worker = {")
    .replace("export class DailyReviewWorkflow", "globalThis.__DailyReviewWorkflow = class DailyReviewWorkflow");
  const context = {
    URL,
    Request,
    Response,
    Headers,
    fetch: fetchImpl,
    AbortController,
    setTimeout: runtime.setTimeout || setTimeout,
    clearTimeout: runtime.clearTimeout || clearTimeout,
    TextEncoder,
    TextDecoder,
    btoa,
    atob,
    crypto: {
      randomUUID: () => "event_test",
      getRandomValues: value => nodeCrypto.webcrypto.getRandomValues(value),
      subtle: nodeCrypto.webcrypto.subtle
    },
    globalThis: null
  };
  context.globalThis = context;
  vm.createContext(context);
  vm.runInContext(source, context, { filename: workerPath });
  context.__worker.DailyReviewWorkflow = context.__DailyReviewWorkflow;
  return context.__worker;
}

function request(url, options = {}) {
  return new Request(url, options);
}

const TEST_DIGEST = `sha256:${"a".repeat(64)}`;
const dailyJobFields = date => ({ jobId: `daily-review:${date}:fixture`, inputDigest: TEST_DIGEST });

function aiJobDb() {
  const rows = new Map();
  return {
    prepare(sql) {
      return {
        bind(...args) {
          return {
            async first() {
              if (/SELECT \* FROM ai_daily_review_jobs/.test(sql)) return rows.get(args[0]) || null;
              throw new Error(`unexpected first: ${sql}`);
            },
            async run() {
              if (/INSERT OR IGNORE INTO ai_daily_review_jobs/.test(sql)) {
                if (rows.has(args[0])) return { meta: { changes: 0 } };
                rows.set(args[0], { job_id: args[0], input_digest: args[1], state: "RUNNING", review_json: null, usage_json: null, finish_reason: null, upstream_requests: 0, error_code: null, created_at: args[2], updated_at: args[3] });
                return { meta: { changes: 1 } };
              }
              const jobId = args.at(-1);
              const row = rows.get(jobId);
              if (!row) return { meta: { changes: 0 } };
              if (/SET state = 'RUNNING'/.test(sql)) Object.assign(row, { state: "RUNNING", review_json: null, usage_json: null, finish_reason: null, upstream_requests: 0, error_code: null, updated_at: args[0] });
              else if (/SET state = 'SUCCEEDED'/.test(sql)) Object.assign(row, { state: "SUCCEEDED", review_json: args[0], usage_json: args[1], finish_reason: args[2], upstream_requests: args[3], error_code: null, updated_at: args[4] });
              else if (/SET state = 'FAILED'/.test(sql)) Object.assign(row, { state: "FAILED", usage_json: args[0], finish_reason: args[1], upstream_requests: args[2], error_code: args[3], updated_at: args[4] });
              rows.set(jobId, row);
              return { meta: { changes: 1 } };
            }
          };
        }
      };
    }
  };
}

function dailyReviewWorkflowEnv(worker, db) {
  const pending = [];
  const env = {
    SHENK_TOKEN: "valid",
    DB: db,
    AI_JOB_ENCRYPTION_KEY: "fixture-encryption-key-with-at-least-32-characters",
    DAILY_REVIEW_WORKFLOW: {
      async create({ id, params }) {
        env.lastWorkflowParams = params;
        const workflow = Object.create(worker.DailyReviewWorkflow.prototype);
        workflow.env = env;
        const execution = Promise.resolve().then(() => workflow.run(
          { payload: params },
          { do: async (_name, ...args) => args.at(-1)() }
        ));
        pending.push(execution);
        return { id };
      }
    },
    async finishWorkflows() {
      await Promise.all(pending);
    }
  };
  return env;
}

async function readDailyReviewStatus(worker, env, jobId) {
  await env.finishWorkflows();
  const response = await worker.fetch(
    request(`https://worker.example/api/ai/daily-review-jobs/${encodeURIComponent(jobId)}`, {
      headers: { Authorization: "Bearer valid" }
    }),
    env
  );
  return { response, body: await response.json() };
}

async function run() {
{
  const worker = loadWorker();
  const response = await worker.fetch(
    request("https://worker.example/api/ai/connection-test", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ provider: {} })
    }),
    { SHENK_TOKEN: "valid" }
  );
  assert.equal(response.status, 401);
}

{
  const worker = loadWorker();
  const response = await worker.fetch(
    request("https://worker.example/api/ai/connection-test", {
      method: "POST",
      headers: { Authorization: "Bearer timer", "Content-Type": "application/json" },
      body: JSON.stringify({ provider: {} })
    }),
    { TIMER_TOKEN: "timer" }
  );
  assert.equal(response.status, 403);
  assert.equal((await response.json()).error, "forbidden_ai_review_role");
}

{
  const worker = loadWorker(() => { throw new Error("private provider must not be called"); });
  const response = await worker.fetch(
    request("https://worker.example/api/ai/connection-test", {
      method: "POST",
      headers: { Authorization: "Bearer valid", "Content-Type": "application/json" },
      body: JSON.stringify({
        provider: { id: "custom", baseUrl: "https://127.0.0.1/v1", model: "fixture", apiKey: "fixture-secret" }
      })
    }),
    { SHENK_TOKEN: "valid" }
  );
  assert.equal(response.status, 400);
  assert.equal((await response.json()).error, "private_ai_provider_url_forbidden");
}

{
  let upstreamRequest;
  const worker = loadWorker(async (url, options) => {
    upstreamRequest = { url, options };
    return new Response(JSON.stringify({ choices: [{ message: { content: "OK" } }] }), {
      status: 200,
      headers: { "Content-Type": "application/json" }
    });
  });
  const response = await worker.fetch(
    request("https://worker.example/api/ai/connection-test", {
      method: "POST",
      headers: { Authorization: "Bearer valid", "Content-Type": "application/json" },
      body: JSON.stringify({
        provider: { id: "deepseek", baseUrl: "https://api.deepseek.com", model: "deepseek-v4-flash", apiKey: "fixture-secret" }
      })
    }),
    { SHENK_TOKEN: "valid" }
  );
  const upstreamBody = JSON.parse(upstreamRequest.options.body);
  assert.equal(response.status, 200);
  assert.equal(upstreamRequest.url, "https://api.deepseek.com/chat/completions");
  assert.equal(upstreamBody.max_tokens, 32);
  assert.deepEqual(upstreamBody.thinking, { type: "disabled" });
  assert.ok(upstreamRequest.options.signal);
}

{
  let upstreamRequest;
  const worker = loadWorker(async (url, options) => {
    upstreamRequest = { url, options };
    return new Response(JSON.stringify({
        choices: [{ message: { content: JSON.stringify({
          conclusion: "Synthetic review.",
          assessment: "Recovery is appropriate today.",
          actions: ["Keep the planned easy session."],
          evidence: ["fixture"],
          cautions: [],
          localSuggestion: {
            date: "2099-01-01",
            title: "Easy walk",
            trainingType: "easy_walk",
            estimatedMinutes: 25,
            reason: "No formal plan exists."
          }
        }) } }]
    }), { status: 200, headers: { "Content-Type": "application/json" } });
  });
  const env = dailyReviewWorkflowEnv(worker, aiJobDb());
  const response = await worker.fetch(
    request("https://worker.example/api/ai/daily-review", {
      method: "POST",
      headers: { Authorization: "Bearer valid", "Content-Type": "application/json" },
      body: JSON.stringify({
        ...dailyJobFields("2099-01-01"),
        provider: { id: "custom", baseUrl: "https://provider.example/v1", model: "fixture", apiKey: "fixture-secret" },
        snapshot: {
          schema: "daily_review_snapshot",
          contractVersion: "2.0",
          date: "2099-01-01",
          missingCriticalFields: [],
          records: []
        }
      })
    }),
    env
  );
  const accepted = await response.json();
  const { body } = await readDailyReviewStatus(worker, env, dailyJobFields("2099-01-01").jobId);
  const upstreamBody = JSON.parse(upstreamRequest.options.body);
  const systemPrompt = upstreamBody.messages.find((message) => message.role === "system")?.content || "";
    assert.equal(response.status, 202);
    assert.equal(accepted.state, "RUNNING");
    assert.doesNotMatch(JSON.stringify(env.lastWorkflowParams), /fixture-secret|daily_review_snapshot/);
    assert.equal(body.review.conclusion, "Synthetic review.");
    assert.equal(body.review.assessment, "Recovery is appropriate today.");
    assert.equal(body.review.localSuggestion.title, "Easy walk");
    assert.deepEqual(body.review.actions, ["Keep the planned easy session."]);
  assert.equal(upstreamRequest.url, "https://provider.example/v1/chat/completions");
  assert.equal(upstreamRequest.options.headers.Authorization, "Bearer fixture-secret");
    assert.deepEqual(upstreamBody.thinking, { type: "enabled" });
    assert.equal(upstreamBody.max_tokens, 42_066);
    assert.deepEqual(upstreamBody.response_format, { type: "json_object" });
    assert.equal(upstreamRequest.options.signal, undefined);
    assert.match(systemPrompt, /已经发生的执行结果/);
    assert.match(systemPrompt, /当天完成得怎么样/);
    assert.match(systemPrompt, /后续修正措施/);
    assert.doesNotMatch(systemPrompt, /今天怎么做/);
    assert.doesNotMatch(JSON.stringify(body), /fixture-secret/);
  }

  {
    let upstreamCalls = 0;
    const db = aiJobDb();
    const worker = loadWorker(async () => {
      upstreamCalls += 1;
      return new Response(JSON.stringify({
        choices: [{
          finish_reason: "stop",
          message: { content: JSON.stringify({
            conclusion: "Idempotent review.",
            assessment: "One provider call is enough.",
            actions: ["Keep the next session easy."],
            evidence: [],
            cautions: [],
            localSuggestion: null
          }) }
        }],
        usage: {
          prompt_tokens: 21033,
          prompt_cache_hit_tokens: 18000,
          prompt_cache_miss_tokens: 3033,
          completion_tokens: 1980,
          total_tokens: 23013,
          completion_tokens_details: { reasoning_tokens: 1500 }
        }
      }), { status: 200, headers: { "Content-Type": "application/json" } });
    });
    const body = JSON.stringify({
      ...dailyJobFields("2099-01-05"),
      provider: { id: "custom", baseUrl: "https://provider.example/v1", model: "fixture", apiKey: "fixture-secret" },
      snapshot: { schema: "daily_review_snapshot", contractVersion: "2.0", date: "2099-01-05", records: [] }
    });
    const env = dailyReviewWorkflowEnv(worker, db);
    const invoke = () => worker.fetch(request("https://worker.example/api/ai/daily-review", {
      method: "POST",
      headers: { Authorization: "Bearer valid", "Content-Type": "application/json" },
      body
    }), env);

    const first = await invoke();
    const firstBody = await first.json();
    const completed = await readDailyReviewStatus(worker, env, dailyJobFields("2099-01-05").jobId);
    const second = await invoke();
    const secondBody = await second.json();
    const status = await worker.fetch(
      request(`https://worker.example/api/ai/daily-review-jobs/${encodeURIComponent(dailyJobFields("2099-01-05").jobId)}`, {
        headers: { Authorization: "Bearer valid" }
      }),
      env
    );
    assert.equal(first.status, 202);
    assert.equal(firstBody.state, "RUNNING");
    assert.equal(completed.body.state, "SUCCEEDED");
    assert.equal(secondBody.state, "SUCCEEDED");
    assert.equal((await status.json()).state, "SUCCEEDED");
    assert.equal(upstreamCalls, 1);
    assert.equal(completed.body.usage.totalTokens, 23013);
    assert.equal(completed.body.usage.reasoningTokens, 1500);
    assert.equal(completed.body.finishReason, "stop");
    assert.equal(completed.body.upstreamRequests, 1);
  }

  {
    const upstreamBodies = [];
    const worker = loadWorker(async (_url, options) => {
      const requestBody = JSON.parse(options.body);
      upstreamBodies.push(requestBody);
      const content = upstreamBodies.length === 1
        ? JSON.stringify({ conclusion: "First response omitted actions." })
        : JSON.stringify({
            conclusion: "Repaired review.",
            assessment: "The second bounded pass repaired the structure.",
            actions: ["Keep the next session easy."],
            evidence: [],
            cautions: [],
            localSuggestion: null
          });
      return new Response(JSON.stringify({ choices: [{ message: { content } }] }), {
        status: 200,
        headers: { "Content-Type": "application/json" }
      });
    });
    const env = dailyReviewWorkflowEnv(worker, aiJobDb());
    const response = await worker.fetch(
      request("https://worker.example/api/ai/daily-review", {
        method: "POST",
        headers: { Authorization: "Bearer valid", "Content-Type": "application/json" },
        body: JSON.stringify({
          ...dailyJobFields("2099-01-04"),
          provider: { id: "custom", baseUrl: "https://provider.example/v1", model: "fixture", apiKey: "fixture-secret" },
          snapshot: { schema: "daily_review_snapshot", contractVersion: "2.0", date: "2099-01-04", records: [] }
        })
      }),
      env
    );
    const accepted = await response.json();
    const { body } = await readDailyReviewStatus(worker, env, dailyJobFields("2099-01-04").jobId);
    assert.equal(response.status, 202);
    assert.equal(accepted.state, "RUNNING");
    assert.equal(body.review.conclusion, "Repaired review.");
    assert.equal(upstreamBodies.length, 2);
    assert.deepEqual(upstreamBodies[0].thinking, { type: "enabled" });
    assert.deepEqual(upstreamBodies[1].thinking, { type: "disabled" });
    assert.equal(upstreamBodies[1].max_tokens, 8192);
  }

  {
    let observedSignal;
    const worker = loadWorker(async (_url, options) => {
      observedSignal = options.signal;
      return new Response(JSON.stringify({
        choices: [{ message: { content: JSON.stringify({
          conclusion: "Long-running review.",
          assessment: "Generation completed without a client deadline.",
          actions: ["Keep the next session easy."],
          evidence: [],
          cautions: [],
          localSuggestion: null
        }) } }]
      }), { status: 200, headers: { "Content-Type": "application/json" } });
    });
    const env = dailyReviewWorkflowEnv(worker, aiJobDb());
    const response = await worker.fetch(
      request("https://worker.example/api/ai/daily-review", {
        method: "POST",
        headers: { Authorization: "Bearer valid", "Content-Type": "application/json" },
        body: JSON.stringify({
          ...dailyJobFields("2099-01-03"),
          provider: { id: "custom", baseUrl: "https://provider.example/v1", model: "fixture", apiKey: "fixture-secret" },
          snapshot: { schema: "daily_review_snapshot", contractVersion: "2.0", date: "2099-01-03", records: [] }
        })
      }),
      env
    );
    const accepted = await response.json();
    const { body } = await readDailyReviewStatus(worker, env, dailyJobFields("2099-01-03").jobId);
    assert.equal(response.status, 202);
    assert.equal(accepted.state, "RUNNING");
    assert.equal(body.state, "SUCCEEDED");
    assert.equal(body.review.conclusion, "Long-running review.");
    assert.equal(observedSignal, undefined);
  }

  {
    const worker = loadWorker(async () => new Response(JSON.stringify({
      choices: [{ message: { content: JSON.stringify({
        conclusion: "Keep the formal plan.",
        assessment: "The current plan remains suitable.",
        actions: ["Follow the formal plan."],
        evidence: [],
        cautions: [],
        localSuggestion: {
          date: "2099-01-02",
          title: "Unauthorized replacement",
          trainingType: "recovery"
        }
      }) } }]
    }), { status: 200, headers: { "Content-Type": "application/json" } }));
    const env = dailyReviewWorkflowEnv(worker, aiJobDb());
    const response = await worker.fetch(
      request("https://worker.example/api/ai/daily-review", {
        method: "POST",
        headers: { Authorization: "Bearer valid", "Content-Type": "application/json" },
        body: JSON.stringify({
          ...dailyJobFields("2099-01-02"),
          provider: { id: "custom", baseUrl: "https://provider.example/v1", model: "fixture", apiKey: "fixture-secret" },
          snapshot: {
            schema: "daily_review_snapshot",
            contractVersion: "2.0",
            date: "2099-01-02",
            missingCriticalFields: [],
            records: [{
              entity: "daily_plan_items",
              id: "plan-1",
              data: { date: "2099-01-02", title: "Strength", trainingType: "strength" }
            }]
          }
        })
      }),
      env
    );
    const accepted = await response.json();
    const { body } = await readDailyReviewStatus(worker, env, dailyJobFields("2099-01-02").jobId);
    assert.equal(response.status, 202);
    assert.equal(accepted.state, "RUNNING");
    assert.equal(body.review.localSuggestion, null);
  }

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
    request("https://worker.example/api/health", {
      headers: { Origin: "https://s2qtech.github.io" }
    }),
    { SHENK_TOKEN: "valid", ALLOWED_ORIGINS: "https://s2qtech.github.io" }
  );
  assert.match(response.headers.get("Access-Control-Allow-Headers") || "", /X-Shenke-Profile-Key/);
}

{
  const worker = loadWorker();
  const profileAccessKey = "fixture_profile_access_key_1234567890";
  const accessKeyHash = nodeCrypto.createHash("sha256").update(profileAccessKey).digest("hex");
  const row = {
    id: "profile_fixture",
    revision: 1,
    device_id: "desktop",
    created_at: "2099-01-01T00:00:00.000Z",
    updated_at: "2099-01-01T00:00:00.000Z",
    profile_json: JSON.stringify({ schema: "shenk_sync_profile/v1", cipher: "AES-GCM" }),
    access_key_hash: accessKeyHash
  };
  const db = {
    prepare(sql) {
      if (sql.includes("SELECT id, revision, device_id")) {
        return { bind() { return { first: async () => row }; } };
      }
      return {
        bind() { return { run: async () => ({}) }; },
        run: async () => ({})
      };
    }
  };
  const response = await worker.fetch(
    request("https://worker.example/api/sync-profiles/profile_fixture", {
      headers: { "X-Shenke-Profile-Key": profileAccessKey }
    }),
    { SHENK_TOKEN: "valid", DB: db }
  );
  const body = await response.json();
  assert.equal(response.status, 200);
  assert.equal(body.id, "profile_fixture");
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
  const existing = {
    entity: "routine_templates",
    id: "routine_published_lifecycle",
    revision: 1,
    device_id: "coach",
    created_at: "2099-01-01T00:00:00.000Z",
    updated_at: "2099-01-01T00:00:00.000Z",
    deleted_at: null,
    data_json: JSON.stringify({ id: "routine_published_lifecycle", lifecycle: "published", title: "Original", steps: [] })
  };
  const writes = [];
  const db = {
    prepare(sql) {
      if (sql.startsWith("SELECT entity, id, revision")) {
        return { bind() { return { first: async () => existing }; } };
      }
      return { bind(...args) { writes.push({ sql, args }); return { run: async () => ({}) }; } };
    }
  };
  const response = await worker.fetch(
    request("https://worker.example/api/records/upsert", {
      method: "POST",
      headers: { Authorization: "Bearer valid", "Content-Type": "application/json" },
      body: JSON.stringify({
        records: [{
          entity: "routine_templates",
          id: "routine_published_lifecycle",
          baseRevision: 1,
          data: {
            id: "routine_published_lifecycle",
            lifecycle: "archived",
            timerVisible: false,
            title: "Original",
            steps: []
          }
        }]
      })
    }),
    { SHENK_TOKEN: "valid", DB: db }
  );
  const body = await response.json();
  assert.equal(body.conflicts.length, 0);
  assert.equal(body.accepted.length, 1);
  assert.equal(writes.length, 2);
}

{
  const worker = loadWorker();
  const existing = {
    entity: "routine_templates",
    id: "routine_published_delete",
    revision: 1,
    device_id: "coach",
    created_at: "2099-01-01T00:00:00.000Z",
    updated_at: "2099-01-01T00:00:00.000Z",
    deleted_at: null,
    data_json: JSON.stringify({ id: "routine_published_delete", lifecycle: "published", title: "Original", steps: [] })
  };
  const writes = [];
  const db = {
    prepare(sql) {
      if (sql.startsWith("SELECT entity, id, revision")) {
        return { bind() { return { first: async () => existing }; } };
      }
      return { bind(...args) { writes.push({ sql, args }); return { run: async () => ({}) }; } };
    }
  };
  const response = await worker.fetch(
    request("https://worker.example/api/records/upsert", {
      method: "POST",
      headers: { Authorization: "Bearer valid", "Content-Type": "application/json" },
      body: JSON.stringify({
        records: [{
          entity: "routine_templates",
          id: "routine_published_delete",
          baseRevision: 1,
          deletedAt: "2099-01-02T00:00:00.000Z",
          data: { id: "routine_published_delete", lifecycle: "published", title: "Original", steps: [] }
        }]
      })
    }),
    { SHENK_TOKEN: "valid", DB: db }
  );
  const body = await response.json();
  assert.equal(body.conflicts.length, 0);
  assert.equal(body.accepted.length, 1);
  assert.equal(writes.length, 2);
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

{
  const worker = loadWorker();
  const response = await worker.fetch(
    request("https://worker.example/api/health"),
    { SHENK_TOKEN: "valid" }
  );
  const body = await response.json();
  assert.equal(body.contractVersion, "1.0");
  assert.deepEqual(Array.from(body.supportedContractVersions), ["1.0", "2.0"]);
}

{
  const worker = loadWorker();
  const response = await worker.fetch(
    request("https://worker.example/api/records/query", {
      method: "POST",
      headers: { Authorization: "Bearer valid", "Content-Type": "application/json" },
      body: JSON.stringify({ contractVersion: "1.0", entities: ["status_checkins"] })
    }),
    { SHENK_TOKEN: "valid", DB: { prepare() { throw new Error("v1 must not query v2-only entities"); } } }
  );
  const body = await response.json();
  assert.equal(response.status, 200);
  assert.equal(body.contractVersion, "1.0");
  assert.deepEqual(Array.from(body.records), []);
}

{
  const worker = loadWorker();
  const unknownData = {
    id: "checkin_roundtrip",
    date: "2099-01-01",
    kind: "morning",
    observedAt: "2099-01-01T00:30:00.000Z",
    futureCompatibleField: { keep: true }
  };
  const row = {
    entity: "status_checkins",
    id: "checkin_roundtrip",
    revision: 1,
    device_id: "android",
    created_at: "2099-01-01T00:30:00.000Z",
    updated_at: "2099-01-01T00:30:00.000Z",
    deleted_at: null,
    data_json: JSON.stringify(unknownData)
  };
  const response = await worker.fetch(
    request("https://worker.example/api/records/query", {
      method: "POST",
      headers: { Authorization: "Bearer valid", "Content-Type": "application/json" },
      body: JSON.stringify({ contractVersion: "2.0", entities: ["status_checkins"] })
    }),
    {
      SHENK_TOKEN: "valid",
      DB: { prepare() { return { bind() { return { all: async () => ({ results: [row] }) }; } }; } }
    }
  );
  const body = await response.json();
  assert.equal(body.contractVersion, "2.0");
  assert.deepEqual(body.records[0].data.futureCompatibleField, { keep: true });
}

{
  const worker = loadWorker();
  const writes = [];
  const checkin = {
    id: "checkin_v2_write",
    date: "2099-01-01",
    kind: "morning",
    observedAt: "2099-01-01T00:31:00.000Z",
    pain: [{ region: "wrist", severity: 1, side: "right" }]
  };
  const db = {
    prepare(sql) {
      if (sql.startsWith("SELECT entity, id, revision")) return { bind() { return { first: async () => null }; } };
      return { bind(...args) { writes.push({ sql, args }); return { run: async () => ({}) }; } };
    }
  };
  const response = await worker.fetch(
    request("https://worker.example/api/records/upsert", {
      method: "POST",
      headers: { Authorization: "Bearer valid", "Content-Type": "application/json" },
      body: JSON.stringify({
        contractVersion: "2.0",
        records: [{ entity: "status_checkins", id: checkin.id, data: checkin }]
      })
    }),
    { SHENK_TOKEN: "valid", DB: db }
  );
  const body = await response.json();
  assert.equal(body.contractVersion, "2.0");
  assert.equal(body.accepted.length, 1);
  assert.equal(body.conflicts.length, 0);
  assert.equal(writes.length, 2);
}

{
  const worker = loadWorker();
  const response = await worker.fetch(
    request("https://worker.example/api/records/upsert", {
      method: "POST",
      headers: { Authorization: "Bearer valid", "Content-Type": "application/json" },
      body: JSON.stringify({
        contractVersion: "2.0",
        records: [{
          entity: "status_checkins",
          id: "timer_cannot_write_checkin",
          data: { id: "timer_cannot_write_checkin", date: "2099-01-01", kind: "morning", observedAt: "2099-01-01T00:00:00.000Z" }
        }]
      })
    }),
    { TIMER_TOKEN: "valid", DB: { prepare() { throw new Error("forbidden records must not query D1"); } } }
  );
  const body = await response.json();
  assert.equal(body.conflicts[0].reason, "forbidden_entity_for_role");
}

{
  const worker = loadWorker();
  const response = await worker.fetch(
    request("https://worker.example/api/records/upsert", {
      method: "POST",
      headers: { Authorization: "Bearer valid", "Content-Type": "application/json" },
      body: JSON.stringify({
        contractVersion: "2.0",
        records: [{
          entity: "timer_sessions",
          id: "invalid_v2_timer",
          data: {
            id: "invalid_v2_timer",
            date: "2099-01-01",
            routineId: "routine_fixture",
            startedAt: "2099-01-01T01:00:00.000Z",
            completion: "completed"
          }
        }]
      })
    }),
    { TIMER_TOKEN: "valid", DB: { prepare() { throw new Error("invalid records must not query D1"); } } }
  );
  const body = await response.json();
  assert.equal(body.conflicts[0].reason, "missing_v2_field:routineVersion");
}

{
  const worker = loadWorker();
  const response = await worker.fetch(
    request("https://worker.example/api/android/update/metadata"),
    { SHENK_TOKEN: "valid" }
  );
  assert.equal(response.status, 401);
}

{
  const worker = loadWorker();
  const response = await worker.fetch(
    request("https://worker.example/api/android/update/metadata", {
      headers: { Authorization: "Bearer timer" }
    }),
    { TIMER_TOKEN: "timer" }
  );
  assert.equal(response.status, 403);
  assert.equal((await response.json()).error, "forbidden_android_update_role");
}

{
  const worker = loadWorker();
  const response = await worker.fetch(
    request("https://worker.example/api/android/update/metadata", {
      headers: { Authorization: "Bearer valid" }
    }),
    { SHENK_TOKEN: "valid" }
  );
  assert.equal(response.status, 200);
  assert.equal((await response.json()).release, null);
}

{
  const apkBytes = new TextEncoder().encode("synthetic private apk");
  const release = {
    applicationId: "io.s2qtech.shenk",
    versionCode: 11,
    versionName: "0.8.2-package8-p8.2",
    sha256: "a".repeat(64),
    sizeBytes: apkBytes.byteLength,
    objectKey: "android/shenk-11.apk",
    publishedAt: "2099-01-01T00:00:00.000Z"
  };
  const env = {
    SHENK_TOKEN: "valid",
    ANDROID_RELEASE_METADATA: JSON.stringify(release),
    ANDROID_RELEASES: {
      async get(key) {
        assert.equal(key, release.objectKey);
        return { body: apkBytes, size: apkBytes.byteLength };
      }
    }
  };
  const worker = loadWorker();
  const metadataResponse = await worker.fetch(
    request("https://worker.example/api/android/update/metadata", {
      headers: { Authorization: "Bearer valid" }
    }),
    env
  );
  const metadataBody = await metadataResponse.json();
  assert.equal(metadataResponse.status, 200);
  assert.equal(metadataBody.release.versionCode, 11);
  assert.equal(metadataBody.release.objectKey, undefined);

  const apkResponse = await worker.fetch(
    request("https://worker.example/api/android/update/apk", {
      headers: { Authorization: "Bearer valid" }
    }),
    env
  );
  assert.equal(apkResponse.status, 200);
  assert.equal(apkResponse.headers.get("Content-Type"), "application/vnd.android.package-archive");
  assert.equal(apkResponse.headers.get("Cache-Control"), "private, no-store");
  assert.deepEqual(new Uint8Array(await apkResponse.arrayBuffer()), apkBytes);
}

console.log("worker security tests passed");
}

run().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
