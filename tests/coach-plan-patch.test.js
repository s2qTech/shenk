"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");

function loadAppTestApi() {
  const appPath = path.join(__dirname, "..", "src", "app.js");
  const corePath = path.join(__dirname, "..", "src", "sync-profile-core.js");
  const storagePath = path.join(__dirname, "..", "src", "snapshot-storage.js");
  const entityStorePath = path.join(__dirname, "..", "src", "entity-store.js");
  const source = fs.readFileSync(appPath, "utf8");
  const coreSource = fs.readFileSync(corePath, "utf8");
  const storageSource = fs.readFileSync(storagePath, "utf8");
  const entityStoreSource = fs.readFileSync(entityStorePath, "utf8");
  const hook = `
  globalThis.__shenkeAppTest = {
    state,
    createEmptySharedRecords,
    upsertSharedEnvelope,
    normalizeDailyPlanItemData,
    normalizePlanAdjustmentData,
    normalizeLooseSharedData,
    normalizeRoutineTemplateData,
    previewPlanPatch,
    applyCoachPlanPatch,
    findSharedRecordById
  };
`;
  const instrumented = source.replace(/\}\)\(\);\s*$/, `${hook}\n})();`);
  const storage = new Map();
  const element = {
    innerHTML: "",
    className: "",
    dataset: {},
    classList: { add() {}, remove() {}, toggle() {} },
    addEventListener() {},
    appendChild() {},
    remove() {},
    querySelector() { return null; },
    querySelectorAll() { return []; },
    setAttribute() {},
    getAttribute() { return null; }
  };
  const context = {
    console,
    setTimeout,
    clearTimeout,
    Blob: function Blob() {},
    URL: { createObjectURL: () => "", revokeObjectURL() {} },
    FileReader: function FileReader() {},
    window: {
      localStorage: {
        getItem(key) { return storage.has(key) ? storage.get(key) : null; },
        setItem(key, value) { storage.set(key, String(value)); },
        removeItem(key) { storage.delete(key); }
      },
      crypto: { randomUUID: () => `uuid_${Math.random().toString(16).slice(2)}` },
      addEventListener() {},
      setTimeout,
      clearTimeout,
      confirm: () => true,
      prompt: () => null,
      navigator: {},
      ShenkeRecommendationEngine: { getRecommendation: () => ({ type: "easyWalk" }) }
    },
    document: {
      body: element,
      getElementById: () => element,
      addEventListener() {},
      createElement: () => ({ ...element, click() {} }),
      querySelector: () => null,
      querySelectorAll: () => []
    },
    navigator: {},
    indexedDB: undefined
  };
  context.globalThis = context;
  vm.createContext(context);
  vm.runInContext(coreSource, context, { filename: corePath });
  vm.runInContext(storageSource, context, { filename: storagePath });
  vm.runInContext(entityStoreSource, context, { filename: entityStorePath });
  vm.runInContext(instrumented, context, { filename: appPath });
  return context.__shenkeAppTest;
}

function reset(api) {
  api.state.records = api.createEmptySharedRecords();
  api.state.workouts = [];
  api.state.bodyMetrics = [];
}

function upsert(api, entity, data) {
  api.upsertSharedEnvelope(api.state.records, entity, data);
}

function record(api, entity, id) {
  return api.findSharedRecordById(entity, id, true);
}

function routinePatch(overrides = {}) {
  return {
    id: "routine_strength_a",
    title: "Strength A",
    trainingType: "strength",
    timerVisible: true,
    steps: [{ stepId: "warmup", name: "Warmup", durationSeconds: 60 }],
    ...overrides
  };
}

{
  const api = loadAppTestApi();
  reset(api);
  const archived = api.normalizeRoutineTemplateData(routinePatch({ lifecycle: "archived", timerVisible: true }));
  assert.equal(archived.lifecycle, "archived");
  assert.equal(archived.timerVisible, false);
  assert.equal(archived.needsTimer, false);
  const walk = api.normalizeRoutineTemplateData(routinePatch({ trainingType: "stretch" }));
  assert.equal(walk.scene, "walk");
  const misclassifiedWarmup = api.normalizeRoutineTemplateData(routinePatch({ title: "健走前热身", trainingType: "recovery", scene: "recovery" }));
  assert.equal(misclassifiedWarmup.trainingType, "warmup");
  assert.equal(misclassifiedWarmup.scene, "walk");
  const recoveryStretch = api.normalizeRoutineTemplateData(routinePatch({ title: "恢复拉伸", trainingType: "stretch", scene: "walk" }));
  assert.equal(recoveryStretch.scene, "recovery");
  const seated = api.normalizeRoutineTemplateData(routinePatch({ title: "座位活动", trainingType: "seat_recovery", scene: "recovery" }));
  assert.equal(seated.scene, "travel");
}

{
  const api = loadAppTestApi();
  reset(api);
  upsert(api, "daily_plan_items", {
    id: "daily_keep",
    date: "2099-01-03",
    trainingType: "easy_walk",
    title: "Easy Walk",
    estimatedMinutes: 45
  });
  upsert(api, "plan_adjustments", {
    id: "adjust_keep",
    date: "2099-01-03",
    reason: "keep",
    fromSnapshot: { trainingType: "easy_walk" },
    toSnapshot: { trainingType: "recovery" }
  });
  upsert(api, "routine_templates", routinePatch({ title: "Old Strength" }));

  const patch = {
    schema: "coach_plan_patch",
    effectiveFrom: "2099-01-01",
    planTemplates: [],
    dailyPlanItems: [],
    planAdjustments: [],
    routineTemplates: [routinePatch({ title: "New Strength" })]
  };
  const preview = api.previewPlanPatch(patch);
  assert.equal(preview.valid, true);
  assert.equal(preview.dailyPreviewCounts.delete, 0);
  assert.equal(preview.adjustmentPreview.delete, 0);
  assert.equal(preview.routinePreview.update, 1);

  const result = api.applyCoachPlanPatch(patch);
  assert.equal(result.deleted, 0);
  assert.equal(api.state.records.daily_plan_items.filter((item) => !item.deletedAt).length, 1);
  assert.equal(api.state.records.plan_adjustments.filter((item) => !item.deletedAt).length, 1);
  assert.equal(record(api, "routine_templates", "routine_strength_a").data.title, "New Strength");
}

{
  const api = loadAppTestApi();
  reset(api);
  const preview = api.previewPlanPatch({
    schema: "coach_plan_patch",
    contractVersion: "9.9",
    effectiveFrom: "2099-01-01",
    routineTemplates: [routinePatch({ id: "routine_bad_contract" })]
  });
  assert.equal(preview.valid, false);
  assert.match(preview.warnings.join("\n"), /contractVersion/);
}

{
  const api = loadAppTestApi();
  reset(api);
  upsert(api, "daily_plan_items", {
    id: "daily_replace_guard",
    date: "2099-02-01",
    trainingType: "recovery",
    title: "Recovery"
  });

  const patch = {
    schema: "coach_plan_patch",
    effectiveFrom: "2099-02-01",
    replaceMode: true,
    routineTemplates: [routinePatch({ id: "routine_recovery_a", title: "Recovery Routine", trainingType: "recovery" })],
    dailyPlanItems: []
  };
  const preview = api.previewPlanPatch(patch);
  assert.equal(preview.valid, true);
  assert.equal(preview.deleteCount, 0, "replaceMode must not create implicit deletes");
  assert.match(preview.warnings.join("\n"), /不会改变日历格/);
  api.applyCoachPlanPatch(patch);
  assert.equal(record(api, "daily_plan_items", "daily_replace_guard").deletedAt, null);
}

{
  const api = loadAppTestApi();
  reset(api);
  upsert(api, "daily_plan_items", {
    id: "daily_delete_me",
    date: "2099-03-01",
    trainingType: "easy_walk",
    title: "Easy Walk"
  });

  const patch = {
    schema: "coach_plan_patch",
    effectiveFrom: "2099-03-01",
    dailyPlanItems: [{ id: "daily_delete_me", operation: "delete" }]
  };
  const preview = api.previewPlanPatch(patch);
  assert.equal(preview.valid, true);
  assert.equal(preview.deleteCount, 1);
  const result = api.applyCoachPlanPatch(patch);
  assert.equal(result.deleted, 1);
  assert.ok(record(api, "daily_plan_items", "daily_delete_me").deletedAt);
}

{
  const api = loadAppTestApi();
  reset(api);
  api.state.workouts = [{ id: "workout_existing", date: "2099-04-01", type: "easyWalk", status: "completed" }];
  const patch = {
    schema: "coach_plan_patch",
    effectiveFrom: "2099-04-01",
    dailyPlanItems: [{
      id: "daily_skip_actual",
      date: "2099-04-01",
      trainingType: "strength",
      title: "Strength"
    }]
  };
  const preview = api.previewPlanPatch(patch);
  assert.equal(preview.valid, true);
  assert.equal(preview.dailyPreviewCounts.skipped, 1);
  const result = api.applyCoachPlanPatch(patch);
  assert.equal(result.skipped, 1);
  assert.equal(record(api, "daily_plan_items", "daily_skip_actual"), null);
}

{
  const api = loadAppTestApi();
  reset(api);
  const patch = {
    schema: "coach_plan_patch",
    effectiveFrom: "2099-05-01",
    routineTemplates: [{ title: "Missing ID", steps: [{ stepId: "x", durationSeconds: 60 }] }]
  };
  const preview = api.previewPlanPatch(patch);
  assert.equal(preview.valid, false);
  assert.equal(preview.routinePreview.invalid, 1);
}

{
  const api = loadAppTestApi();
  reset(api);
  const patch = {
    schema: "coach_plan_patch",
    effectiveFrom: "2099-05-02",
    routineTemplates: [routinePatch({
      id: "routine_execution_recovery",
      title: "Execution Recovery",
      trainingType: "recovery",
      steps: [{
        stepId: "stretch_calf_straight",
        name: "小腿直膝拉伸",
        phase: "stretch",
        durationSeconds: 30,
        dose: "每侧30秒",
        execution: {
          mode: "bilateral_hold",
          prepare_seconds: 8,
          sideSeconds: 30,
          switchSeconds: 6,
          sides: ["左侧", "右侧"]
        }
      }]
    })]
  };
  const preview = api.previewPlanPatch(patch);
  assert.equal(preview.valid, true);
  assert.equal(preview.routinePreview.add, 1);
  api.applyCoachPlanPatch(patch);
  const stored = record(api, "routine_templates", "routine_execution_recovery").data.steps[0];
  assert.equal(stored.execution.mode, "bilateral_hold");
  assert.equal(stored.execution.prepareSeconds, 8);
  assert.equal(stored.execution.sideSeconds, 30);
  assert.equal(stored.execution.switchSeconds, 6);
  assert.deepEqual(stored.execution.sides, ["左侧", "右侧"]);
  assert.equal(stored.execution.prepare_seconds, undefined);
}

{
  const api = loadAppTestApi();
  reset(api);
  const patch = {
    schema: "coach_plan_patch",
    effectiveFrom: "2099-05-03",
    routineTemplates: [routinePatch({
      id: "routine_bad_execution",
      steps: [{
        stepId: "x",
        durationSeconds: 60,
        execution: { mode: "split_everything", prepareSeconds: 5 }
      }]
    })]
  };
  const preview = api.previewPlanPatch(patch);
  assert.equal(preview.valid, false);
  assert.match(preview.warnings.join("\n"), /split_everything/);
}

{
  const api = loadAppTestApi();
  reset(api);
  const patch = {
    schema: "coach_plan_patch",
    effectiveFrom: "2099-06-01",
    dailyPlanItems: [{
      id: "daily_missing_routine_id",
      date: "2099-06-01",
      trainingType: "recovery",
      title: "Recovery",
      needsTimer: true
    }]
  };
  const preview = api.previewPlanPatch(patch);
  assert.equal(preview.valid, false);
  assert.equal(preview.dailyPreviewCounts.invalid, 1);
  assert.match(preview.warnings.join("\n"), /缺少 routineId/);
}

{
  const api = loadAppTestApi();
  reset(api);
  const patch = {
    schema: "coach_plan_patch",
    effectiveFrom: "2099-06-02",
    dailyPlanItems: [{
      id: "daily_unknown_routine",
      date: "2099-06-02",
      trainingType: "strength",
      title: "Strength",
      routineId: "routine_not_found"
    }]
  };
  const preview = api.previewPlanPatch(patch);
  assert.equal(preview.valid, false);
  assert.equal(preview.dailyPreviewCounts.invalid, 1);
  assert.match(preview.warnings.join("\n"), /routine_not_found/);
}

{
  const api = loadAppTestApi();
  reset(api);
  upsert(api, "routine_templates", routinePatch({ id: "routine_existing_strength", title: "Existing Strength" }));
  const patch = {
    schema: "coach_plan_patch",
    effectiveFrom: "2099-06-03",
    dailyPlanItems: [{
      id: "daily_existing_routine",
      date: "2099-06-03",
      trainingType: "strength",
      title: "Strength",
      routineId: "routine_existing_strength"
    }]
  };
  const preview = api.previewPlanPatch(patch);
  assert.equal(preview.valid, true);
  assert.equal(preview.dailyPreviewCounts.add, 1);
}

{
  const api = loadAppTestApi();
  reset(api);
  const patch = {
    schema: "coach_plan_patch",
    effectiveFrom: "2099-07-01",
    effectiveTo: "2099-07-10",
    planAdjustments: [{
      date: "2099-07-01",
      title: "低压恢复",
      trainingType: "recovery",
      estimatedMinutes: 15,
      status: "planned",
      reason: "主动降负荷。",
      notes: "做恢复拉伸或完全休息。"
    }]
  };
  const preview = api.previewPlanPatch(patch);
  assert.equal(preview.valid, true);
  assert.equal(preview.adjustmentPreview.add, 1);
  assert.match(preview.warnings.join("\n"), /覆盖 2099-07-01 至 2099-07-01/);
  assert.match(preview.warnings.join("\n"), /只到 2099-07-01/);
  const result = api.applyCoachPlanPatch(patch);
  assert.equal(result.calendarAdded, 1);
  const stored = api.state.records.plan_adjustments.find((item) => !item.deletedAt)?.data;
  assert.equal(stored.toSnapshot.title, "低压恢复");
  assert.equal(stored.toSnapshot.trainingType, "recovery");
  assert.equal(stored.toSnapshot.estimatedMinutes, 15);
  assert.equal(JSON.stringify(stored.toSnapshot.notes), JSON.stringify(["做恢复拉伸或完全休息。"]));
}

{
  const api = loadAppTestApi();
  reset(api);
  const patch = {
    schema: "coach_plan_patch",
    effectiveFrom: "2099-08-01",
    planAdjustments: [
      { date: "2099-08-01", title: "Easy walk", trainingType: "easy_walk", reason: "First adjustment" },
      { date: "2099-08-01", title: "Recovery", trainingType: "recovery", reason: "Second adjustment" }
    ]
  };
  const result = api.applyCoachPlanPatch(patch);
  assert.equal(result.added, 2);
  const records = api.state.records.plan_adjustments.filter((item) => !item.deletedAt);
  assert.equal(records.length, 2);
  assert.notEqual(records[0].id, records[1].id);
}

{
  const api = loadAppTestApi();
  reset(api);
  upsert(api, "routine_templates", routinePatch({ id: "routine_published", lifecycle: "published" }));
  const preview = api.previewPlanPatch({
    schema: "coach_plan_patch",
    effectiveFrom: "2099-09-01",
    routineTemplates: [routinePatch({ id: "routine_published", title: "Changed" })]
  });
  assert.equal(preview.valid, false);
  assert.match(preview.warnings.join("\n"), /不可原地修改/);
}

console.log("coach-plan-patch tests passed");
