"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");

const root = path.join(__dirname, "..", "contracts", "v1");
const schema = JSON.parse(fs.readFileSync(path.join(root, "contract.schema.json"), "utf8"));
const fixtures = JSON.parse(fs.readFileSync(path.join(root, "contract-fixtures.json"), "utf8"));

assert.equal(schema.properties.contractVersion.const, "1.0");
assert.equal(fixtures.contractVersion, schema.properties.contractVersion.const);
assert.deepEqual(schema.$defs.trainingType.enum, [
  "strength", "easy_walk", "quality_walk", "indoor_cardio", "warmup",
  "cooldown", "recovery", "travel_strength", "seat_recovery", "stretch", "rest"
]);
assert.deepEqual(schema.$defs.executionMode.enum, [
  "simple", "prepare_only", "alternating", "bilateral_hold", "bilateral_reps"
]);
assert.equal(fixtures.recordEnvelope.contractVersion, "1.0");
assert.equal(fixtures.recordEnvelope.data.id, fixtures.recordEnvelope.id);
assert.ok(schema.$defs.entity.enum.includes(fixtures.recordEnvelope.entity));
assert.equal(schema.$defs.recordEnvelope.allOf.length, schema.$defs.entity.enum.length);
for (const entity of schema.$defs.entity.enum) {
  assert.match(JSON.stringify(schema.$defs.recordEnvelope.allOf), new RegExp(`"${entity}"`));
}
assert.equal(fixtures.recordEnvelope.data.completion, "completed");
assert.equal(fixtures.coachPlanPatch.schema, "coach_plan_patch");
assert.equal(fixtures.coachPlanPatch.contractVersion, "1.0");
assert.equal(fixtures.coachPlanPatch.routineTemplates[0].steps[0].execution.mode, "bilateral_hold");

const openApi = JSON.parse(fs.readFileSync(path.join(root, "openapi.json"), "utf8"));
assert.equal(openApi.openapi, "3.1.0");
assert.ok(openApi.paths["/records/query"]);
assert.ok(openApi.paths["/records/upsert"]);
assert.ok(openApi.paths["/timer-sessions"]);
assert.equal(openApi.components.schemas.RecordQuery.properties.limit.maximum, 500);
assert.ok(openApi.components.schemas.RecordQueryResponse.properties.nextCursor);

console.log("contract v1 tests passed");
