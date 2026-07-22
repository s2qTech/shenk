"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");

const root = path.join(__dirname, "..", "contracts");
const readJson = (...parts) => JSON.parse(fs.readFileSync(path.join(root, ...parts), "utf8"));

const v1Schema = readJson("v1", "contract.schema.json");
const schema = readJson("v2", "contract.schema.json");
const fixtures = readJson("v2", "contract-fixtures.json");
const openApi = readJson("v2", "openapi.json");

test("Contract v2 is additive over Contract v1", () => {
  assert.equal(schema.properties.contractVersion.const, "2.0");
  assert.equal(fixtures.contractVersion, "2.0");
  assert.equal(fixtures.synthetic, true);

  const v1Entities = new Set(v1Schema.$defs.entity.enum);
  const v2Entities = new Set(schema.$defs.entity.enum);
  for (const entity of v1Entities) assert.ok(v2Entities.has(entity), `v2 removed ${entity}`);
  for (const entity of ["status_checkins", "daily_reviews", "plan_import_batches", "goal_sets", "coach_strategies", "planning_runs", "coach_plan_patches"]) {
    assert.ok(v2Entities.has(entity), `v2 missing ${entity}`);
  }
  assert.equal(schema.$defs.recordEnvelope.allOf.length, v2Entities.size);
});

test("Contract v2 encodes authority and explicit routine classification", () => {
  const required = schema.$defs.routineTemplate.required;
  for (const field of ["scene", "role", "lifecycle", "timerVisible", "calendarVisible", "countsTowardTraining"]) {
    assert.ok(required.includes(field), `routineTemplate must require ${field}`);
  }
  assert.deepEqual(schema.$defs.routineTemplate.properties.scene.enum, ["home", "walk", "recovery", "travel"]);
  assert.deepEqual(schema.$defs.routineTemplate.properties.role.enum, ["main", "warmup", "stretch", "cooldown", "recovery", "auxiliary"]);
  assert.equal(schema.$defs.statusCheckin.properties.kind.enum.includes("pre_workout"), true);
});

test("sanitized v2 records cover every new entity and preserve compatible fields", () => {
  const byEntity = new Map(fixtures.records.map(record => [record.entity, record]));
  for (const entity of ["routine_templates", "timer_sessions", "body_metrics", "status_checkins", "daily_reviews", "plan_import_batches", "goal_sets", "coach_strategies", "planning_runs", "coach_plan_patches"]) {
    const record = byEntity.get(entity);
    assert.ok(record, `missing fixture for ${entity}`);
    assert.equal(record.contractVersion, "2.0");
    assert.equal(record.data.id, record.id);
  }
  const step = byEntity.get("routine_templates").data.steps[0];
  assert.equal(step.execution.mode, "bilateral_hold");
  assert.equal(step.mediaAssetId, "media_fixture_calf");
  assert.equal(step.futureCompatibleField, "preserve-me");
  assert.equal(byEntity.get("status_checkins").data.energy, 4);
  assert.equal(fixtures.coachPlanPatch.replaceMode, false);
  assert.deepEqual(fixtures.coachPlanPatch.routineTemplates, []);
  assert.deepEqual(fixtures.coachPlanPatch.dailyPlanItems, []);
});

test("compatibility fixtures keep patch, deletion, conflict, and legacy metric semantics explicit", () => {
  const cases = fixtures.compatibilityCases;
  assert.deepEqual(cases.emptyPatchIsNoOp, {
    routineTemplates: [],
    dailyPlanItems: [],
    planAdjustments: [],
    planTemplates: []
  });
  assert.match(cases.explicitDelete.deletedAt, /^2099-/);
  assert.ok(cases.staleConflict.baseRevision < cases.staleConflict.serverRevision);
  assert.equal(cases.staleConflict.expected, "conflict");
  assert.notEqual(
    cases.legacyBodyMetricMigration.expectedBodyMetricId,
    cases.legacyBodyMetricMigration.expectedStatusCheckinId
  );
});

test("OpenAPI v2 publishes version negotiation and disjoint entity ownership", () => {
  assert.equal(openApi.openapi, "3.1.0");
  assert.equal(openApi.info.version, "2.0");
  assert.deepEqual(openApi["x-shenk-supported-contract-versions"], ["1.0", "2.0"]);
  assert.ok(openApi.paths["/records/query"]);
  assert.ok(openApi.paths["/records/upsert"]);

  const owners = openApi["x-shenk-entity-owners"];
  const assigned = Object.values(owners).flat();
  assert.equal(new Set(assigned).size, assigned.length, "an entity has multiple writers");
  assert.deepEqual(new Set(assigned), new Set(schema.$defs.entity.enum), "ownership must cover every v2 entity");
  assert.deepEqual(owners.timer, ["timer_sessions"]);
  assert.ok(owners.record.includes("status_checkins"));
  assert.ok(owners.ai_review.includes("daily_reviews"));
  assert.deepEqual(owners.planning_exchange, ["planning_runs", "coach_plan_patches"]);
});

test("planning exchange fixtures stay pending and cannot imply formal application", () => {
  const byEntity = new Map(fixtures.records.map(record => [record.entity, record]));
  const run = byEntity.get("planning_runs").data;
  const draft = byEntity.get("coach_plan_patches").data;
  assert.equal(run.source, "chatgpt_mcp");
  assert.equal(draft.status, "pending");
  assert.equal(draft.patch.replaceMode, false);
  assert.equal(draft.runId, run.id);
  assert.equal(draft.snapshotDigest, run.snapshotDigest);
});

test("Contract fixtures contain no credential-shaped fields", () => {
  const fixtureText = JSON.stringify(fixtures);
  assert.doesNotMatch(fixtureText, /api[_-]?key|access[_-]?token|bearer\s|password|migration[_-]?code/i);
});
