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
    findSharedRecordById,
    getEffectivePlanForDate,
    buildMonthCalendarEntries,
    getRollingRecommendation,
    makeVirtualWorkoutFromEffectivePlan,
    setRecommendationEngine(fn) {
      window.ShenkeRecommendationEngine.getRecommendation = fn;
    }
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

function addDays(date, days) {
  const value = new Date(`${date}T12:00:00`);
  value.setDate(value.getDate() + days);
  return `${value.getFullYear()}-${String(value.getMonth() + 1).padStart(2, "0")}-${String(value.getDate()).padStart(2, "0")}`;
}

function routinePatch(overrides = {}) {
  return {
    id: "routine_strength_a",
    title: "Strength A",
    trainingType: "strength",
    scene: "home",
    role: "main",
    lifecycle: "draft",
    timerVisible: true,
    calendarVisible: true,
    countsTowardTraining: true,
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
  const explicitWalk = api.normalizeRoutineTemplateData(routinePatch({ trainingType: "stretch", scene: "walk", role: "stretch" }));
  assert.equal(explicitWalk.scene, "walk");
  const mismatchedTitle = api.normalizeRoutineTemplateData(routinePatch({ title: "健走前热身", trainingType: "recovery", scene: "recovery", role: "recovery" }));
  assert.equal(mismatchedTitle.trainingType, "recovery");
  assert.equal(mismatchedTitle.scene, "recovery");
  const seated = api.normalizeRoutineTemplateData(routinePatch({ title: "座位活动", trainingType: "seat_recovery", scene: "travel", role: "auxiliary" }));
  assert.equal(seated.scene, "travel");
}

{
  const api = loadAppTestApi();
  reset(api);
  upsert(api, "plan_adjustments", {
    id: "adjust_old_import",
    date: "2099-08-03",
    reason: "Old import",
    adjustedAt: "2020-01-01T00:00:00.000Z",
    updatedAt: "2020-01-01T00:00:00.000Z",
    toSnapshot: {
      date: "2099-08-03",
      title: "Old recovery",
      trainingType: "recovery"
    }
  });
  api.applyCoachPlanPatch({
    schema: "coach_plan_patch",
    contractVersion: "2.0",
    effectiveFrom: "2099-08-03",
    planAdjustments: [{
      id: "adjust_new_import",
      date: "2099-08-03",
      reason: "New import",
      toSnapshot: {
        date: "2099-08-03",
        title: "Updated strength",
        trainingType: "strength",
        status: "planned"
      }
    }]
  });
  const effective = api.getEffectivePlanForDate("2099-08-03");
  assert.equal(effective.source, "adjustment");
  assert.equal(effective.data.title, "Updated strength");
  assert.equal(effective.data.trainingType, "strength");
  assert.equal(record(api, "plan_adjustments", "adjust_new_import").contractVersion, "2.0");
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
    contractVersion: "2.0",
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
  assert.equal(record(api, "routine_templates", "routine_strength_a").contractVersion, "2.0");
}

{
  const api = loadAppTestApi();
  reset(api);
  const preview = api.previewPlanPatch({
    schema: "coach_plan_patch",
    effectiveFrom: "2099-01-01",
    routineTemplates: [routinePatch({ id: "routine_missing_contract" })]
  });
  assert.equal(preview.valid, false);
  assert.match(preview.warnings.join("\n"), /contractVersion/);
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
    contractVersion: "2.0",
    effectiveFrom: "2099-02-01",
    replaceMode: true,
    routineTemplates: [routinePatch({ id: "routine_recovery_a", title: "Recovery Routine", trainingType: "recovery" })],
    dailyPlanItems: []
  };
  const preview = api.previewPlanPatch(patch);
  assert.equal(preview.valid, false);
  assert.equal(preview.deleteCount, 0, "replaceMode must not create implicit deletes");
  assert.match(preview.warnings.join("\n"), /replaceMode/);
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
    contractVersion: "2.0",
    effectiveFrom: "2099-03-01",
    dailyPlanItems: [{ id: "daily_delete_me", operation: "delete" }]
  };
  const preview = api.previewPlanPatch(patch);
  assert.equal(preview.valid, true);
  assert.equal(preview.deleteCount, 1);
  const result = api.applyCoachPlanPatch(patch);
  assert.equal(result.deleted, 1);
  assert.ok(record(api, "daily_plan_items", "daily_delete_me").deletedAt);
  assert.equal(record(api, "daily_plan_items", "daily_delete_me").contractVersion, "2.0");
}

{
  const api = loadAppTestApi();
  reset(api);
  api.state.workouts = [{ id: "workout_existing", date: "2099-04-01", type: "easyWalk", status: "completed" }];
  const patch = {
    schema: "coach_plan_patch",
    contractVersion: "2.0",
    effectiveFrom: "2099-04-01",
    dailyPlanItems: [{
      id: "daily_skip_actual",
      date: "2099-04-01",
      trainingType: "strength",
      title: "Strength",
      status: "planned"
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
    contractVersion: "2.0",
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
    contractVersion: "2.0",
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
    contractVersion: "2.0",
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
    contractVersion: "2.0",
    effectiveFrom: "2099-06-01",
    dailyPlanItems: [{
      id: "daily_missing_routine_id",
      date: "2099-06-01",
      trainingType: "recovery",
      title: "Recovery",
      status: "planned",
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
    contractVersion: "2.0",
    effectiveFrom: "2099-06-02",
    dailyPlanItems: [{
      id: "daily_unknown_routine",
      date: "2099-06-02",
      trainingType: "strength",
      title: "Strength",
      status: "planned",
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
    contractVersion: "2.0",
    effectiveFrom: "2099-06-03",
    dailyPlanItems: [{
      id: "daily_existing_routine",
      date: "2099-06-03",
      trainingType: "strength",
      title: "Strength",
      status: "planned",
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
    contractVersion: "2.0",
    effectiveFrom: "2099-07-01",
    effectiveTo: "2099-07-10",
    planAdjustments: [{
      id: "adjust_low_pressure_2099_07_01",
      date: "2099-07-01",
      reason: "主动降负荷。",
      toSnapshot: {
        date: "2099-07-01",
        title: "低压恢复",
        trainingType: "recovery",
        estimatedMinutes: 15,
        status: "planned",
        notes: "做恢复拉伸或完全休息。"
      }
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
    contractVersion: "2.0",
    effectiveFrom: "2099-08-01",
    planAdjustments: [
      {
        id: "adjust_easy_walk_2099_08_01",
        date: "2099-08-01",
        reason: "First adjustment",
        toSnapshot: { date: "2099-08-01", title: "Easy walk", trainingType: "easy_walk", status: "planned" }
      },
      {
        id: "adjust_recovery_2099_08_01",
        date: "2099-08-01",
        reason: "Second adjustment",
        toSnapshot: { date: "2099-08-01", title: "Recovery", trainingType: "recovery", status: "planned" }
      }
    ]
  };
  const result = api.applyCoachPlanPatch(patch);
  assert.equal(result.added, 2);
  const records = api.state.records.plan_adjustments.filter((item) => !item.deletedAt);
  assert.equal(records.length, 2);
  assert.notEqual(records[0].id, records[1].id);
  const effective = api.getEffectivePlanForDate("2099-08-01");
  assert.equal(effective.source, "adjustment");
  assert.equal(effective.data.title, "Recovery");
  assert.equal(effective.data.trainingType, "recovery");
  const calendarEntry = api.buildMonthCalendarEntries(2099, 8).get("2099-08-01");
  assert.equal(calendarEntry.kind, "adjustment");
  assert.equal(calendarEntry.type, "recovery");
  assert.match(calendarEntry.text, /Recovery/);
}

{
  const api = loadAppTestApi();
  reset(api);
  upsert(api, "daily_plan_items", {
    id: "daily_old_plan",
    date: "2099-08-02",
    title: "Old walk",
    trainingType: "easy_walk",
    updatedAt: "2099-01-01T00:00:00.000Z"
  });
  upsert(api, "daily_plan_items", {
    id: "daily_new_plan",
    date: "2099-08-02",
    title: "New strength",
    trainingType: "strength",
    updatedAt: "2099-02-01T00:00:00.000Z"
  });
  const effective = api.getEffectivePlanForDate("2099-08-02");
  assert.equal(effective.source, "plan");
  assert.equal(effective.data.title, "New strength");
  const calendarEntry = api.buildMonthCalendarEntries(2099, 8).get("2099-08-02");
  assert.equal(calendarEntry.kind, "plan");
  assert.equal(calendarEntry.type, "strength");
  assert.match(calendarEntry.text, /New strength/);
}

{
  const api = loadAppTestApi();
  reset(api);
  const today = new Date();
  const start = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, "0")}-${String(today.getDate()).padStart(2, "0")}`;
  const lastPlanDate = addDays(start, 6);
  const suggestionDate = addDays(start, 7);

  for (let index = 0; index < 7; index += 1) {
    const date = addDays(start, index);
    upsert(api, "daily_plan_items", {
      id: `daily_plan_${date}`,
      date,
      title: index === 6 ? "Strength" : "Easy walk",
      trainingType: index === 6 ? "strength" : "easy_walk",
      estimatedMinutes: index === 6 ? 47 : 35,
      status: "planned"
    });
  }

  api.setRecommendationEngine((date, workouts) => {
    const yesterday = addDays(date, -1);
    const previous = workouts.find((item) => item.date === yesterday);
    const type = previous?.type === "strength" ? "easyWalk" : "strength";
    return { type, label: "Test", title: type, minutes: 35, reasons: [], sourceLabel: "Test" };
  });

  const [year, month] = suggestionDate.split("-").map(Number);
  const entry = api.buildMonthCalendarEntries(year, month).get(suggestionDate);
  assert.equal(entry.kind, "forecast");
  assert.equal(entry.type, "easyWalk", "fallback suggestions should continue from the imported formal-plan rhythm");
  assert.equal(api.getRollingRecommendation(suggestionDate).type, "easyWalk", "date detail should use the same plan-aware forecast timeline");

  const virtualPlan = api.makeVirtualWorkoutFromEffectivePlan(lastPlanDate, api.getEffectivePlanForDate(lastPlanDate));
  assert.equal(virtualPlan.type, "strength");
  assert.equal(virtualPlan.source, "forecast");
  assert.equal(virtualPlan.forecastBasis, "formalPlan");
}

{
  const api = loadAppTestApi();
  reset(api);
  upsert(api, "routine_templates", routinePatch({ id: "routine_published", lifecycle: "published" }));
  const preview = api.previewPlanPatch({
    schema: "coach_plan_patch",
    contractVersion: "2.0",
    effectiveFrom: "2099-09-01",
    routineTemplates: [routinePatch({ id: "routine_published", title: "Changed" })]
  });
  assert.equal(preview.valid, false);
  assert.match(preview.warnings.join("\n"), /不可原地修改/);
}

{
  const api = loadAppTestApi();
  reset(api);
  const missingRole = api.previewPlanPatch({
    schema: "coach_plan_patch",
    contractVersion: "2.0",
    effectiveFrom: "2099-10-01",
    routineTemplates: [routinePatch({ id: "routine_missing_role", role: undefined })]
  });
  assert.equal(missingRole.valid, false);
  assert.match(missingRole.warnings.join("\n"), /role 必须显式填写/);

  const legacyLifecycle = api.previewPlanPatch({
    schema: "coach_plan_patch",
    contractVersion: "2.0",
    effectiveFrom: "2099-10-01",
    routineTemplates: [routinePatch({ id: "routine_legacy_active", lifecycle: "active" })]
  });
  assert.equal(legacyLifecycle.valid, false);
  assert.match(legacyLifecycle.warnings.join("\n"), /active 是旧值/);

  const missingVisibility = api.previewPlanPatch({
    schema: "coach_plan_patch",
    contractVersion: "2.0",
    effectiveFrom: "2099-10-01",
    routineTemplates: [routinePatch({ id: "routine_missing_visibility", calendarVisible: undefined })]
  });
  assert.equal(missingVisibility.valid, false);
  assert.match(missingVisibility.warnings.join("\n"), /calendarVisible 必须显式填写/);
}

console.log("coach-plan-patch tests passed");
