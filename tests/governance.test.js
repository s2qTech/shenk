"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");

const root = path.resolve(__dirname, "..");
const read = (relativePath) => fs.readFileSync(path.join(root, relativePath), "utf8");

test("repository governance entrypoints and canonical documents are present", () => {
  const guardrails = JSON.parse(read("governance/guardrails.json"));
  const agents = read("AGENTS.md");

  assert.equal(guardrails.schema, "shenk_project_guardrails/v1");
  assert.equal(guardrails.activeDelivery.phase, "android_package_4_ready");
  assert.equal(guardrails.activeDelivery.progress, "4/9");
  assert.equal(guardrails.activeDelivery.productionContract, "1.0");
  assert.deepEqual(guardrails.activeDelivery.workerSupportedContracts, ["1.0", "2.0"]);
  assert.equal(guardrails.activeDelivery.androidProductionStack, "kotlin_jetpack_compose");
  assert.equal(guardrails.activeDelivery.capacitorStatus, "frozen_prototype");

  for (const relativePath of guardrails.canonicalDocuments) {
    assert.ok(fs.existsSync(path.join(root, relativePath)), `missing canonical document: ${relativePath}`);
    assert.ok(agents.includes(relativePath) || relativePath === "AGENTS.md", `AGENTS.md must reference ${relativePath}`);
  }
});

test("critical guardrails keep stable identifiers and ownership", () => {
  const guardrails = JSON.parse(read("governance/guardrails.json"));
  const contractV2 = JSON.parse(read("contracts/v2/contract.schema.json"));
  const ids = guardrails.requiredInvariants.map((item) => item.id);

  assert.equal(new Set(ids).size, ids.length, "guardrail IDs must be unique");
  for (const requiredId of [
    "G-DATA-001",
    "G-DATA-002",
    "G-PLAN-001",
    "G-PLAN-002",
    "G-ROUTINE-001",
    "G-TIMER-001",
    "G-AI-001",
    "G-SEC-001",
    "G-ANDROID-001",
    "G-DELETE-001"
  ]) {
    assert.ok(ids.includes(requiredId), `missing required guardrail: ${requiredId}`);
  }

  assert.deepEqual(guardrails.dayDisplayPriority, [
    "training_logs",
    "effective_formal_plan",
    "local_fallback_suggestion"
  ]);
  assert.deepEqual(guardrails.entityOwnership.timer_module, ["timer_sessions"]);
  assert.ok(!guardrails.entityOwnership.record_module.includes("timer_sessions"));
  assert.deepEqual(guardrails.entityOwnership.asset_module, ["media_assets"]);
  assert.deepEqual(
    new Set(Object.values(guardrails.entityOwnership).flat()),
    new Set(contractV2.$defs.entity.enum),
    "guardrail ownership must cover every Contract v2 entity exactly once"
  );
});

test("accepted ADRs and historical-document warnings remain visible", () => {
  const acceptedAdrs = [
    "docs/adr/0001-native-android-compose.md",
    "docs/adr/0002-local-first-room-outbox.md",
    "docs/adr/0003-ai-authority-and-day-priority.md",
    "docs/adr/0004-timer-facts-and-formal-records.md",
    "docs/adr/0005-explicit-routine-scene-role.md"
  ];
  for (const relativePath of acceptedAdrs) {
    assert.match(read(relativePath), /Status: Accepted/);
  }

  for (const relativePath of [
    "docs/android-foundation.md",
    "docs/mobile-strategy.md",
    "docs/next-stage-development-plan.md",
    "docs/product-constraints.md"
  ]) {
    assert.match(read(relativePath), /Status:.*(superseded|historical)/i, `${relativePath} needs a historical warning`);
  }
});

test("independent timer repository has a canonical agent-rule source", () => {
  const timerRules = read("governance/timer-repository-AGENTS.md");
  const timerBoundaries = read("governance/timer-PROJECT_BOUNDARIES.md");
  assert.match(timerRules, /Web timer 只写 `timer_sessions`/);
  assert.match(timerRules, /禁止从标题、trainingType、routineId 或动作内容推断/);
  assert.match(timerRules, /不得写 `training_logs`/);
  assert.match(timerRules, /Android 原生计时器/);
  assert.match(timerBoundaries, /Contract v1 remains the timer's default production write contract/);
  assert.match(timerBoundaries, /Contract v2 passed the additive Worker and cross-client compatibility gate/);
  assert.match(timerBoundaries, /Never infer or rewrite them from title/);
});
