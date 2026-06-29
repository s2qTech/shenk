"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");

function loadAppTestApi() {
  const appPath = path.join(__dirname, "..", "src", "app.js");
  const source = fs.readFileSync(appPath, "utf8");
  const hook = `
  globalThis.__shenkeIntegrationTest = {
    state,
    createEmptySharedRecords,
    upsertSharedEnvelope,
    applyCoachPlanPatch,
    buildTimerUrl,
    openTimerSessionTrainingDraft,
    getDirtySharedRecords,
    timerSessionToWorkout,
    workoutToTrainingLogData,
    getTimerSessionHandling,
    renderSelectedSummary,
    refreshLegacyCachesFromSharedRecords,
    normalizeDailyPlanItemData,
    normalizeRoutineTemplateData
  };
`;
  const instrumented = source.replace(/\}\)\(\);\s*$/, `${hook}\n})();`);
  const storage = new Map();
  const element = makeElement();
  const URLCtor = URL;
  URLCtor.createObjectURL = () => "";
  URLCtor.revokeObjectURL = () => {};
  const context = {
    console,
    setTimeout,
    clearTimeout,
    Blob: function Blob() {},
    URL: URLCtor,
    FileReader: function FileReader() {},
    window: {
      location: { href: "https://s2qtech.github.io/shenk/", origin: "https://s2qtech.github.io" },
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
      createElement: () => makeElement(),
      querySelector: () => null,
      querySelectorAll: () => []
    },
    navigator: {},
    indexedDB: undefined
  };
  context.globalThis = context;
  vm.createContext(context);
  vm.runInContext(instrumented, context, { filename: appPath });
  return context.__shenkeIntegrationTest;
}

function makeElement() {
  return {
    innerHTML: "",
    textContent: "",
    value: "",
    className: "",
    dataset: {},
    classList: { add() {}, remove() {}, toggle() {} },
    addEventListener() {},
    appendChild() {},
    remove() {},
    click() {},
    querySelector() { return null; },
    querySelectorAll() { return []; },
    setAttribute() {},
    getAttribute() { return ""; }
  };
}

function reset(api) {
  api.state.records = api.createEmptySharedRecords();
  api.state.workouts = [];
  api.state.bodyMetrics = [];
  api.state.selectedDate = "2099-06-01";
  api.state.syncConfig = {
    ...api.state.syncConfig,
    apiBase: "https://example.workers.dev/api",
    timerUrl: "https://timer.example/app/",
    token: "",
    timerToken: ""
  };
}

function upsert(api, entity, data) {
  api.upsertSharedEnvelope(api.state.records, entity, data);
}

{
  const api = loadAppTestApi();
  reset(api);
  const patch = {
    schema: "coach_plan_patch",
    effectiveFrom: "2099-06-01",
    routineTemplates: [{
      id: "routine_quality_walk_test",
      title: "Quality Walk",
      trainingType: "quality_walk",
      timerVisible: true,
      steps: [{ stepId: "warmup", name: "Warmup", durationSeconds: 60 }]
    }],
    dailyPlanItems: [{
      id: "daily_quality_walk_test",
      date: "2099-06-01",
      trainingType: "quality_walk",
      title: "Quality Walk Plan",
      estimatedMinutes: 45,
      routineId: "routine_quality_walk_test",
      timerOptions: { restSeconds: 20 }
    }]
  };
  const result = api.applyCoachPlanPatch(patch);
  assert.equal(result.added, 2);

  const plan = api.state.records.daily_plan_items.find((item) => item.id === "daily_quality_walk_test").data;
  assert.equal(plan.trainingType, "quality_walk");
  assert.equal(plan.routineId, "routine_quality_walk_test");
  assert.equal(plan.estimatedMinutes, 45);

  const timerUrl = new URL(api.buildTimerUrl(plan));
  assert.equal(timerUrl.origin + timerUrl.pathname, "https://timer.example/app/");
  assert.equal(timerUrl.searchParams.get("routineId"), "routine_quality_walk_test");
  assert.equal(timerUrl.searchParams.get("date"), "2099-06-01");
  assert.equal(timerUrl.searchParams.get("dailyPlanItemId"), "daily_quality_walk_test");
  assert.equal(timerUrl.searchParams.get("source"), "shenk");
  assert.equal(timerUrl.searchParams.get("cloudApiBase"), "https://example.workers.dev/api");
  assert.equal(timerUrl.searchParams.has("token"), false);
  assert.equal(timerUrl.searchParams.has("timerToken"), false);
}

{
  const api = loadAppTestApi();
  reset(api);
  upsert(api, "daily_plan_items", {
    id: "daily_hidden_when_actual",
    date: "2099-06-02",
    trainingType: "easy_walk",
    title: "Planned Walk",
    estimatedMinutes: 45
  });
  upsert(api, "training_logs", {
    id: "log_actual_walk",
    date: "2099-06-02",
    type: "easy_walk",
    status: "completed",
    source: "manual",
    durationSec: 3300,
    distanceKm: 5.2,
    notes: "Actual Walk"
  });
  api.refreshLegacyCachesFromSharedRecords();
  api.state.selectedDate = "2099-06-02";
  const summary = api.renderSelectedSummary();
  assert.match(summary, /Actual Walk/);
  assert.doesNotMatch(summary, /Planned Walk/);
}

{
  const api = loadAppTestApi();
  reset(api);
  upsert(api, "daily_plan_items", {
    id: "daily_adjusted_plan",
    date: "2099-06-03",
    trainingType: "quality_walk",
    title: "原始提高走",
    estimatedMinutes: 50,
    routineId: "routine_quality_walk_test"
  });
  upsert(api, "plan_adjustments", {
    id: "adjust_daily_plan",
    date: "2099-06-03",
    targetDailyPlanItemId: "daily_adjusted_plan",
    adjustedBy: "coach",
    adjustedAt: "2099-06-03T09:00:00.000Z",
    reason: "当天降级。",
    toSnapshot: {
      trainingType: "easy_walk",
      title: "调整普通走",
      estimatedMinutes: 45,
      routineId: "routine_easy_walk_test"
    }
  });
  api.state.selectedDate = "2099-06-03";
  const summary = api.renderSelectedSummary();
  assert.match(summary, /训练安排/);
  assert.match(summary, /调整普通走/);
  assert.doesNotMatch(summary, /原计划参考/);
  assert.doesNotMatch(summary, /原始提高走/);
  assert.doesNotMatch(summary, /当天降级/);
  assert.doesNotMatch(summary, /coach/);
}

{
  const api = loadAppTestApi();
  reset(api);
  upsert(api, "timer_sessions", {
    id: "session_shenk_must_not_push",
    date: "2099-06-05",
    routineTitle: "Strength",
    trainingType: "strength",
    completion: "completed",
    actualSeconds: 1800,
    startedAt: "2099-06-05T10:00:00.000Z"
  });
  upsert(api, "training_logs", {
    id: "log_shenk_can_push",
    date: "2099-06-05",
    type: "strength",
    status: "completed",
    source: "manual",
    durationSec: 1800
  });
  const dirty = api.getDirtySharedRecords();
  assert.equal(dirty.some((item) => item.entity === "timer_sessions"), false);
  assert.equal(dirty.some((item) => item.entity === "training_logs" && item.id === "log_shenk_can_push"), true);
}

{
  const api = loadAppTestApi();
  reset(api);
  const session = {
    id: "session_strength_draft",
    date: "2099-06-04",
    dailyPlanItemId: "daily_strength_draft",
    routineId: "routine_strength_001",
    routineTitle: "Strength",
    trainingType: "strength",
    completion: "completed",
    actualSeconds: 2760,
    startedAt: "2099-06-04T10:00:00.000Z"
  };
  upsert(api, "timer_sessions", session);

  api.openTimerSessionTrainingDraft({ dataset: { sessionId: "session_strength_draft" } });

  assert.equal(api.state.records.training_logs.length, 0);
  assert.equal(api.state.activeTab, "calendar");
  assert.equal(api.state.detailOpen, true);
  assert.equal(api.state.editMode, true);
  assert.equal(api.state.editorDrafts.date, "2099-06-04");
  assert.equal(api.state.editorDrafts.training.timerSessionId, "session_strength_draft");
  assert.equal(api.state.editorDrafts.training.dailyPlanItemId, "daily_strength_draft");
  assert.equal(api.state.editorDrafts.training.source, "timer");
  assert.equal(api.state.editorSections.training, true);
  assert.equal(api.state.editorSections.status, false);
}

{
  const api = loadAppTestApi();
  reset(api);
  const session = {
    id: "session_strength_001",
    date: "2099-06-03",
    dailyPlanItemId: "daily_strength_001",
    routineId: "routine_strength_001",
    routineTitle: "Strength",
    trainingType: "strength",
    completion: "completed",
    actualSeconds: 2880,
    startedAt: "2099-06-03T10:00:00.000Z"
  };
  const workout = api.timerSessionToWorkout(session);
  assert.equal(workout.type, "strength");
  assert.equal(workout.source, "timer");
  assert.equal(workout.timerSessionId, "session_strength_001");
  assert.equal(JSON.stringify(workout.timerSessionIds), JSON.stringify(["session_strength_001"]));
  assert.equal(workout.durationSec, 2880);
  assert.equal(workout.dailyPlanItemId, "daily_strength_001");

  const trainingLog = api.workoutToTrainingLogData(workout);
  assert.equal(trainingLog.source, "timer");
  assert.equal(trainingLog.timerSessionId, "session_strength_001");
  assert.equal(JSON.stringify(trainingLog.timerSessionIds), JSON.stringify(["session_strength_001"]));
  assert.equal(trainingLog.rawJson.timerSession.id, "session_strength_001");

  upsert(api, "timer_sessions", session);
  upsert(api, "training_logs", trainingLog);
  api.refreshLegacyCachesFromSharedRecords();
  const timerEnvelope = api.state.records.timer_sessions.find((item) => item.id === "session_strength_001");
  const handling = api.getTimerSessionHandling(timerEnvelope);
  assert.equal(handling.action, "converted");
  assert.equal(handling.targetTrainingLogId, trainingLog.id);
}

console.log("integration-flow tests passed");
