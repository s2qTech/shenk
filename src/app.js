(() => {
  "use strict";

  const DB_NAME = "training-assistant-v2";
  const DB_VERSION = 1;
  const SNAPSHOT_KEY = "snapshot";
  const FALLBACK_KEY = "training-assistant-v2:snapshot";
  const DEVICE_KEY = "training-assistant-v2:device-id";
  const SYNC_CONFIG_KEY = "training-assistant-v2:sync-config";
  const DEFAULT_CLOUD_API_BASE = "https://shenke-cloud-db.sq-muyi.workers.dev/api";
  const DEFAULT_TIMER_URL = "https://s2qtech.github.io/home-training-timer/";
  const SHARED_SCHEMA_VERSION = "2026-06-19-001";
  const SHARED_ENTITIES = [
    "plan_templates",
    "routine_templates",
    "daily_plan_items",
    "plan_adjustments",
    "timer_sessions",
    "timer_session_links",
    "training_logs",
    "body_metrics",
    "weather_logs",
    "media_assets",
    "feedback_summaries"
  ];
  const SHENK_WRITE_ENTITIES = [
    "plan_templates",
    "routine_templates",
    "daily_plan_items",
    "plan_adjustments",
    "timer_session_links",
    "training_logs",
    "body_metrics",
    "weather_logs",
    "media_assets",
    "feedback_summaries"
  ];
  const LEGACY_TO_SHARED_TYPE = {
    strength: "strength",
    easyWalk: "easy_walk",
    qualityWalk: "quality_walk",
    indoorCardio: "indoor_cardio",
    recovery: "recovery",
    rest: "rest"
  };
  const SHARED_TO_LEGACY_TYPE = {
    strength: "strength",
    easy_walk: "easyWalk",
    quality_walk: "qualityWalk",
    indoor_cardio: "indoorCardio",
    warmup: "recovery",
    cooldown: "recovery",
    travel_strength: "strength",
    seat_recovery: "recovery",
    recovery: "recovery",
    stretch: "recovery",
    rest: "rest"
  };
  const LEGACY_TO_SHARED_STATUS = {
    completed: "completed",
    short: "short_version",
    stretchOnly: "stretch_only",
    skipped: "skipped"
  };
  const SHARED_TO_LEGACY_STATUS = {
    planned: "completed",
    completed: "completed",
    short_version: "short",
    stretch_only: "stretchOnly",
    skipped: "skipped",
    rested: "skipped",
    modified_by_user: "short"
  };
  const LEGACY_FATIGUE_TO_SHARED = {
    low: 1,
    normal: 2,
    high: 3,
    severe: 4
  };
  const SHARED_FATIGUE_TO_LEGACY = {
    1: "low",
    2: "normal",
    3: "high",
    4: "severe"
  };

  const TYPE_META = {
    strength: { label: "力量", short: "力", icon: "◆", asset: "assets/sports/strength.png", className: "type-strength" },
    easyWalk: { label: "普通走", short: "走", icon: "→", asset: "assets/sports/walk.png", className: "type-easyWalk" },
    qualityWalk: { label: "提高走", short: "提", icon: "↗", asset: "assets/sports/run.png", className: "type-qualityWalk" },
    indoorCardio: { label: "室内有氧", short: "室", icon: "⌂", asset: "assets/sports/treadmill.png", className: "type-indoorCardio" },
    recovery: { label: "恢复", short: "恢", icon: "↺", asset: "assets/sports/stretch.png", className: "type-recovery" },
    rest: { label: "休息", short: "休", icon: "○", asset: "assets/sports/rest.png", className: "type-rest" }
  };

  const APP_ICON_META = {
    calendar: { label: "日历", asset: "assets/app/calendar.png" },
    timer: { label: "计时器记录", asset: "assets/app/list.png" },
    data: { label: "数据", asset: "assets/app/notebook.png" },
    settings: { label: "设置", asset: "assets/app/setting.png" }
  };

  const TIMER_TYPE_META = {
    warmup: { label: "热身", legacyType: "recovery", defaultRole: "warmup", canConvert: false },
    stretch: { label: "拉伸", legacyType: "recovery", defaultRole: "stretch", canConvert: false },
    cooldown: { label: "冷身", legacyType: "recovery", defaultRole: "cooldown", canConvert: false },
    recovery: { label: "恢复", legacyType: "recovery", defaultRole: "recovery", canConvert: true },
    seat_recovery: { label: "坐姿恢复", legacyType: "recovery", defaultRole: "recovery", canConvert: false },
    strength: { label: "力量", legacyType: "strength", defaultRole: "main", canConvert: true },
    travel_strength: { label: "出差力量", legacyType: "strength", defaultRole: "main", canConvert: true },
    indoor_cardio: { label: "室内有氧", legacyType: "indoorCardio", defaultRole: "main", canConvert: true },
    easy_walk: { label: "普通走", legacyType: "easyWalk", defaultRole: "main", canConvert: true },
    quality_walk: { label: "提高走", legacyType: "qualityWalk", defaultRole: "main", canConvert: true },
    rest: { label: "休息", legacyType: "rest", defaultRole: "note", canConvert: false }
  };

  const TIMER_LINK_ACTION_META = {
    pending: "未处理",
    linked: "已关联",
    converted: "已转日志",
    ignored: "已忽略"
  };

  const TIMER_LINK_ROLE_META = {
    warmup: "热身",
    stretch: "拉伸",
    cooldown: "冷身",
    main: "主训练",
    recovery: "恢复",
    note: "备注"
  };

  const BRAND_META = {
    name: "身刻",
    slogan: "记录身体变化，掌控生活节奏",
    symbolDark: "brand-assets/shinke-symbol-dark.png",
    symbolLight: "brand-assets/shinke-symbol-light.png",
    logoDark: "brand-assets/shinke-logo-horizontal-dark.png"
  };

  const STATUS_META = {
    completed: "完成",
    short: "短版",
    stretchOnly: "只拉伸",
    skipped: "未做"
  };

  const STATUS_OPTIONS_BY_TYPE = {
    strength: {
      completed: "完成",
      short: "短版",
      skipped: "未做"
    },
    easyWalk: {
      completed: "完成",
      short: "缩短",
      skipped: "未做"
    },
    qualityWalk: {
      completed: "完成",
      short: "缩短",
      skipped: "未做"
    },
    indoorCardio: {
      completed: "完成",
      short: "缩短",
      skipped: "未做"
    },
    recovery: {
      completed: "完成",
      stretchOnly: "只拉伸",
      skipped: "未做"
    },
    rest: {
      skipped: "已休息"
    }
  };

  const FATIGUE_META = {
    low: "轻松",
    normal: "正常",
    high: "累",
    severe: "很累"
  };

  const FATIGUE_ORDER = ["low", "normal", "high", "severe"];
  const FATIGUE_SLIDER_ORDER = ["severe", "high", "normal", "low"];

  const SLEEP_META = {
    poor: "差",
    normal: "一般",
    good: "好"
  };

  const SLEEP_ORDER = ["poor", "normal", "good"];

  const PAIN_LEVELS = [
    { value: 0, label: "无" },
    { value: 1, label: "轻微" },
    { value: 2, label: "明显" },
    { value: 3, label: "严重" }
  ];

  const SEED_WORKOUTS = [];

  const app = document.getElementById("app");
  let db = null;
  let messageTimer = null;

  const state = {
    ready: false,
    storageMode: "载入中",
    selectedDate: todayISO(),
    visibleMonth: todayISO().slice(0, 7),
    activeTab: "calendar",
    detailOpen: false,
    editMode: false,
    message: "",
    timerFilters: {
      date: "",
      type: "all",
      status: "all",
      selectedSessionId: ""
    },
    dataView: "summary",
    editorDrafts: null,
    workouts: [],
    bodyMetrics: [],
    records: createEmptySharedRecords(),
    syncConfig: loadSyncConfig(),
    syncStatus: {
      busy: false,
      lastResult: "",
      lastError: ""
    }
  };

  document.addEventListener("DOMContentLoaded", init);

  async function init() {
    render();
    const snapshot = normalizeSnapshot(await loadSnapshot());
    state.workouts = normalizeWorkouts(snapshot.workouts);
    state.bodyMetrics = normalizeBodyMetrics(snapshot.bodyMetrics);
    state.records = normalizeSharedRecords(snapshot.records);
    if (!state.workouts.length) {
      state.workouts = seedWorkouts();
      await saveSnapshot("已载入历史种子记录");
    }
    state.ready = true;
    render();
  }

  async function loadSnapshot() {
    try {
      db = await openDatabase();
      state.storageMode = "IndexedDB";
      const idbSnapshot = await idbGet(SNAPSHOT_KEY);
      if (idbSnapshot) return idbSnapshot;
    } catch (error) {
      db = null;
      state.storageMode = "localStorage";
    }

    try {
      const raw = window.localStorage.getItem(FALLBACK_KEY);
      if (raw) return JSON.parse(raw);
    } catch (error) {
      state.message = "本地缓存读取失败，已使用种子数据";
    }

    return { schemaVersion: SHARED_SCHEMA_VERSION, workouts: seedWorkouts(), bodyMetrics: [], records: createEmptySharedRecords() };
  }

  function openDatabase() {
    return new Promise((resolve, reject) => {
      if (!("indexedDB" in window)) {
        reject(new Error("IndexedDB unavailable"));
        return;
      }
      const request = window.indexedDB.open(DB_NAME, DB_VERSION);
      request.onupgradeneeded = () => {
        const nextDb = request.result;
        if (!nextDb.objectStoreNames.contains("kv")) {
          nextDb.createObjectStore("kv");
        }
      };
      request.onsuccess = () => resolve(request.result);
      request.onerror = () => reject(request.error || new Error("IndexedDB open failed"));
    });
  }

  function idbGet(key) {
    return new Promise((resolve, reject) => {
      const tx = db.transaction("kv", "readonly");
      const request = tx.objectStore("kv").get(key);
      request.onsuccess = () => resolve(request.result || null);
      request.onerror = () => reject(request.error || new Error("IndexedDB read failed"));
    });
  }

  function idbSet(key, value) {
    return new Promise((resolve, reject) => {
      const tx = db.transaction("kv", "readwrite");
      tx.objectStore("kv").put(value, key);
      tx.oncomplete = () => resolve();
      tx.onerror = () => reject(tx.error || new Error("IndexedDB write failed"));
    });
  }

  async function saveSnapshot(message) {
    const snapshot = buildSnapshot();

    try {
      if (db) {
        await idbSet(SNAPSHOT_KEY, snapshot);
        state.storageMode = "IndexedDB";
      } else {
        window.localStorage.setItem(FALLBACK_KEY, JSON.stringify(snapshot));
        state.storageMode = "localStorage";
      }
      if (message) state.message = message;
    } catch (error) {
      window.localStorage.setItem(FALLBACK_KEY, JSON.stringify(snapshot));
      state.storageMode = "localStorage";
      state.message = message || "已保存到 localStorage";
    }
  }

  function buildSnapshot() {
    syncSharedRecordsFromLegacy();
    return {
      schemaVersion: SHARED_SCHEMA_VERSION,
      legacySchemaVersion: 1,
      updatedAt: new Date().toISOString(),
      workouts: state.workouts,
      bodyMetrics: state.bodyMetrics,
      records: cloneJson(state.records)
    };
  }

  function normalizeSnapshot(payload) {
    const source = payload && typeof payload === "object" ? payload : {};
    const records = normalizeSharedRecords(collectSharedRecordSource(source));
    let workouts = normalizeWorkouts(source.workouts);
    let bodyMetrics = normalizeBodyMetrics(source.bodyMetrics);

    if (!workouts.length && records.training_logs.length) {
      workouts = normalizeWorkouts(records.training_logs.map(trainingLogEnvelopeToWorkout).filter(Boolean));
    }
    if (!bodyMetrics.length && records.body_metrics.length) {
      bodyMetrics = normalizeBodyMetrics(records.body_metrics.map(bodyMetricEnvelopeToLegacy).filter(Boolean));
    }
    if (!records.training_logs.length && workouts.length) {
      records.training_logs = workouts.map((item) => workoutToTrainingLogEnvelope(item)).filter(Boolean);
    }
    if (!records.body_metrics.length && bodyMetrics.length) {
      records.body_metrics = bodyMetrics.map((item) => bodyMetricToSharedEnvelope(item)).filter(Boolean);
    }

    return { workouts, bodyMetrics, records };
  }

  function createEmptySharedRecords() {
    return SHARED_ENTITIES.reduce((records, entity) => {
      records[entity] = [];
      return records;
    }, {});
  }

  function collectSharedRecordSource(source) {
    const records = createEmptySharedRecords();
    const nested = source.records && typeof source.records === "object" ? source.records : {};
    SHARED_ENTITIES.forEach((entity) => {
      const value = Array.isArray(nested[entity]) ? nested[entity] : source[entity];
      records[entity] = Array.isArray(value) ? value : [];
    });
    return records;
  }

  function normalizeSharedRecords(source) {
    const records = createEmptySharedRecords();
    const input = source && typeof source === "object" ? source : {};
    SHARED_ENTITIES.forEach((entity) => {
      records[entity] = normalizeSharedRecordArray(entity, input[entity]);
    });
    return records;
  }

  function normalizeSharedRecordArray(entity, items) {
    if (!Array.isArray(items)) return [];
    return items.map((item) => normalizeSharedEnvelope(entity, item)).filter(Boolean).sort(compareSharedEnvelopes);
  }

  function normalizeSharedEnvelope(entity, item) {
    if (!item || typeof item !== "object") return null;
    const rawData = item.data && typeof item.data === "object" ? item.data : stripEnvelopeFields(item);
    const data = normalizeSharedEntityData(entity, rawData);
    if (!data) return null;
    const now = new Date().toISOString();
    const id = item.id || data.id || makeId();
    data.id = id;
    return {
      id,
      entity: item.entity || entity,
      data,
      revision: Math.max(1, Math.round(toNullableNumber(item.revision) || toNullableNumber(data.revision) || 1)),
      deviceId: item.deviceId || data.deviceId || getDeviceId(),
      createdAt: item.createdAt || data.createdAt || now,
      updatedAt: item.updatedAt || data.updatedAt || now,
      deletedAt: item.deletedAt || data.deletedAt || null,
      syncState: item.syncState || "dirty",
      lastSyncedAt: item.lastSyncedAt || null,
      conflict: item.conflict || null
    };
  }

  function stripEnvelopeFields(item) {
    const data = { ...item };
    ["entity", "revision", "deviceId", "syncState", "lastSyncedAt", "conflict"].forEach((key) => delete data[key]);
    return data;
  }

  function normalizeSharedEntityData(entity, data) {
    if (entity === "daily_plan_items") return normalizeDailyPlanItemData(data);
    if (entity === "timer_sessions") return normalizeTimerSessionData(data);
    if (entity === "timer_session_links") return normalizeTimerSessionLinkData(data);
    if (entity === "training_logs") return normalizeTrainingLogData(data);
    if (entity === "body_metrics") return normalizeBodyMetricData(data);
    if (entity === "plan_adjustments") return normalizePlanAdjustmentData(data);
    if (entity === "plan_templates") return normalizeLooseSharedData(data, "plan");
    if (entity === "routine_templates") return normalizeLooseSharedData(data, "routine");
    return normalizeLooseSharedData(data, entity);
  }

  function normalizeLooseSharedData(data, prefix) {
    if (!data || typeof data !== "object") return null;
    return {
      ...data,
      id: data.id || `${prefix}_${makeId()}`
    };
  }

  function normalizeDailyPlanItemData(data) {
    if (!data || !isIsoDate(data.date)) return null;
    const trainingType = toSharedTrainingType(data.trainingType || data.training_type || data.type);
    return {
      ...data,
      id: data.id || `daily_${data.date}_${trainingType}_001`,
      date: data.date,
      sourcePlanId: data.sourcePlanId || data.source_plan_id || null,
      sourcePlanVersion: data.sourcePlanVersion || data.source_plan_version || null,
      trainingType,
      title: data.title || TYPE_META[toLegacyTrainingType(trainingType)].label,
      goal: data.goal || "",
      estimatedMinutes: toNullableNumber(data.estimatedMinutes ?? data.estimated_minutes),
      intensity: clamp(Math.round(toNullableNumber(data.intensity) || 3), 1, 5),
      needsTimer: Boolean(data.needsTimer ?? data.needs_timer),
      routineId: data.routineId || data.routine_id || null,
      routineVersion: data.routineVersion || data.routine_version || null,
      timerOptions: data.timerOptions || data.timer_options || {},
      notes: Array.isArray(data.notes) ? data.notes : [],
      snapshot: data.snapshot || {},
      status: data.status || "planned",
      sortOrder: Math.round(toNullableNumber(data.sortOrder ?? data.sort_order) || 0),
      createdAt: data.createdAt || data.created_at || new Date().toISOString(),
      updatedAt: data.updatedAt || data.updated_at || new Date().toISOString()
    };
  }

  function normalizePlanAdjustmentData(data) {
    if (!data || !isIsoDate(data.date)) return null;
    return {
      ...data,
      id: data.id || `adjust_${data.date}_${makeId()}`,
      date: data.date,
      targetDailyPlanItemId: data.targetDailyPlanItemId || data.target_daily_plan_item_id || null,
      adjustedAt: data.adjustedAt || data.adjusted_at || new Date().toISOString(),
      adjustedBy: data.adjustedBy || data.adjusted_by || "coach",
      reason: data.reason || "",
      fromSnapshot: data.fromSnapshot || data.from_snapshot || {},
      toSnapshot: data.toSnapshot || data.to_snapshot || {},
      createdAt: data.createdAt || data.created_at || new Date().toISOString(),
      updatedAt: data.updatedAt || data.updated_at || new Date().toISOString()
    };
  }

  function normalizeTimerSessionData(data) {
    if (!data || typeof data !== "object") return null;
    const date = data.date || String(data.startedAt || data.started_at || "").slice(0, 10);
    if (!isIsoDate(date)) return null;
    return {
      ...data,
      id: data.id || `session_${date}_${makeId()}`,
      date,
      dailyPlanItemId: data.dailyPlanItemId || data.daily_plan_item_id || null,
      planTemplateId: data.planTemplateId || data.plan_template_id || null,
      routineId: data.routineId || data.routine_id || "",
      routineVersion: data.routineVersion || data.routine_version || null,
      routineTitle: data.routineTitle || data.routine_title || data.title || "",
      trainingType: normalizeTimerTrainingType(data.trainingType || data.training_type || data.type),
      startedAt: data.startedAt || data.started_at || new Date().toISOString(),
      endedAt: data.endedAt || data.ended_at || null,
      actualSeconds: toNullableNumber(data.actualSeconds ?? data.actual_seconds),
      completion: data.completion || "completed",
      stepResults: Array.isArray(data.stepResults) ? data.stepResults : Array.isArray(data.step_results) ? data.step_results : [],
      notes: data.notes || "",
      source: data.source || "home_training_timer",
      createdAt: data.createdAt || data.created_at || new Date().toISOString(),
      updatedAt: data.updatedAt || data.updated_at || new Date().toISOString()
    };
  }

  function normalizeTimerSessionLinkData(data) {
    if (!data || typeof data !== "object") return null;
    const timerSessionId = data.timerSessionId || data.timer_session_id || null;
    const date = data.date || "";
    if (!timerSessionId || !isIsoDate(date)) return null;
    const action = ["linked", "converted", "ignored"].includes(data.action) ? data.action : "linked";
    const role = data.role ? defaultTimerLinkRole(data.role) : "note";
    const now = new Date().toISOString();
    return {
      ...data,
      id: data.id || `timer_link_${safeIdPart(timerSessionId)}`,
      timerSessionId,
      date,
      action,
      targetTrainingLogId: data.targetTrainingLogId || data.target_training_log_id || null,
      role,
      note: data.note || data.notes || "",
      createdAt: data.createdAt || data.created_at || now,
      updatedAt: data.updatedAt || data.updated_at || now
    };
  }

  function normalizeTrainingLogData(data) {
    if (!data || !isIsoDate(data.date)) return null;
    const type = toSharedTrainingType(data.type || data.trainingType || data.training_type);
    return {
      ...data,
      id: data.id || `log_${data.date}_001`,
      date: data.date,
      dailyPlanItemId: data.dailyPlanItemId || data.daily_plan_item_id || null,
      timerSessionId: data.timerSessionId || data.timer_session_id || null,
      timerSessionIds: Array.isArray(data.timerSessionIds) ? data.timerSessionIds : Array.isArray(data.timer_session_ids) ? data.timer_session_ids : data.timerSessionId || data.timer_session_id ? [data.timerSessionId || data.timer_session_id] : [],
      supportSessions: Array.isArray(data.supportSessions) ? data.supportSessions : Array.isArray(data.support_sessions) ? data.support_sessions : [],
      type,
      status: toSharedCompletionStatus(data.status, type),
      source: data.source || "manual",
      durationSec: toNullableNumber(data.durationSec ?? data.duration_sec),
      distanceKm: toNullableNumber(data.distanceKm ?? data.distance_km),
      avgPaceSecPerKm: toNullableNumber(data.avgPaceSecPerKm ?? data.avg_pace_sec_per_km),
      bestPaceSecPerKm: toNullableNumber(data.bestPaceSecPerKm ?? data.best_pace_sec_per_km),
      avgHeartRate: toNullableNumber(data.avgHeartRate ?? data.avg_heart_rate),
      maxHeartRate: toNullableNumber(data.maxHeartRate ?? data.max_heart_rate),
      steps: toNullableNumber(data.steps),
      cadence: toNullableNumber(data.cadence),
      strideCm: toNullableNumber(data.strideCm ?? data.stride_cm),
      trainingEffect: toNullableNumber(data.trainingEffect ?? data.training_effect),
      trainingLoad: toNullableNumber(data.trainingLoad ?? data.training_load),
      recoveryHours: toNullableNumber(data.recoveryHours ?? data.recovery_hours),
      laps: Array.isArray(data.laps) ? data.laps : [],
      notes: data.notes || "",
      rawJson: data.rawJson || data.raw_json || {},
      createdAt: data.createdAt || data.created_at || new Date().toISOString(),
      updatedAt: data.updatedAt || data.updated_at || new Date().toISOString()
    };
  }

  function normalizeBodyMetricData(data) {
    if (!data || !isIsoDate(data.date)) return null;
    const pain = data.pain || data.pain_json || {};
    return {
      ...data,
      id: data.id || `metric_${data.date}`,
      date: data.date,
      weightKg: toNullableNumber(data.weightKg ?? data.weight_kg),
      waistCm: toNullableNumber(data.waistCm ?? data.waist_cm),
      bodyFatPct: toNullableNumber(data.bodyFatPct ?? data.body_fat_pct),
      muscleKg: toNullableNumber(data.muscleKg ?? data.muscle_kg),
      sleepQuality: SLEEP_META[data.sleepQuality || data.sleep_quality] ? data.sleepQuality || data.sleep_quality : "normal",
      energy: clamp(toNullableNumber(data.energy) || 3, 1, 5),
      fatigue: normalizeSharedFatigue(data.fatigue),
      pain: {
        calf: clampPain(pain.calf ?? pain.calfRightOuter),
        back: clampPain(pain.back ?? pain.backLeftLower),
        wrist: clampPain(pain.wrist),
        outerThigh: clampPain(pain.outerThigh ?? pain.hipRightOuter),
        calfRightOuter: clampPain(pain.calfRightOuter ?? pain.calf),
        backLeftLower: clampPain(pain.backLeftLower ?? pain.back),
        hipRightOuter: clampPain(pain.hipRightOuter ?? pain.outerThigh)
      },
      notes: data.notes || "",
      rawJson: data.rawJson || data.raw_json || {},
      createdAt: data.createdAt || data.created_at || new Date().toISOString(),
      updatedAt: data.updatedAt || data.updated_at || new Date().toISOString()
    };
  }

  function syncSharedRecordsFromLegacy() {
    const records = normalizeSharedRecords(state.records);
    const existingTrainingLogs = new Map(records.training_logs.map((item) => [item.id, item]));
    const existingBodyMetrics = new Map(records.body_metrics.map((item) => [item.id, item]));
    records.training_logs = records.training_logs.filter((item) => item.data?.rawJson?.compatSource !== "workouts");
    records.body_metrics = records.body_metrics.filter((item) => item.data?.rawJson?.compatSource !== "bodyMetrics");
    state.workouts.forEach((workout) => {
      const data = workoutToTrainingLogData(workout);
      if (data) upsertSharedEnvelope(records, "training_logs", data, existingTrainingLogs.get(data.id));
    });
    state.bodyMetrics.forEach((metric) => {
      const data = bodyMetricToSharedData(metric);
      if (data) upsertSharedEnvelope(records, "body_metrics", data, existingBodyMetrics.get(data.id));
    });
    state.records = records;
  }

  function upsertSharedEnvelope(records, entity, data, existingOverride = null) {
    const existing = existingOverride || records[entity].find((item) => item.id === data.id) || null;
    const envelope = makeSharedEnvelope(entity, data, existing);
    if (!envelope) return;
    records[entity] = records[entity].filter((item) => item.id !== envelope.id).concat(envelope).sort(compareSharedEnvelopes);
  }

  function removeSharedRecordsByDate(entities, date) {
    entities.forEach((entity) => {
      if (!Array.isArray(state.records[entity])) return;
      state.records[entity] = state.records[entity].filter((item) => item.data?.date !== date);
    });
  }

  function makeSharedEnvelope(entity, data, existing = null) {
    const now = new Date().toISOString();
    const normalized = normalizeSharedEntityData(entity, data);
    if (!normalized) return null;
    const id = normalized.id || data.id || existing?.id || makeId();
    normalized.id = id;
    const dataChanged = !existing || JSON.stringify(existing.data || {}) !== JSON.stringify(normalized);
    return {
      id,
      entity,
      data: normalized,
      revision: existing?.revision || Math.max(1, Math.round(toNullableNumber(data.revision) || 1)),
      deviceId: existing?.deviceId || data.deviceId || getDeviceId(),
      createdAt: existing?.createdAt || normalized.createdAt || now,
      updatedAt: normalized.updatedAt || now,
      deletedAt: normalized.deletedAt || null,
      syncState: dataChanged ? "dirty" : existing?.syncState || "dirty",
      lastSyncedAt: existing?.lastSyncedAt || null,
      conflict: dataChanged ? null : existing?.conflict || null
    };
  }

  function workoutToTrainingLogEnvelope(workout) {
    const data = workoutToTrainingLogData(workout);
    return data ? makeSharedEnvelope("training_logs", data) : null;
  }

  function workoutToTrainingLogData(workout) {
    if (!workout || !isIsoDate(workout.date)) return null;
    const type = toSharedTrainingType(workout.type);
    return {
      id: workout.trainingLogId || `log_${workout.date}_001`,
      date: workout.date,
      dailyPlanItemId: workout.dailyPlanItemId || null,
      timerSessionId: workout.timerSessionId || null,
      timerSessionIds: Array.isArray(workout.timerSessionIds) ? workout.timerSessionIds : workout.timerSessionId ? [workout.timerSessionId] : [],
      supportSessions: Array.isArray(workout.supportSessions) ? workout.supportSessions : [],
      type,
      status: toSharedCompletionStatus(workout.status, type),
      source: workout.source || "manual",
      durationSec: toNullableNumber(workout.durationSec),
      distanceKm: toNullableNumber(workout.distanceKm),
      avgPaceSecPerKm: toNullableNumber(workout.avgPaceSecPerKm),
      bestPaceSecPerKm: toNullableNumber(workout.bestPaceSecPerKm),
      avgHeartRate: toNullableNumber(workout.avgHeartRate),
      maxHeartRate: toNullableNumber(workout.maxHeartRate),
      steps: toNullableNumber(workout.steps),
      cadence: toNullableNumber(workout.cadence),
      strideCm: toNullableNumber(workout.strideCm),
      trainingEffect: toNullableNumber(workout.trainingEffect),
      trainingLoad: toNullableNumber(workout.trainingLoad),
      recoveryHours: toNullableNumber(workout.recoveryHours),
      notes: workout.notes || "",
      rawJson: { ...(workout.rawJson || {}), compatSource: "workouts", legacyWorkoutId: workout.id },
      createdAt: workout.createdAt || new Date().toISOString(),
      updatedAt: workout.updatedAt || new Date().toISOString()
    };
  }

  function trainingLogEnvelopeToWorkout(envelope) {
    if (!envelope || envelope.deletedAt) return null;
    const data = envelope.data || {};
    if (!isIsoDate(data.date)) return null;
    const type = toLegacyTrainingType(data.type);
    return {
      id: data.rawJson?.legacyWorkoutId || `workout_${data.id}`,
      trainingLogId: data.id,
      dailyPlanItemId: data.dailyPlanItemId || null,
      timerSessionId: data.timerSessionId || null,
      timerSessionIds: Array.isArray(data.timerSessionIds) ? data.timerSessionIds : data.timerSessionId ? [data.timerSessionId] : [],
      supportSessions: Array.isArray(data.supportSessions) ? data.supportSessions : [],
      date: data.date,
      type,
      status: toLegacyCompletionStatus(data.status, type),
      source: data.source || "manual",
      durationSec: toNullableNumber(data.durationSec),
      distanceKm: toNullableNumber(data.distanceKm),
      avgPaceSecPerKm: toNullableNumber(data.avgPaceSecPerKm),
      bestPaceSecPerKm: toNullableNumber(data.bestPaceSecPerKm),
      avgHeartRate: toNullableNumber(data.avgHeartRate),
      maxHeartRate: toNullableNumber(data.maxHeartRate),
      steps: toNullableNumber(data.steps),
      cadence: toNullableNumber(data.cadence),
      strideCm: toNullableNumber(data.strideCm),
      trainingEffect: toNullableNumber(data.trainingEffect),
      trainingLoad: toNullableNumber(data.trainingLoad),
      recoveryHours: toNullableNumber(data.recoveryHours),
      fatigue: "normal",
      pain: { calf: 0, back: 0, wrist: 0, outerThigh: 0 },
      notes: data.notes || "",
      rawJson: data.rawJson || {},
      createdAt: data.createdAt || envelope.createdAt,
      updatedAt: data.updatedAt || envelope.updatedAt
    };
  }

  function bodyMetricToSharedEnvelope(metric) {
    const data = bodyMetricToSharedData(metric);
    return data ? makeSharedEnvelope("body_metrics", data) : null;
  }

  function bodyMetricToSharedData(metric) {
    if (!metric || !isIsoDate(metric.date)) return null;
    const pain = metric.pain || {};
    return {
      id: metric.bodyMetricId || `metric_${metric.date}`,
      date: metric.date,
      weightKg: toNullableNumber(metric.weightKg),
      waistCm: toNullableNumber(metric.waistCm),
      bodyFatPct: toNullableNumber(metric.bodyFatPct),
      muscleKg: toNullableNumber(metric.muscleKg),
      sleepQuality: metric.sleepQuality || "normal",
      energy: clamp(toNullableNumber(metric.energy) || 3, 1, 5),
      fatigue: legacyFatigueToShared(metric.fatigue),
      pain: {
        calf: clampPain(pain.calf),
        back: clampPain(pain.back),
        wrist: clampPain(pain.wrist),
        outerThigh: clampPain(pain.outerThigh),
        calfRightOuter: clampPain(pain.calf),
        backLeftLower: clampPain(pain.back),
        hipRightOuter: clampPain(pain.outerThigh)
      },
      notes: metric.notes || "",
      rawJson: { compatSource: "bodyMetrics", legacyMetricId: metric.id },
      createdAt: metric.createdAt || new Date().toISOString(),
      updatedAt: metric.updatedAt || new Date().toISOString()
    };
  }

  function bodyMetricEnvelopeToLegacy(envelope) {
    if (!envelope || envelope.deletedAt) return null;
    const data = envelope.data || {};
    if (!isIsoDate(data.date)) return null;
    const pain = data.pain || {};
    return {
      id: data.rawJson?.legacyMetricId || `metric_legacy_${data.date}`,
      bodyMetricId: data.id,
      date: data.date,
      weightKg: toNullableNumber(data.weightKg),
      waistCm: toNullableNumber(data.waistCm),
      bodyFatPct: toNullableNumber(data.bodyFatPct),
      muscleKg: toNullableNumber(data.muscleKg),
      sleepQuality: data.sleepQuality || "normal",
      energy: clamp(toNullableNumber(data.energy) || 3, 1, 5),
      fatigue: sharedFatigueToLegacy(data.fatigue),
      pain: {
        calf: clampPain(pain.calf ?? pain.calfRightOuter),
        back: clampPain(pain.back ?? pain.backLeftLower),
        wrist: clampPain(pain.wrist),
        outerThigh: clampPain(pain.outerThigh ?? pain.hipRightOuter)
      },
      notes: data.notes || "",
      createdAt: data.createdAt || envelope.createdAt,
      updatedAt: data.updatedAt || envelope.updatedAt
    };
  }

  function compareSharedEnvelopes(a, b) {
    const aDate = a.data?.date || a.updatedAt || "";
    const bDate = b.data?.date || b.updatedAt || "";
    return aDate.localeCompare(bDate) || a.id.localeCompare(b.id);
  }

  function cloneJson(value) {
    return JSON.parse(JSON.stringify(value));
  }

  function hasSharedRecordPayload(payload) {
    if (!payload || typeof payload !== "object") return false;
    if (payload.records && SHARED_ENTITIES.some((entity) => Array.isArray(payload.records[entity]))) return true;
    return SHARED_ENTITIES.some((entity) => Array.isArray(payload[entity]));
  }

  function mergeSharedRecords(current, incoming) {
    const next = normalizeSharedRecords(current);
    const add = normalizeSharedRecords(incoming);
    SHARED_ENTITIES.forEach((entity) => {
      const map = new Map(next[entity].map((item) => [item.id, item]));
      add[entity].forEach((item) => {
        const existing = map.get(item.id);
        if (!existing || compareIncomingRecord(existing, item) <= 0) {
          map.set(item.id, item);
        }
      });
      next[entity] = Array.from(map.values()).sort(compareSharedEnvelopes);
    });
    return next;
  }

  function compareIncomingRecord(existing, incoming) {
    if ((incoming.revision || 0) !== (existing.revision || 0)) {
      return (existing.revision || 0) - (incoming.revision || 0);
    }
    return String(existing.updatedAt || "").localeCompare(String(incoming.updatedAt || ""));
  }

  function mergeWorkoutsByDate(current, incoming) {
    const map = new Map(normalizeWorkouts(current).map((item) => [workoutKey(item), item]));
    normalizeWorkouts(incoming).forEach((item) => map.set(workoutKey(item), item));
    return Array.from(map.values()).sort((a, b) => a.date.localeCompare(b.date) || workoutKey(a).localeCompare(workoutKey(b)));
  }

  function mergeBodyMetricsByDate(current, incoming) {
    const map = new Map(normalizeBodyMetrics(current).map((item) => [item.date, item]));
    normalizeBodyMetrics(incoming).forEach((item) => map.set(item.date, item));
    return Array.from(map.values()).sort((a, b) => a.date.localeCompare(b.date));
  }

  function seedWorkouts() {
    return SEED_WORKOUTS.map((item) => normalizeWorkout({
      id: makeId(),
      source: "import",
      fatigue: "normal",
      pain: { calf: 0, back: 0, wrist: 0, outerThigh: 0 },
      createdAt: `${item.date}T12:00:00.000Z`,
      updatedAt: `${item.date}T12:00:00.000Z`,
      ...item
    }));
  }

  function normalizeWorkouts(workouts) {
    if (!Array.isArray(workouts)) return [];
    return workouts.map(normalizeWorkout).filter(Boolean).sort((a, b) => a.date.localeCompare(b.date));
  }

  function normalizeWorkout(item) {
    if (!item || !isIsoDate(item.date)) return null;
    const type = toLegacyTrainingType(item.type);
    const status = toLegacyCompletionStatus(item.status, type);
    return {
      id: item.id || makeId(),
      trainingLogId: item.trainingLogId || null,
      dailyPlanItemId: item.dailyPlanItemId || null,
      timerSessionId: item.timerSessionId || null,
      timerSessionIds: Array.isArray(item.timerSessionIds) ? item.timerSessionIds : item.timerSessionId ? [item.timerSessionId] : [],
      supportSessions: Array.isArray(item.supportSessions) ? item.supportSessions : [],
      date: item.date,
      type,
      status,
      source: item.source || "manual",
      durationSec: toNullableNumber(item.durationSec),
      distanceKm: toNullableNumber(item.distanceKm),
      avgPaceSecPerKm: toNullableNumber(item.avgPaceSecPerKm),
      bestPaceSecPerKm: toNullableNumber(item.bestPaceSecPerKm),
      avgHeartRate: toNullableNumber(item.avgHeartRate),
      maxHeartRate: toNullableNumber(item.maxHeartRate),
      steps: toNullableNumber(item.steps),
      cadence: toNullableNumber(item.cadence),
      strideCm: toNullableNumber(item.strideCm),
      trainingEffect: toNullableNumber(item.trainingEffect),
      trainingLoad: toNullableNumber(item.trainingLoad),
      recoveryHours: toNullableNumber(item.recoveryHours),
      fatigue: FATIGUE_META[item.fatigue] ? item.fatigue : "normal",
      pain: {
        calf: clampPain(item.pain && item.pain.calf),
        back: clampPain(item.pain && item.pain.back),
        wrist: clampPain(item.pain && item.pain.wrist),
        outerThigh: clampPain(item.pain && item.pain.outerThigh)
      },
      notes: item.notes || "",
      rawJson: item.rawJson || {},
      createdAt: item.createdAt || new Date().toISOString(),
      updatedAt: item.updatedAt || new Date().toISOString()
    };
  }

  function normalizeBodyMetrics(metrics) {
    if (!Array.isArray(metrics)) return [];
    return metrics.map((item) => {
      if (!item || !isIsoDate(item.date)) return null;
      return {
        id: item.id || makeId(),
        date: item.date,
        weightKg: toNullableNumber(item.weightKg),
        waistCm: toNullableNumber(item.waistCm),
        bodyFatPct: toNullableNumber(item.bodyFatPct),
        muscleKg: toNullableNumber(item.muscleKg),
        bodyMetricId: item.bodyMetricId || null,
        sleepQuality: SLEEP_META[item.sleepQuality] ? item.sleepQuality : "normal",
        energy: clamp(toNullableNumber(item.energy) || 3, 1, 5),
        fatigue: normalizeLegacyFatigue(item.fatigue),
        pain: {
          calf: clampPain(item.pain && item.pain.calf),
          back: clampPain(item.pain && item.pain.back),
          wrist: clampPain(item.pain && item.pain.wrist),
          outerThigh: clampPain(item.pain && item.pain.outerThigh)
        },
        notes: item.notes || "",
        createdAt: item.createdAt || new Date().toISOString(),
        updatedAt: item.updatedAt || new Date().toISOString()
      };
    }).filter(Boolean).sort((a, b) => a.date.localeCompare(b.date));
  }

  function render() {
    if (!state.ready) {
      app.innerHTML = `
        <main class="boot-screen">
          <img class="boot-logo" src="${BRAND_META.logoDark}" alt="${BRAND_META.name}">
          <h1>${BRAND_META.name}</h1>
          <p>${BRAND_META.slogan}</p>
        </main>
      `;
      return;
    }

    app.innerHTML = `
      <div class="workspace">
        ${renderSideNav()}
        <main class="workspace-main">
          ${renderActiveTab()}
        </main>
        ${state.message ? `<div class="toast-message" role="status">${escapeHtml(state.message)}</div>` : ""}
        ${state.detailOpen ? renderDateDrawer() : ""}
      </div>
    `;
    bindEvents();
    scheduleMessageDismiss();
  }

  function scheduleMessageDismiss() {
    if (messageTimer) {
      window.clearTimeout(messageTimer);
      messageTimer = null;
    }
    if (!state.message) return;
    const message = state.message;
    messageTimer = window.setTimeout(() => {
      if (state.message !== message) return;
      state.message = "";
      messageTimer = null;
      app.querySelector(".toast-message")?.remove();
    }, 2600);
  }

  function renderSideNav() {
    const tabs = ["calendar", "timer", "data"];
    return `
      <aside class="side-nav" aria-label="主导航">
        <nav class="side-tabs">
          ${tabs.map((id) => renderSideTab(id)).join("")}
        </nav>
        <div class="side-bottom">
          ${renderSideTab("settings", "settings-tab")}
        </div>
      </aside>
    `;
  }

  function renderSideTab(id, extraClass = "") {
    const item = APP_ICON_META[id];
    return `
      <button type="button" class="side-tab ${extraClass} ${state.activeTab === id ? "active" : ""}" data-tab="${id}" title="${item.label}">
        <img class="side-tab-icon-img" src="${escapeHtml(item.asset)}" alt="" aria-hidden="true">
        <span>${escapeHtml(item.label)}</span>
      </button>
    `;
  }

  function renderActiveTab() {
    if (state.activeTab === "timer") {
      return renderTimerSessionsPage();
    }

    if (state.activeTab === "data") {
      return state.dataView === "records" ? renderAllRecordsPage() : renderDataSummaryPage();
    }

    if (state.activeTab === "settings") {
      return `
        <section class="content-page settings-page">
          ${renderPageHead("设置")}
          <div class="data-grid-layout">
            ${panel("云数据库", "Cloudflare Worker + D1，同一份云端数据，按角色读写", renderSyncPanel())}
            ${panel("本地数据", "导入、导出和种子记录", renderDataPanel())}
          </div>
        </section>
      `;
    }

    return `
      <section class="calendar-page">
        ${renderPageHead("训练日历")}
        <div data-calendar-area="true">${renderCalendar()}</div>
      </section>
    `;
  }

  function renderPageHead(title) {
    return `
      <div class="page-head">
        <div class="page-title-group">
          <h1>${escapeHtml(title)}</h1>
        </div>
        <img class="page-brand-logo" src="${BRAND_META.logoDark}" alt="${BRAND_META.name}，${BRAND_META.slogan}">
      </div>
    `;
  }

  function panel(title, subtitle, body, action = "") {
    return `
      <section class="panel">
        <div class="panel-header">
          <div>
            <h2 class="panel-title">${escapeHtml(title)}</h2>
            <p class="panel-subtitle">${escapeHtml(subtitle)}</p>
          </div>
          ${action}
        </div>
        <div class="panel-body">${body}</div>
      </section>
    `;
  }

  function renderDataSummaryPage() {
    return `
      <section class="content-page data-page">
        ${renderPageHead("数据")}
        <div class="data-grid-layout">
          <div class="data-column">
            ${panel("体重趋势", "最近体重记录", renderWeightTrend())}
            ${panel("腰围趋势", "最近腰围记录", renderWaistTrend())}
          </div>
          <div class="data-column">
            ${panel("记录概览", "最近训练节奏", renderOverview())}
            ${panel(
              "最近记录",
              "只显示最近几条",
              renderRecordList(recentWorkouts(4), "recent-record-list"),
              `<button type="button" class="subtle panel-action" data-action="open-all-records">全部记录</button>`
            )}
          </div>
        </div>
      </section>
    `;
  }

  function renderAllRecordsPage() {
    const records = recentWorkouts();
    return `
      <section class="content-page records-page">
        <div class="page-head records-page-head">
          <div class="page-title-group">
            <h1>全部记录</h1>
          </div>
          <button type="button" class="subtle" data-action="back-to-data">返回数据</button>
        </div>
        ${records.length ? renderWeeklyRecordTimeline(records) : `<div class="empty-state">暂无记录。</div>`}
      </section>
    `;
  }

  function renderDateDrawer() {
    const date = state.selectedDate;
    const isPast = date < todayISO();
    const hasRecord = Boolean(getWorkoutByDate(date) || getMetricByDate(date));
    const hasEditableDate = date <= todayISO();
    const canEdit = canEditSelectedDate();
    const editLabel = isPast ? "修正" : "编辑";
    const cancelLabel = isPast ? "取消修正" : "取消编辑";
    const editorTitle = isPast ? "修正记录" : hasRecord ? "编辑今天" : "记录今天";
    return `
      <aside class="date-drawer" aria-label="日期详情">
        <div class="drawer-head">
          <div>
            <p class="drawer-kicker">${escapeHtml(getSelectedPanelTitle())}</p>
            <h2>${escapeHtml(date)}</h2>
          </div>
          <div class="drawer-actions">
            ${hasEditableDate && !state.editMode ? `<button type="button" class="subtle" data-action="enable-edit">${editLabel}</button>` : ""}
            ${hasEditableDate && state.editMode ? `<button type="button" class="subtle" data-action="cancel-edit">${cancelLabel}</button>` : ""}
            <button type="button" class="icon-button" data-action="close-detail" aria-label="关闭">×</button>
          </div>
        </div>
        <div class="drawer-body">
          ${renderSelectedSummary()}
          ${canEdit ? `
            <section class="drawer-editor">
              <h3>${editorTitle}</h3>
              ${renderEditor()}
            </section>
          ` : renderReadOnlyNote()}
        </div>
      </aside>
    `;
  }

  function renderReadOnlyNote() {
    return "";
  }

  function canEditSelectedDate() {
    return state.editMode && state.selectedDate <= todayISO();
  }

  function getSelectedPanelTitle() {
    const date = state.selectedDate;
    if (getWorkoutByDate(date) || getMetricByDate(date)) return "日期详情";
    if (date === todayISO()) return "今日建议";
    if (date > todayISO()) return "未来预测";
    return "日期详情";
  }

  function renderTypeIcon(type, className) {
    const meta = TYPE_META[type] || TYPE_META.easyWalk;
    return `<img class="${className}" src="${escapeHtml(meta.asset)}" alt="" aria-hidden="true">`;
  }

  function renderAdviceCard(date, kind) {
    const recommendation = getDisplayRecommendation(date);
    const meta = TYPE_META[recommendation.type];
    const isToday = date === todayISO();
    const label = kind === "forecast" ? "预测" : "建议";
    return `
      <div class="suggestion advice-${kind}">
        <div class="suggestion-top">
          <div>
            <span class="suggestion-label">${label} · ${escapeHtml(recommendation.label)}</span>
            <h2>${renderTypeIcon(recommendation.type, "title-icon-img")}${escapeHtml(recommendation.title)}</h2>
          </div>
          <div class="suggestion-time">${recommendation.minutes}<span class="sr-only">分钟</span></div>
        </div>
        <ul class="reason-list">
          ${recommendation.reasons.map((reason) => `<li>${escapeHtml(reason)}</li>`).join("")}
        </ul>
      </div>
    `;
  }

  function renderOverview() {
    const today = todayISO();
    const last7 = getWindowWorkouts(today, 7);
    const last30 = getWindowWorkouts(today, 30);
    const completed = last30.filter(isCompletedRecord).length;
    const distance = last30.reduce((sum, item) => sum + (item.distanceKm || 0), 0);
    const strength = last7.filter((item) => item.type === "strength" && isCompletedRecord(item)).length;
    const aerobic = last7.filter((item) => ["easyWalk", "qualityWalk", "indoorCardio"].includes(item.type) && isCompletedRecord(item)).length;
    return `
      <div class="metric-row">
        <div class="metric"><strong>${state.workouts.length}</strong><span>总记录</span></div>
        <div class="metric"><strong>${completed}</strong><span>30 天完成</span></div>
        <div class="metric"><strong>${formatNumber(distance, 1)}</strong><span>30 天公里</span></div>
        <div class="metric"><strong>${strength}/${aerobic}</strong><span>7 天力/有氧</span></div>
      </div>
    `;
  }

  function renderWeightTrend() {
    return renderBodyTrend({
      key: "weightKg",
      title: "体重趋势",
      unit: "kg",
      emptyText: "还没有体重记录。编辑某一天时填写体重后，这里会显示趋势。",
      rangeFloor: 0.8,
      deltaThreshold: 0.05,
      className: "weight-trend-chart"
    });
  }

  function renderWaistTrend() {
    return renderBodyTrend({
      key: "waistCm",
      title: "腰围趋势",
      unit: "cm",
      emptyText: "还没有腰围记录。编辑状态记录时填写腰围后，这里会显示趋势。",
      rangeFloor: 1,
      deltaThreshold: 0.1,
      className: "waist-trend-chart"
    });
  }

  function renderBodyTrend({ key, title, unit, emptyText, rangeFloor, deltaThreshold, className }) {
    const records = state.bodyMetrics
      .filter((item) => item[key] !== null && item[key] !== undefined)
      .sort((a, b) => a.date.localeCompare(b.date))
      .slice(-30);

    if (!records.length) {
      return `<div class="empty-state">${escapeHtml(emptyText)}</div>`;
    }

    const latest = records[records.length - 1];
    const first = records[0];
    const delta = latest[key] - first[key];
    const values = records.map((item) => item[key]);
    const minValue = Math.min(...values);
    const maxValue = Math.max(...values);
    const range = Math.max(rangeFloor, maxValue - minValue);
    const chartMin = minValue - range * 0.16;
    const chartMax = maxValue + range * 0.16;
    const chartRange = chartMax - chartMin;
    const width = 620;
    const height = 220;
    const padLeft = 42;
    const padRight = 18;
    const padTop = 18;
    const padBottom = 34;
    const innerWidth = width - padLeft - padRight;
    const innerHeight = height - padTop - padBottom;
    const points = records.map((item, index) => {
      const x = records.length === 1 ? padLeft + innerWidth / 2 : padLeft + (index / (records.length - 1)) * innerWidth;
      const y = padTop + ((chartMax - item[key]) / chartRange) * innerHeight;
      return { x, y, item };
    });
    const linePoints = points.map((point) => `${formatNumber(point.x, 1)},${formatNumber(point.y, 1)}`).join(" ");
    const yTicks = [chartMax, (chartMax + chartMin) / 2, chartMin];
    const deltaText = `${delta > 0 ? "+" : ""}${formatNumber(delta, 1)} ${unit}`;
    const deltaClass = delta > deltaThreshold ? "trend-up" : delta < -deltaThreshold ? "trend-down" : "trend-flat";
    const latestPoint = points[points.length - 1];

    return `
      <div class="weight-trend ${className}">
        <div class="trend-summary">
          <div class="trend-stat primary-stat">
            <span>最新</span>
            <strong>${formatNumber(latest[key], 1)} ${unit}</strong>
            <small>${formatShortDate(latest.date)}</small>
          </div>
          <div class="trend-stat">
            <span>区间变化</span>
            <strong class="${deltaClass}">${deltaText}</strong>
            <small>${formatShortDate(first.date)} - ${formatShortDate(latest.date)}</small>
          </div>
          <div class="trend-stat">
            <span>记录数</span>
            <strong>${records.length}</strong>
            <small>最近 ${records.length} 条</small>
          </div>
        </div>
        <svg class="trend-chart" viewBox="0 0 ${width} ${height}" role="img" aria-label="${escapeHtml(title)}">
          ${yTicks.map((tick) => {
            const y = padTop + ((chartMax - tick) / chartRange) * innerHeight;
            return `
              <line class="trend-grid" x1="${padLeft}" y1="${formatNumber(y, 1)}" x2="${width - padRight}" y2="${formatNumber(y, 1)}"></line>
              <text class="trend-y-label" x="8" y="${formatNumber(y + 4, 1)}">${formatNumber(tick, 1)}</text>
            `;
          }).join("")}
          <polyline class="trend-line" points="${linePoints}"></polyline>
          ${records.length === 1 ? `<circle class="trend-dot" cx="${formatNumber(latestPoint.x, 1)}" cy="${formatNumber(latestPoint.y, 1)}" r="5"></circle>` : ""}
          <circle class="trend-dot latest-dot" cx="${formatNumber(latestPoint.x, 1)}" cy="${formatNumber(latestPoint.y, 1)}" r="5"></circle>
          <text class="trend-x-label" x="${padLeft}" y="${height - 8}">${formatShortDate(first.date)}</text>
          <text class="trend-x-label end" x="${width - padRight}" y="${height - 8}">${formatShortDate(latest.date)}</text>
        </svg>
      </div>
    `;
  }

  function renderEditor() {
    const record = getWorkoutByDate(state.selectedDate);
    const metric = getMetricByDate(state.selectedDate);
    const draft = getEditorDraft(state.selectedDate);
    const trainingDraft = draft?.training || {};
    const statusDraft = draft?.status || {};
    const recommendedType = getDisplayRecommendation(state.selectedDate).type;
    const type = TYPE_META[draftValue(trainingDraft, "type", record ? record.type : recommendedType)] ? draftValue(trainingDraft, "type", record ? record.type : recommendedType) : recommendedType;
    const status = getStatusForType(type, draftValue(trainingDraft, "status", record ? record.status : "completed"));
    const trainingDate = draftValue(trainingDraft, "date", state.selectedDate);
    const durationMin = draftValue(trainingDraft, "durationMin", record && record.durationSec ? Math.round(record.durationSec / 60) : "");
    const distanceKm = draftValue(trainingDraft, "distanceKm", record && record.distanceKm ? record.distanceKm : "");
    const avgHeartRate = draftValue(trainingDraft, "avgHeartRate", record && record.avgHeartRate ? record.avgHeartRate : "");
    const trainingNotes = draftValue(trainingDraft, "trainingNotes", record ? record.notes : "");
    const pain = metric ? metric.pain : record ? record.pain : { calf: 0, back: 0, wrist: 0, outerThigh: 0 };
    const fatigue = metric ? metric.fatigue : record ? record.fatigue : "normal";
    const weightKg = draftValue(statusDraft, "weightKg", metric && metric.weightKg !== null ? metric.weightKg : "");
    const waistCm = draftValue(statusDraft, "waistCm", metric && metric.waistCm !== null ? metric.waistCm : "");
    const bodyFatPct = draftValue(statusDraft, "bodyFatPct", metric && metric.bodyFatPct !== null ? metric.bodyFatPct : "");
    const muscleKg = draftValue(statusDraft, "muscleKg", metric && metric.muscleKg !== null ? metric.muscleKg : "");
    const sleepQuality = draftValue(statusDraft, "sleepLevel", metric ? metric.sleepQuality : "normal");
    const energy = draftValue(statusDraft, "energy", metric ? metric.energy : 3);
    const fatigueLevel = draftValue(statusDraft, "fatigueLevel", fatigue);
    const painCalf = draftValue(statusDraft, "painCalf", pain.calf);
    const painBack = draftValue(statusDraft, "painBack", pain.back);
    const painWrist = draftValue(statusDraft, "painWrist", pain.wrist);
    const painOuterThigh = draftValue(statusDraft, "painOuterThigh", pain.outerThigh);
    const statusNotes = draftValue(statusDraft, "statusNotes", metric ? metric.notes : "");
    const hasRecord = Boolean(record);

    return `
      <form class="editor-form" id="training-form">
        ${formSection("训练记录", "实际做了什么，只填已经发生的训练数据。", `
          <div class="form-grid">
            ${field("日期", `<input name="date" type="date" value="${escapeHtml(trainingDate)}">`)}
            ${field("训练类型", renderSelect("type", TYPE_META, type))}
            ${field("完成状态", renderSelect("status", getStatusOptionsForType(type), status))}
            ${field("时长（分钟）", `<input name="durationMin" type="number" min="0" step="1" value="${escapeHtml(durationMin)}">`)}
            ${field("距离（公里）", `<input name="distanceKm" type="number" min="0" step="0.01" value="${escapeHtml(distanceKm)}">`)}
            ${field("平均心率", `<input name="avgHeartRate" type="number" min="0" step="1" value="${escapeHtml(avgHeartRate)}">`)}
          </div>
        `)}
        ${formSection("训练备注", "", `
          ${field("备注", `<textarea name="trainingNotes">${escapeHtml(trainingNotes)}</textarea>`, true)}
        `)}
        <div class="button-row form-actions">
          <button type="submit" class="primary">保存训练</button>
          ${hasRecord ? `<button type="button" class="danger" data-action="delete-date">删除当天</button>` : ""}
        </div>
      </form>
      <form class="editor-form" id="status-form">
        <input name="date" type="hidden" value="${state.selectedDate}">
        ${formSection("状态记录", "", `
          <div class="status-editor">
            <div class="status-primary-grid">
            ${field("体重（kg）", `<input name="weightKg" type="number" min="0" step="0.1" value="${escapeHtml(weightKg)}">`)}
            ${field("腰围（cm）", `<input name="waistCm" type="number" min="0" step="0.1" value="${escapeHtml(waistCm)}">`)}
            ${field("体脂率（%）", `<input name="bodyFatPct" type="number" min="0" max="80" step="0.1" value="${escapeHtml(bodyFatPct)}">`)}
            ${field("肌肉量（kg）", `<input name="muscleKg" type="number" min="0" step="0.1" value="${escapeHtml(muscleKg)}">`)}
              ${sleepRangeField(sleepQuality)}
              ${rangeField("今日精力", "energy", energy, 1, 5, `${energy}/5`, "低", "高")}
              ${fatigueRangeField(fatigueLevel)}
            </div>
            <div class="status-pain-grid">
              ${painField("小腿", "painCalf", painCalf)}
              ${painField("腰背", "painBack", painBack)}
              ${painField("手腕", "painWrist", painWrist)}
              ${painField("大腿外侧", "painOuterThigh", painOuterThigh)}
            </div>
          </div>
        `)}
        ${formSection("状态备注", "", `
          ${field("备注", `<textarea name="statusNotes">${escapeHtml(statusNotes)}</textarea>`, true)}
        `)}
        <div class="button-row form-actions">
          <button type="submit" class="primary">保存状态</button>
        </div>
      </form>
    `;
  }

  function formSection(title, description, body) {
    return `
      <section class="form-section">
        <div class="form-section-head">
          <h3>${escapeHtml(title)}</h3>
          ${description ? `<p>${escapeHtml(description)}</p>` : ""}
        </div>
        ${body}
      </section>
    `;
  }

  function field(label, input, full) {
    return `
      <div class="form-field ${full ? "full" : ""}">
        <label>${escapeHtml(label)}</label>
        ${input}
      </div>
    `;
  }

  function rangeField(label, name, value, min, max, valueText, lowLabel, highLabel, kind = name, wide = false) {
    const current = clamp(Math.round(toNullableNumber(value) || min), min, max);
    return `
      <div class="range-field range-${kind} tone-${rangeTone(kind, current)} ${wide ? "range-wide" : ""}" style="--range-fill: ${rangeFill(current, min, max)}%;">
        <div class="range-head">
          <label>${escapeHtml(label)}</label>
          <span class="range-value" data-range-output>${escapeHtml(valueText)}</span>
        </div>
        <input class="range-input" name="${name}" type="range" min="${min}" max="${max}" step="1" value="${current}" data-range-input data-range-kind="${kind}">
        <div class="range-scale"><span>${escapeHtml(lowLabel)}</span><span>${escapeHtml(highLabel)}</span></div>
      </div>
    `;
  }

  function sleepRangeField(level) {
    const current = /^\d+$/.test(String(level)) ? clamp(Math.round(Number(level)), 1, SLEEP_ORDER.length) : sleepRangeValue(SLEEP_META[level] ? level : "normal");
    const currentLevel = sleepFromRange(current);
    return rangeField("昨晚睡眠", "sleepLevel", current, 1, SLEEP_ORDER.length, SLEEP_META[currentLevel], SLEEP_META.poor, SLEEP_META.good, "sleep");
  }

  function fatigueRangeField(level) {
    const current = /^\d+$/.test(String(level)) ? clamp(Math.round(Number(level)), 1, FATIGUE_SLIDER_ORDER.length) : fatigueRangeValue(FATIGUE_META[level] ? level : "normal");
    const currentLevel = fatigueFromRange(current);
    return rangeField("当前疲劳", "fatigueLevel", current, 1, FATIGUE_SLIDER_ORDER.length, FATIGUE_META[currentLevel], FATIGUE_META.severe, FATIGUE_META.low, "fatigue", true);
  }

  function painField(label, name, value) {
    const current = Math.min(3, clampPain(value));
    return `
      <div class="pain-field">
        <label>${escapeHtml(label)}</label>
        <input type="hidden" name="${name}" value="${current}" data-pain-input="${name}">
        <div class="segmented-control" role="group" aria-label="${escapeHtml(label)}不适程度">
          ${PAIN_LEVELS.map((level) => `
            <button type="button" class="segment-button ${current === level.value ? "active" : ""}" data-pain-option="${name}" data-value="${level.value}">
              ${escapeHtml(level.label)}
            </button>
          `).join("")}
        </div>
      </div>
    `;
  }

  function renderSelect(name, meta, selected) {
    return `
      <select name="${name}">
        ${Object.entries(meta).map(([value, option]) => {
          const label = typeof option === "string" ? option : option.label;
          return `<option value="${value}" ${selected === value ? "selected" : ""}>${escapeHtml(label)}</option>`;
        }).join("")}
      </select>
    `;
  }

  function getStatusOptionsForType(type) {
    return STATUS_OPTIONS_BY_TYPE[type] || STATUS_OPTIONS_BY_TYPE.easyWalk;
  }

  function getStatusForType(type, status) {
    const options = getStatusOptionsForType(type);
    if (Object.prototype.hasOwnProperty.call(options, status)) return status;
    if (type === "rest") return "skipped";
    if (type === "recovery" && status === "short") return "completed";
    return "completed";
  }

  function getStatusLabel(status, type) {
    return getStatusOptionsForType(type)[status] || STATUS_META[status] || String(status || "");
  }

  function fatigueRangeValue(level) {
    return Math.max(1, FATIGUE_SLIDER_ORDER.indexOf(level) + 1);
  }

  function fatigueFromRange(value) {
    const index = clamp(Math.round(toNullableNumber(value) || 3), 1, FATIGUE_SLIDER_ORDER.length) - 1;
    return FATIGUE_SLIDER_ORDER[index] || "normal";
  }

  function sleepRangeValue(level) {
    return Math.max(1, SLEEP_ORDER.indexOf(level) + 1);
  }

  function sleepFromRange(value) {
    const index = clamp(Math.round(toNullableNumber(value) || 2), 1, SLEEP_ORDER.length) - 1;
    return SLEEP_ORDER[index] || "normal";
  }

  function rangeFill(value, min, max) {
    if (max <= min) return 100;
    return Math.round(((value - min) / (max - min)) * 100);
  }

  function rangeTone(kind, value) {
    const number = Math.round(toNullableNumber(value) || 0);
    if (kind === "fatigue") {
      if (number <= 1) return "risk";
      if (number === 2) return "warning";
      if (number === 3) return "steady";
      return "good";
    }
    if (kind === "sleep") {
      if (number <= 1) return "risk";
      if (number === 2) return "steady";
      return "good";
    }
    if (kind === "energy") {
      if (number <= 2) return "risk";
      if (number === 3) return "steady";
      return "good";
    }
    return "steady";
  }

  function rangeDisplayText(kind, value) {
    if (kind === "fatigue") return FATIGUE_META[fatigueFromRange(value)];
    if (kind === "sleep") return SLEEP_META[sleepFromRange(value)];
    return `${value}/5`;
  }

  function updateStatusOptions(select, type, currentStatus, previousType) {
    const nextStatus = previousType === "rest" && type !== "rest" ? "completed" : currentStatus;
    const status = getStatusForType(type, nextStatus);
    select.innerHTML = Object.entries(getStatusOptionsForType(type)).map(([value, label]) => {
      return `<option value="${value}" ${status === value ? "selected" : ""}>${escapeHtml(label)}</option>`;
    }).join("");
    select.value = status;
    select.dataset.statusType = type;
  }

  function buildMonthCalendarEntries(year, month) {
    const entries = new Map();
    const daysInMonth = new Date(year, month, 0).getDate();
    const today = todayISO();
    const monthStart = `${year}-${pad(month)}-01`;
    const monthEnd = `${year}-${pad(month)}-${pad(daysInMonth)}`;
    const virtualWorkouts = state.workouts.map((item) => ({ ...item }));

    for (let day = 1; day <= daysInMonth; day += 1) {
      const date = `${year}-${pad(month)}-${pad(day)}`;
      const record = getWorkoutByDate(date);
      if (record) {
        entries.set(date, calendarEntryFromRecord(record));
      } else {
        const pendingTimers = getConfirmableTimerSessions(date);
        if (pendingTimers.length) {
          entries.set(date, calendarEntryFromTimerSession(pendingTimers[0]));
        }
      }
    }

    if (monthEnd >= today) {
      const cursor = parseIsoDate(today);
      const end = parseIsoDate(monthEnd);
      while (cursor <= end) {
        const date = dateToISO(cursor);
        const record = getWorkoutByDate(date);
        if (!record && !entries.has(date)) {
          const recommendation = getRecommendation(date, virtualWorkouts);
          if (date >= monthStart) {
            const kind = date === today ? "suggestion" : "forecast";
            entries.set(date, calendarEntryFromRecommendation(date, recommendation, kind));
          }
          virtualWorkouts.push(makeVirtualWorkout(date, recommendation));
        }
        cursor.setDate(cursor.getDate() + 1);
      }
    }

    return entries;
  }

  function getDisplayRecommendation(date) {
    return date > todayISO() ? getRollingRecommendation(date) : getRecommendation(date);
  }

  function getRollingRecommendation(date) {
    const today = todayISO();
    const virtualWorkouts = state.workouts.map((item) => ({ ...item }));
    const cursor = parseIsoDate(today);
    const target = parseIsoDate(date);
    let recommendation = getRecommendation(date, virtualWorkouts);

    while (cursor <= target) {
      const current = dateToISO(cursor);
      const record = getWorkoutByDate(current);
      if (!record) {
        const nextRecommendation = getRecommendation(current, virtualWorkouts);
        if (current === date) return nextRecommendation;
        virtualWorkouts.push(makeVirtualWorkout(current, nextRecommendation));
      }
      cursor.setDate(cursor.getDate() + 1);
    }

    return recommendation;
  }

  function calendarEntryFromRecord(record) {
    const meta = TYPE_META[record.type];
    const done = isCompletedRecord(record);
    const statusLabel = getStatusLabel(record.status, record.type);
    return {
      kind: "actual",
      type: record.type,
      icon: meta.icon,
      marker: statusLabel,
      text: formatCalendarRecord(record),
      className: meta.className,
      statusClass: done ? "status-done" : `status-${record.status}`
    };
  }

  function calendarEntryFromRecommendation(date, recommendation, kind) {
    const meta = TYPE_META[recommendation.type];
    return {
      kind,
      type: recommendation.type,
      icon: meta.icon,
      marker: kind === "suggestion" ? "建议" : "预测",
      text: meta.label,
      className: meta.className
    };
  }

  function calendarEntryFromTimerSession(envelope) {
    const data = envelope.data || {};
    const type = toLegacyTrainingType(data.trainingType || data.type);
    const meta = TYPE_META[type] || TYPE_META.easyWalk;
    return {
      kind: "timer",
      type,
      icon: meta.icon,
      marker: "待确认",
      text: "计时器记录",
      className: meta.className,
      statusClass: "status-pending"
    };
  }

  function makeVirtualWorkout(date, recommendation) {
    return {
      id: `forecast-${date}`,
      date,
      type: recommendation.type,
      status: recommendation.type === "rest" ? "skipped" : "completed",
      source: "forecast",
      durationSec: recommendation.minutes * 60,
      fatigue: "normal",
      pain: { calf: 0, back: 0, wrist: 0, outerThigh: 0 },
      notes: ""
    };
  }

  function renderCalendar() {
    const [year, month] = state.visibleMonth.split("-").map(Number);
    const first = new Date(year, month - 1, 1);
    const daysInMonth = new Date(year, month, 0).getDate();
    const offset = (first.getDay() + 6) % 7;
    const weekCount = Math.max(5, Math.ceil((offset + daysInMonth) / 7));
    const entries = buildMonthCalendarEntries(year, month);
    const cells = [];

    for (let i = 0; i < offset; i += 1) {
      cells.push(`<button type="button" class="day-cell empty" aria-hidden="true"></button>`);
    }

    for (let day = 1; day <= daysInMonth; day += 1) {
      const date = `${year}-${pad(month)}-${pad(day)}`;
      const entry = entries.get(date);
      const classes = ["day-cell"];
      if (entry) classes.push(entry.className, `calendar-${entry.kind}`, entry.statusClass || "");
      if (date === todayISO()) classes.push("today");
      if (date === state.selectedDate) classes.push("selected");
      const showIcon = entry && entry.kind === "actual";
      cells.push(`
        <button type="button" class="${classes.join(" ")}" data-date="${date}">
          <span class="day-head">
            <span class="day-date">
              <span class="day-number">${day}</span>
              ${date === todayISO() ? `<span class="today-badge">今天</span>` : ""}
            </span>
            ${entry ? `<span class="day-marker marker-${entry.kind}">${escapeHtml(entry.marker)}</span>` : ""}
          </span>
          ${entry ? `
            <span class="day-content ${showIcon ? "" : "text-only"}">
              ${showIcon ? renderTypeIcon(entry.type, "day-type-icon") : ""}
              <span class="day-kind">${escapeHtml(entry.text)}</span>
            </span>
          ` : ""}
        </button>
      `);
    }

    const trailingDays = weekCount * 7 - offset - daysInMonth;
    for (let i = 0; i < trailingDays; i += 1) {
      cells.push(`<button type="button" class="day-cell empty" aria-hidden="true"></button>`);
    }

    return `
      <div class="calendar-board">
        <div class="calendar-tools">
          <div class="month-switcher">
            <button type="button" class="subtle" data-action="month-prev" aria-label="上个月">‹</button>
            <div class="calendar-title">${year}-${pad(month)}</div>
            <button type="button" class="subtle" data-action="month-next" aria-label="下个月">›</button>
          </div>
          <div class="calendar-legend">
            <span><i class="legend-dot legend-actual"></i>记录</span>
            <span><i class="legend-dot legend-timer"></i>计时器待确认</span>
            <span><i class="legend-dot legend-suggestion"></i>今日建议</span>
            <span><i class="legend-dot legend-forecast"></i>未来预测</span>
          </div>
        </div>
        <div class="calendar-grid weeks-${weekCount}">
          ${["一", "二", "三", "四", "五", "六", "日"].map((day) => `<div class="weekday">${day}</div>`).join("")}
          ${cells.join("")}
        </div>
      </div>
    `;
  }

  function renderSelectedSummary() {
    const date = state.selectedDate;
    const records = getWorkoutsByDate(date);
    const record = records[0] || null;
    const metric = getMetricByDate(date);
    const planItems = getPlanItemsByDate(date);
    const adjustments = getPlanAdjustmentsByDate(date);
    const timerSessions = getTimerSessionsForDate(date);
    const sections = [];

    if (planItems.length) {
      sections.push(renderLayerSection("计划", planItems.map(renderPlanItemCard).join("")));
    } else if (date >= todayISO() && !record) {
      sections.push(renderLayerSection(date === todayISO() ? "今日建议" : "未来预测", renderAdviceCard(date, date === todayISO() ? "suggestion" : "forecast")));
    }

    if (adjustments.length) {
      sections.push(renderLayerSection("调整", adjustments.map(renderPlanAdjustmentCard).join("")));
    }

    if (records.length) {
      sections.push(renderLayerSection("正式训练记录", records.map(renderRecordCard).join("")));
    } else if (timerSessions.some((session) => getTimerSessionHandling(session).action === "pending")) {
      sections.push(renderLayerSection("正式训练记录", `<div class="empty-state compact">有计时器记录待处理，确认后才会进入正式训练记录。</div>`));
    }

    if (timerSessions.length) {
      sections.push(renderLayerSection("计时器记录", timerSessions.map(renderTimerSessionCard).join("")));
    }

    if (metric) {
      sections.push(renderLayerSection("身体状态", renderMetricCard(metric)));
    }

    if (!sections.length) {
      return `<div class="empty-state">这一天还没有记录。</div>`;
    }

    return `<div class="detail-layer-list">${sections.join("")}</div>`;
  }

  function renderLayerSection(title, body) {
    return `
      <section class="detail-layer">
        <h3>${escapeHtml(title)}</h3>
        <div class="detail-layer-body">${body}</div>
      </section>
    `;
  }

  function renderPlanItemCard(envelope) {
    const data = envelope.data || {};
    const legacyType = toLegacyTrainingType(data.trainingType || data.type);
    const meta = TYPE_META[legacyType] || TYPE_META.easyWalk;
    const details = [
      data.estimatedMinutes ? `${data.estimatedMinutes} 分` : "",
      data.routineId ? `routine ${data.routineId}` : "",
      data.sourcePlanVersion ? `计划版本 ${data.sourcePlanVersion}` : ""
    ].filter(Boolean).join(" · ");
    return `
      <article class="record-card plan-card">
        <div class="record-top">
          <h3>${renderTypeIcon(legacyType, "record-type-icon")}${escapeHtml(data.title || meta.label)}</h3>
          <span class="tag">计划</span>
        </div>
        ${details ? `<p>${escapeHtml(details)}</p>` : ""}
        ${data.goal ? `<p>${escapeHtml(data.goal)}</p>` : ""}
        ${Array.isArray(data.notes) && data.notes.length ? `<p>${escapeHtml(data.notes.join("；"))}</p>` : ""}
        ${data.routineId ? `
          <div class="button-row compact-actions">
            <button type="button" data-action="open-timer-plan" data-plan-id="${escapeHtml(data.id)}">打开计时器</button>
          </div>
        ` : ""}
      </article>
    `;
  }

  function renderPlanAdjustmentCard(envelope) {
    const data = envelope.data || {};
    const toTitle = data.toSnapshot?.title || data.toSnapshot?.trainingType || "";
    return `
      <article class="record-card">
        <div class="record-top">
          <h3>${escapeHtml(toTitle || "计划调整")}</h3>
          <span class="tag">${escapeHtml(data.adjustedBy || "adjusted")}</span>
        </div>
        ${data.reason ? `<p>${escapeHtml(data.reason)}</p>` : ""}
        ${data.adjustedAt ? `<p>${escapeHtml(formatLocalDateTime(data.adjustedAt))}</p>` : ""}
      </article>
    `;
  }

  function renderTimerSessionCard(envelope) {
    const data = envelope.data || {};
    const timerMeta = getTimerTypeMeta(data.trainingType);
    const legacyType = timerMeta.legacyType;
    const handling = getTimerSessionHandling(envelope);
    const details = [
      data.startedAt ? `开始 ${formatLocalDateTime(data.startedAt)}` : "",
      data.actualSeconds ? formatDuration(data.actualSeconds) : "",
      data.routineId ? `routine ${data.routineId}` : "",
      data.routineVersion ? `v${data.routineVersion}` : "",
      data.completion ? getTimerCompletionLabel(data.completion) : ""
    ].filter(Boolean).join(" · ");
    return `
      <article class="record-card timer-card timer-${handling.action}">
        <div class="record-top">
          <h3>${renderTypeIcon(legacyType, "record-type-icon")}${escapeHtml(timerMeta.label)} · ${escapeHtml(getTimerSessionTitle(data))}</h3>
          <span class="tag">${escapeHtml(handling.label)}</span>
        </div>
        ${details ? `<p>${escapeHtml(details)}</p>` : ""}
        ${data.notes ? `<p>${escapeHtml(String(data.notes))}</p>` : ""}
        ${renderTimerSessionActions(envelope)}
      </article>
    `;
  }

  function renderMetricCard(metric) {
    const painSummary = formatPainSummary(metric.pain);
    const measureSummary = formatBodyMeasureSummary(metric);
    return `
      <div class="record-card">
        <div class="record-top">
          <h3>状态记录</h3>
          <span class="tag">${escapeHtml(formatMetricTag(metric))}</span>
        </div>
        <p>疲劳 ${FATIGUE_META[metric.fatigue]}，昨晚睡眠 ${SLEEP_META[metric.sleepQuality]}，今日精力 ${metric.energy}/5。</p>
        ${measureSummary ? `<p>${escapeHtml(measureSummary)}</p>` : ""}
        ${painSummary ? `<p>不适：${escapeHtml(painSummary)}。</p>` : ""}
        ${metric.notes ? `<p>${escapeHtml(metric.notes)}</p>` : ""}
      </div>
    `;
  }

  function renderTimerSessionsPage() {
    const sessions = getFilteredTimerSessions();
    const selected = getSelectedTimerSession(sessions);
    return `
      <section class="content-page timer-page">
        ${renderPageHead("计时器记录")}
        ${renderTimerStats()}
        <div class="timer-workspace">
          <section class="panel timer-list-panel">
            <div class="panel-header timer-panel-header">
              <div>
                <h2 class="panel-title">最近计时器记录</h2>
                <p class="panel-subtitle">来自 home-training-timer 的事实记录</p>
              </div>
              ${renderTimerFilters()}
            </div>
            <div class="panel-body">${renderTimerSessionList(sessions)}</div>
          </section>
          <section class="panel timer-detail-panel">
            <div class="panel-header">
              <div>
                <h2 class="panel-title">记录详情</h2>
                <p class="panel-subtitle">只处理关联关系，不改写 timer_sessions</p>
              </div>
            </div>
            <div class="panel-body">${renderTimerSessionDetail(selected)}</div>
          </section>
        </div>
      </section>
    `;
  }

  function renderTimerStats() {
    const sessions = getAllTimerSessions();
    const today = todayISO();
    const todayPending = sessions.filter((item) => item.data?.date === today && getTimerSessionHandling(item).action === "pending").length;
    const recent7 = sessions.filter((item) => dateWithinDays(item.data?.date, today, 7)).length;
    const linked = sessions.filter((item) => ["linked", "converted"].includes(getTimerSessionHandling(item).action)).length;
    const short = sessions.filter((item) => isShortTimerSession(item.data)).length;
    return `
      <div class="timer-stat-grid">
        <div class="metric"><strong>${todayPending}</strong><span>今日未处理</span></div>
        <div class="metric"><strong>${recent7}</strong><span>最近 7 天</span></div>
        <div class="metric"><strong>${linked}</strong><span>已关联/转日志</span></div>
        <div class="metric"><strong>${short}</strong><span>过短/测试</span></div>
      </div>
    `;
  }

  function renderTimerFilters() {
    const filters = state.timerFilters;
    const typeOptions = getTimerTypeOptions();
    const statusOptions = [
      ["all", "全部状态"],
      ["pending", "未处理"],
      ["linked", "已关联"],
      ["converted", "已转训练日志"],
      ["ignored", "已忽略"]
    ];
    return `
      <div class="timer-filters">
        <label>
          <span>日期</span>
          <input type="date" data-timer-filter="date" value="${escapeHtml(filters.date || "")}">
        </label>
        <label>
          <span>类型</span>
          <select data-timer-filter="type">
            ${typeOptions.map(([value, label]) => `<option value="${escapeHtml(value)}" ${filters.type === value ? "selected" : ""}>${escapeHtml(label)}</option>`).join("")}
          </select>
        </label>
        <label>
          <span>状态</span>
          <select data-timer-filter="status">
            ${statusOptions.map(([value, label]) => `<option value="${escapeHtml(value)}" ${filters.status === value ? "selected" : ""}>${escapeHtml(label)}</option>`).join("")}
          </select>
        </label>
      </div>
    `;
  }

  function renderTimerSessionList(sessions) {
    if (!sessions.length) {
      return `<div class="empty-state">没有符合条件的计时器记录。</div>`;
    }
    return `
      <div class="timer-table">
        <div class="timer-table-head">
          <span>日期</span>
          <span>开始</span>
          <span>类型</span>
          <span>流程</span>
          <span>时长</span>
          <span>完成</span>
          <span>处理</span>
          <span>操作</span>
        </div>
        ${sessions.map(renderTimerSessionRow).join("")}
      </div>
    `;
  }

  function renderTimerSessionRow(envelope) {
    const data = envelope.data || {};
    const typeMeta = getTimerTypeMeta(data.trainingType);
    const handling = getTimerSessionHandling(envelope);
    const selected = state.timerFilters.selectedSessionId === data.id;
    const canConvert = canConvertTimerSession(data) && handling.action === "pending";
    return `
      <article class="timer-table-row ${selected ? "selected" : ""}" data-session-id="${escapeHtml(data.id)}">
        <button type="button" class="timer-row-main" data-action="select-timer-session" data-session-id="${escapeHtml(data.id)}">
          <span>${escapeHtml(data.date)}</span>
          <span>${escapeHtml(formatTimeOnly(data.startedAt))}</span>
          <span>${escapeHtml(typeMeta.label)}</span>
          <span>${escapeHtml(getTimerSessionTitle(data))}</span>
          <span>${escapeHtml(formatDuration(data.actualSeconds) || "-")}</span>
          <span>${escapeHtml(getTimerCompletionLabel(data.completion))}</span>
          <span><i class="timer-status-dot status-${handling.action}"></i>${escapeHtml(handling.label)}</span>
        </button>
        <div class="timer-row-actions">
          ${canConvert ? `<button type="button" data-action="convert-timer-session" data-session-id="${escapeHtml(data.id)}">转日志</button>` : ""}
          ${handling.action === "pending" ? `<button type="button" data-action="ignore-timer-session" data-session-id="${escapeHtml(data.id)}">忽略</button>` : ""}
        </div>
      </article>
    `;
  }

  function renderTimerSessionDetail(envelope) {
    if (!envelope) {
      return `<div class="empty-state">选择一条计时器记录查看详情。</div>`;
    }
    const data = envelope.data || {};
    const typeMeta = getTimerTypeMeta(data.trainingType);
    const handling = getTimerSessionHandling(envelope);
    const details = [
      ["日期", data.date],
      ["开始时间", formatLocalDateTime(data.startedAt)],
      ["结束时间", data.endedAt ? formatLocalDateTime(data.endedAt) : "-"],
      ["类型", typeMeta.label],
      ["流程", getTimerSessionTitle(data)],
      ["实际时长", formatDuration(data.actualSeconds) || "-"],
      ["完成状态", getTimerCompletionLabel(data.completion)],
      ["处理状态", handling.label],
      ["关联日志", handling.targetTrainingLogId || "-"]
    ];
    return `
      <div class="timer-detail">
        <div class="timer-detail-title">
          <h3>${escapeHtml(typeMeta.label)} · ${escapeHtml(getTimerSessionTitle(data))}</h3>
          <span class="tag">${escapeHtml(handling.label)}</span>
        </div>
        <dl class="timer-detail-grid">
          ${details.map(([label, value]) => `<div><dt>${escapeHtml(label)}</dt><dd>${escapeHtml(value)}</dd></div>`).join("")}
        </dl>
        ${data.notes ? `<p class="timer-note">${escapeHtml(String(data.notes))}</p>` : ""}
        ${renderTimerSessionActions(envelope)}
      </div>
    `;
  }

  function renderTimerSessionActions(envelope) {
    const data = envelope.data || {};
    const handling = getTimerSessionHandling(envelope);
    if (handling.action !== "pending") {
      return `<p class="timer-note">这条记录已经处理。如需修改，后续可在关联记录里增加编辑入口。</p>`;
    }
    const canConvert = canConvertTimerSession(data);
    return `
      <div class="button-row timer-action-row">
        ${canConvert ? `<button type="button" class="primary" data-action="convert-timer-session" data-session-id="${escapeHtml(data.id)}">转为训练日志</button>` : ""}
        <button type="button" data-action="link-timer-session" data-session-id="${escapeHtml(data.id)}">关联到已有训练日志</button>
        <button type="button" data-action="mark-timer-session-role" data-role="warmup" data-session-id="${escapeHtml(data.id)}">标记为热身</button>
        <button type="button" data-action="mark-timer-session-role" data-role="stretch" data-session-id="${escapeHtml(data.id)}">标记为拉伸/冷身</button>
        <button type="button" data-action="ignore-timer-session" data-session-id="${escapeHtml(data.id)}">忽略</button>
      </div>
      ${!canConvert ? `<p class="timer-note">这条记录更像辅助流程或测试记录，默认不建议转成正式训练日志。</p>` : ""}
    `;
  }

  function renderDataPanel() {
    const snapshotSize = JSON.stringify({
      workouts: state.workouts,
      bodyMetrics: state.bodyMetrics,
      records: state.records
    }).length;
    return `
      <div class="data-grid">
        <div class="metric"><strong>${state.workouts.length}</strong><span>训练记录</span></div>
        <div class="metric"><strong>${state.bodyMetrics.length}</strong><span>身体记录</span></div>
        <div class="metric"><strong>${Math.ceil(snapshotSize / 1024)}</strong><span>KB 本地数据</span></div>
      </div>
      <div class="button-row" style="margin-top: 14px;">
        <button type="button" class="primary" data-action="export">导出 JSON</button>
        <label class="file-button">
          导入 JSON
          <input type="file" accept="application/json,.json" data-action="import">
        </label>
        <button type="button" class="danger" data-action="restore-seed">恢复种子记录</button>
      </div>
    `;
  }

  function renderRecordList(records, className = "") {
    if (!records.length) {
      return `<div class="empty-state">暂无记录。</div>`;
    }
    return `<div class="record-list ${className}">${records.map(renderRecordCard).join("")}</div>`;
  }

  function renderWeeklyRecordTimeline(records) {
    const weeks = groupRecordsByWeek(records);
    return `
      <div class="records-timeline">
        ${weeks.map((week) => `
          <section class="week-group">
            <div class="week-head">
              <h2>${escapeHtml(week.label)}</h2>
              <span>${week.records.length} 条</span>
            </div>
            <div class="record-list timeline-record-list">
              ${week.records.map(renderRecordCard).join("")}
            </div>
          </section>
        `).join("")}
      </div>
    `;
  }

  function groupRecordsByWeek(records) {
    const map = new Map();
    records.forEach((record) => {
      const weekStart = getWeekStartISO(record.date);
      if (!map.has(weekStart)) {
        map.set(weekStart, {
          start: weekStart,
          label: formatWeekLabel(weekStart),
          records: []
        });
      }
      map.get(weekStart).records.push(record);
    });
    return Array.from(map.values()).sort((a, b) => b.start.localeCompare(a.start));
  }

  function getWeekStartISO(date) {
    const parsed = parseIsoDate(date);
    const offset = (parsed.getDay() + 6) % 7;
    parsed.setDate(parsed.getDate() - offset);
    return dateToISO(parsed);
  }

  function formatWeekLabel(weekStart) {
    const start = parseIsoDate(weekStart);
    const end = parseIsoDate(weekStart);
    end.setDate(end.getDate() + 6);
    return `${dateToISO(start).replace(/-/g, "/")} - ${formatShortDate(dateToISO(end))}`;
  }

  function renderRecordCard(record) {
    const meta = TYPE_META[record.type];
    const details = [
      record.durationSec ? formatDuration(record.durationSec) : "",
      record.distanceKm ? `${formatNumber(record.distanceKm, 2)} km` : "",
      record.avgHeartRate ? `均心 ${record.avgHeartRate}` : "",
      getPace(record) ? `配速 ${getPace(record)}` : ""
    ].filter(Boolean).join(" · ");

    return `
      <article class="record-card">
        <div class="record-top">
          <h3>${renderTypeIcon(record.type, "record-type-icon")}${record.date} ${escapeHtml(meta.label)}</h3>
          <span class="tag">${escapeHtml(getStatusLabel(record.status, record.type))}</span>
        </div>
        <p>${escapeHtml(details || "无运动数据")}</p>
        ${record.notes ? `<p>${escapeHtml(record.notes)}</p>` : ""}
      </article>
    `;
  }

  function formatPainSummary(pain) {
    const labels = {
      calf: "小腿",
      back: "腰背",
      wrist: "手腕",
      outerThigh: "大腿外侧"
    };
    return Object.entries(labels).map(([key, label]) => {
      const level = clampPain(pain && pain[key]);
      if (!level) return "";
      const meta = PAIN_LEVELS.find((item) => item.value === level);
      return `${label}${meta ? meta.label : level}`;
    }).filter(Boolean).join("、");
  }

  function formatMetricTag(metric) {
    if (metric.weightKg !== null && metric.weightKg !== undefined) return `${formatNumber(metric.weightKg, 1)} kg`;
    if (metric.waistCm !== null && metric.waistCm !== undefined) return `${formatNumber(metric.waistCm, 1)} cm`;
    if (metric.bodyFatPct !== null && metric.bodyFatPct !== undefined) return `${formatNumber(metric.bodyFatPct, 1)}%`;
    if (metric.muscleKg !== null && metric.muscleKg !== undefined) return `${formatNumber(metric.muscleKg, 1)} kg`;
    return FATIGUE_META[metric.fatigue];
  }

  function formatBodyMeasureSummary(metric) {
    return [
      metric.weightKg !== null && metric.weightKg !== undefined ? `体重 ${formatNumber(metric.weightKg, 1)} kg` : "",
      metric.waistCm !== null && metric.waistCm !== undefined ? `腰围 ${formatNumber(metric.waistCm, 1)} cm` : "",
      metric.bodyFatPct !== null && metric.bodyFatPct !== undefined ? `体脂率 ${formatNumber(metric.bodyFatPct, 1)}%` : "",
      metric.muscleKg !== null && metric.muscleKg !== undefined ? `肌肉量 ${formatNumber(metric.muscleKg, 1)} kg` : ""
    ].filter(Boolean).join("，");
  }

  function formatCalendarRecord(record) {
    const meta = TYPE_META[record.type];
    if (record.status === "skipped") return meta.label;
    if (record.status === "stretchOnly") return "只拉伸";
    if (record.distanceKm) return `${meta.label} ${formatNumber(record.distanceKm, 1)}km`;
    if (record.durationSec) return `${meta.label} ${Math.round(record.durationSec / 60)}分`;
    return meta.label;
  }

  function isCompletedRecord(record) {
    return record.type === "rest" || record.status === "completed" || record.status === "short" || record.status === "stretchOnly";
  }

  function renderSyncPanel() {
    const config = state.syncConfig || {};
    const dirtyCount = getDirtySharedRecords().length;
    const totalCount = getAllSharedRecords().length;
    const conflictCount = getConflictRecords().length;
    const lastSyncAt = config.lastSyncAt ? formatLocalDateTime(config.lastSyncAt) : "未连接";
    const apiBase = config.apiBase || "";
    const timerUrl = config.timerUrl || DEFAULT_TIMER_URL;
    const token = config.token || "";
    const timerToken = config.timerToken || "";
    const status = state.syncStatus.busy ? "云端读写中..." : state.syncStatus.lastResult || "未连接";
    const error = state.syncStatus.lastError;
    return `
      <form class="sync-form" id="sync-config-form">
        <label>
          <span>API 地址</span>
          <input type="url" name="apiBase" placeholder="https://your-worker.workers.dev/api" value="${escapeHtml(apiBase)}">
        </label>
        <label>
          <span>Timer 地址</span>
          <input type="url" name="timerUrl" placeholder="https://s2qtech.github.io/home-training-timer/" value="${escapeHtml(timerUrl)}">
        </label>
        <label>
          <span>身刻访问密钥</span>
          <input type="password" name="token" autocomplete="off" placeholder="Worker SHENK_TOKEN 或 ADMIN_TOKEN" value="${escapeHtml(token)}">
        </label>
        <label>
          <span>计时器访问密钥</span>
          <input type="password" name="timerToken" autocomplete="off" placeholder="Worker TIMER_TOKEN，仅本地保存，不进 URL" value="${escapeHtml(timerToken)}">
        </label>
        <div class="data-grid sync-metrics">
          <div class="metric"><strong>${dirtyCount}</strong><span>待写入云端</span></div>
          <div class="metric"><strong>${totalCount}</strong><span>共享记录</span></div>
          <div class="metric"><strong>${conflictCount}</strong><span>冲突</span></div>
          <div class="metric"><strong>${escapeHtml(lastSyncAt)}</strong><span>最近云端读写</span></div>
        </div>
        <div class="button-row">
          <button type="submit" class="primary">保存配置</button>
          <button type="button" data-action="sync-health">测试连接</button>
          <button type="button" data-action="sync-pull">读取云端</button>
          <button type="button" data-action="sync-push">写入云端</button>
          <button type="button" class="primary" data-action="sync-now">云端读写</button>
        </div>
        ${conflictCount ? `
          <div class="button-row">
            <button type="button" data-action="resolve-conflicts-cloud">使用云端</button>
            <button type="button" data-action="resolve-conflicts-local">使用本地覆盖</button>
          </div>
        ` : ""}
        <p class="sync-status ${error ? "sync-error" : ""}">${escapeHtml(error || status)}</p>
      </form>
    `;
  }

  function bindEvents() {
    app.querySelectorAll("[data-tab]").forEach((button) => {
      button.addEventListener("click", () => {
        state.activeTab = button.dataset.tab;
        if (state.activeTab === "data") state.dataView = "summary";
        state.detailOpen = false;
        state.editMode = false;
        clearEditorDrafts();
        state.message = "";
        render();
      });
    });

    app.querySelectorAll("[data-action='export']").forEach((button) => {
      button.addEventListener("click", exportJson);
    });

    app.querySelectorAll("[data-action='import']").forEach((input) => {
      input.addEventListener("change", importJson);
    });

    const syncForm = app.querySelector("#sync-config-form");
    if (syncForm) {
      syncForm.addEventListener("submit", handleSyncConfigSubmit);
    }

    app.querySelectorAll("[data-action='sync-health']").forEach((button) => {
      button.addEventListener("click", testSyncConnection);
    });

    app.querySelectorAll("[data-action='sync-pull']").forEach((button) => {
      button.addEventListener("click", pullCloudRecords);
    });

    app.querySelectorAll("[data-action='sync-push']").forEach((button) => {
      button.addEventListener("click", pushDirtyRecords);
    });

    app.querySelectorAll("[data-action='sync-now']").forEach((button) => {
      button.addEventListener("click", syncNow);
    });

    app.querySelectorAll("[data-timer-filter]").forEach((input) => {
      input.addEventListener("change", () => {
        state.timerFilters[input.dataset.timerFilter] = input.value || (input.dataset.timerFilter === "date" ? "" : "all");
        state.timerFilters.selectedSessionId = "";
        render();
      });
    });

    app.querySelectorAll("[data-date]").forEach((button) => {
      button.addEventListener("click", () => {
        state.selectedDate = button.dataset.date;
        state.visibleMonth = state.selectedDate.slice(0, 7);
        state.detailOpen = true;
        state.editMode = false;
        clearEditorDrafts();
        render();
      });
    });

    const calendarArea = app.querySelector("[data-calendar-area]");
    if (calendarArea) {
      calendarArea.addEventListener("click", (event) => {
        if (!state.detailOpen) return;
        if (event.target.closest("[data-date]")) return;
        state.detailOpen = false;
        state.editMode = false;
        clearEditorDrafts();
        render();
      });
    }

    app.querySelectorAll("[data-pain-option]").forEach((button) => {
      button.addEventListener("click", () => {
        const name = button.dataset.painOption;
        const input = app.querySelector(`[data-pain-input="${name}"]`);
        if (input) input.value = button.dataset.value;
        app.querySelectorAll(`[data-pain-option="${name}"]`).forEach((option) => {
          option.classList.toggle("active", option === button);
        });
      });
    });

    app.querySelectorAll("[data-range-input]").forEach((input) => {
      input.addEventListener("input", () => updateRangeOutput(input));
    });

    const trainingForm = app.querySelector("#training-form");
    if (trainingForm) {
      trainingForm.addEventListener("submit", handleTrainingSubmit);
      const typeSelect = trainingForm.querySelector('select[name="type"]');
      const statusSelect = trainingForm.querySelector('select[name="status"]');
      if (typeSelect && statusSelect) {
        statusSelect.dataset.statusType = typeSelect.value;
        typeSelect.addEventListener("change", () => {
          updateStatusOptions(statusSelect, typeSelect.value, statusSelect.value, statusSelect.dataset.statusType);
        });
      }
    }

    const statusForm = app.querySelector("#status-form");
    if (statusForm) {
      statusForm.addEventListener("submit", handleStatusSubmit);
    }

    bindAction("month-prev", () => shiftMonth(-1));
    bindAction("month-next", () => shiftMonth(1));
    bindAction("close-detail", closeDetail);
    bindAction("enable-edit", enableSelectedEdit);
    bindAction("cancel-edit", cancelSelectedEdit);
    bindAction("delete-date", deleteSelectedDate);
    bindAction("restore-seed", restoreSeed);
    bindAction("confirm-timer-session", confirmTimerSession);
    bindAction("select-timer-session", selectTimerSession);
    bindAction("convert-timer-session", convertTimerSession);
    bindAction("link-timer-session", linkTimerSessionToExistingLog);
    bindAction("mark-timer-session-role", markTimerSessionRole);
    bindAction("ignore-timer-session", ignoreTimerSession);
    bindAction("open-timer-plan", openTimerFromPlan);
    bindAction("open-all-records", openAllRecords);
    bindAction("back-to-data", backToDataSummary);
    bindAction("resolve-conflicts-cloud", resolveConflictsWithCloud);
    bindAction("resolve-conflicts-local", resolveConflictsWithLocal);
  }

  function bindAction(action, handler) {
    app.querySelectorAll(`[data-action="${action}"]`).forEach((element) => {
      element.addEventListener("click", () => handler(element));
    });
  }

  function captureEditorDrafts() {
    const trainingForm = app.querySelector("#training-form");
    const statusForm = app.querySelector("#status-form");
    if (!trainingForm && !statusForm) {
      clearEditorDrafts();
      return;
    }
    state.editorDrafts = {
      date: state.selectedDate,
      training: trainingForm ? Object.fromEntries(new FormData(trainingForm).entries()) : {},
      status: statusForm ? Object.fromEntries(new FormData(statusForm).entries()) : {}
    };
  }

  function getEditorDraft(date) {
    return state.editorDrafts && state.editorDrafts.date === date ? state.editorDrafts : null;
  }

  function clearEditorDrafts() {
    state.editorDrafts = null;
  }

  function draftValue(draft, key, fallback) {
    return draft && Object.prototype.hasOwnProperty.call(draft, key) ? draft[key] : fallback;
  }

  function updateRangeOutput(input) {
    const field = input.closest(".range-field");
    const output = field?.querySelector("[data-range-output]");
    if (!output) return;
    const kind = input.dataset.rangeKind;
    output.textContent = rangeDisplayText(kind, input.value);
    field.style.setProperty("--range-fill", `${rangeFill(Number(input.value), Number(input.min), Number(input.max))}%`);
    field.classList.remove("tone-good", "tone-steady", "tone-warning", "tone-risk");
    field.classList.add(`tone-${rangeTone(kind, input.value)}`);
  }

  function openAllRecords() {
    state.activeTab = "data";
    state.dataView = "records";
    state.detailOpen = false;
    state.editMode = false;
    clearEditorDrafts();
    render();
  }

  function backToDataSummary() {
    state.activeTab = "data";
    state.dataView = "summary";
    state.detailOpen = false;
    state.editMode = false;
    clearEditorDrafts();
    render();
  }

  async function handleTrainingSubmit(event) {
    event.preventDefault();
    captureEditorDrafts();
    const data = new FormData(event.currentTarget);
    const date = String(data.get("date"));
    if (!isIsoDate(date)) return;
    const existing = getWorkoutByDate(date);
    const now = new Date().toISOString();
    const durationMin = toNullableNumber(data.get("durationMin"));
    const workoutType = TYPE_META[String(data.get("type"))] ? String(data.get("type")) : "easyWalk";
    const workoutStatus = getStatusForType(workoutType, String(data.get("status")));
    const isRest = workoutType === "rest";
    const workout = normalizeWorkout({
      id: existing ? existing.id : makeId(),
      date,
      type: workoutType,
      status: workoutStatus,
      source: existing ? existing.source : "manual",
      durationSec: isRest || durationMin === null ? null : Math.round(durationMin * 60),
      distanceKm: isRest ? null : toNullableNumber(data.get("distanceKm")),
      avgHeartRate: isRest ? null : toNullableNumber(data.get("avgHeartRate")),
      fatigue: existing ? existing.fatigue : "normal",
      pain: existing ? existing.pain : { calf: 0, back: 0, wrist: 0, outerThigh: 0 },
      notes: String(data.get("trainingNotes") || "").trim(),
      createdAt: existing ? existing.createdAt : now,
      updatedAt: now
    });

    upsertWorkout(workout);
    state.selectedDate = date;
    state.visibleMonth = date.slice(0, 7);
    state.detailOpen = true;
    state.editMode = true;
    await saveSnapshot(`已保存训练 ${date}`);
    render();
  }

  async function handleStatusSubmit(event) {
    event.preventDefault();
    captureEditorDrafts();
    const data = new FormData(event.currentTarget);
    const date = String(data.get("date"));
    if (!isIsoDate(date)) return;
    const existing = getMetricByDate(date);
    const now = new Date().toISOString();
    const weightKg = toNullableNumber(data.get("weightKg"));
    const waistCm = toNullableNumber(data.get("waistCm"));
    const bodyFatPct = toNullableNumber(data.get("bodyFatPct"));
    const muscleKg = toNullableNumber(data.get("muscleKg"));
    const energy = toNullableNumber(data.get("energy"));
    const sleepQuality = sleepFromRange(data.get("sleepLevel"));
    const metric = normalizeBodyMetrics([{
      id: existing ? existing.id : makeId(),
      date,
      weightKg,
      waistCm,
      bodyFatPct,
      muscleKg,
      sleepQuality: SLEEP_META[sleepQuality] ? sleepQuality : "normal",
      energy: clamp(energy || 3, 1, 5),
      fatigue: fatigueFromRange(data.get("fatigueLevel")),
      pain: {
        calf: data.get("painCalf"),
        back: data.get("painBack"),
        wrist: data.get("painWrist"),
        outerThigh: data.get("painOuterThigh")
      },
      notes: String(data.get("statusNotes") || "").trim(),
      createdAt: existing ? existing.createdAt : now,
      updatedAt: now
    }])[0];
    if (!metric) return;
    upsertMetric(metric);
    state.selectedDate = date;
    state.visibleMonth = date.slice(0, 7);
    state.detailOpen = true;
    state.editMode = true;
    await saveSnapshot(`已保存状态 ${date}`);
    render();
  }

  async function confirmTimerSession(element) {
    await convertTimerSession(element);
  }

  function selectTimerSession(element) {
    state.timerFilters.selectedSessionId = element.dataset.sessionId || "";
    render();
  }

  async function convertTimerSession(element) {
    const envelope = findTimerSessionById(element.dataset.sessionId);
    if (!envelope) return;
    const session = envelope.data;
    const existingLink = getTimerSessionLink(session.id);
    if (existingLink) {
      state.message = "这条计时器记录已经处理过";
      render();
      return;
    }
    if (getTrainingLogForTimerSession(session.id)) {
      upsertTimerSessionLink(createTimerSessionLinkData(session, "converted", "main", getExistingTrainingLogIdForSession(session.id), "已存在正式训练记录"));
      await refreshAfterTimerSessionAction(session, "已补充计时器处理状态");
      return;
    }
    if (!canConvertTimerSession(session)) {
      const seconds = toNullableNumber(session.actualSeconds) || 0;
      const reason = seconds < 60 ? "这条记录少于 60 秒，更像测试记录。" : seconds < 300 ? "这条记录不足 5 分钟，更适合作为辅助流程。" : "这个类型默认不转正式训练日志。";
      if (!window.confirm(`${reason} 仍要创建正式训练日志吗？`)) return;
    }

    const sameDayWorkouts = getWorkoutsByDate(session.date);
    if (sameDayWorkouts.length) {
      const choice = window.prompt("这一天已有正式训练记录：输入 1 关联到已有记录，输入 2 创建新训练日志，输入 3 忽略本次计时器记录。", "2");
      if (choice === "1") {
        await linkTimerSessionToExistingLog(element);
        return;
      }
      if (choice === "3") {
        await ignoreTimerSession(element);
        return;
      }
      if (choice !== "2") return;
    }

    const workout = timerSessionToWorkout(session);
    if (!workout) return;
    upsertWorkout(workout);
    upsertTimerSessionLink(createTimerSessionLinkData(session, "converted", defaultTimerLinkRole(session.trainingType), workout.trainingLogId, "由计时器记录转为正式训练日志"));
    await refreshAfterTimerSessionAction(session, `已转为训练日志 ${session.date}`);
  }

  async function linkTimerSessionToExistingLog(element) {
    const envelope = findTimerSessionById(element.dataset.sessionId);
    if (!envelope) return;
    const session = envelope.data;
    if (getTimerSessionLink(session.id)) {
      state.message = "这条计时器记录已经处理过";
      render();
      return;
    }
    const target = chooseExistingWorkoutForTimerSession(session);
    if (!target) {
      state.message = "这一天还没有可关联的正式训练记录";
      render();
      return;
    }
    upsertTimerSessionLink(createTimerSessionLinkData(session, "linked", defaultTimerLinkRole(session.trainingType), getTrainingLogIdForWorkout(target), "关联到已有正式训练记录"));
    await refreshAfterTimerSessionAction(session, "已关联到已有训练日志");
  }

  async function markTimerSessionRole(element) {
    const envelope = findTimerSessionById(element.dataset.sessionId);
    if (!envelope) return;
    const session = envelope.data;
    if (getTimerSessionLink(session.id)) {
      state.message = "这条计时器记录已经处理过";
      render();
      return;
    }
    const role = TIMER_LINK_ROLE_META[element.dataset.role] ? element.dataset.role : defaultTimerLinkRole(session.trainingType);
    const target = findDefaultWorkoutForTimerSession(session);
    upsertTimerSessionLink(createTimerSessionLinkData(session, "linked", role, target ? getTrainingLogIdForWorkout(target) : null, `${TIMER_LINK_ROLE_META[role] || "辅助流程"}记录`));
    await refreshAfterTimerSessionAction(session, `已标记为${TIMER_LINK_ROLE_META[role] || "辅助流程"}`);
  }

  async function ignoreTimerSession(element) {
    const envelope = findTimerSessionById(element.dataset.sessionId);
    if (!envelope) return;
    const session = envelope.data;
    if (getTimerSessionLink(session.id)) {
      state.message = "这条计时器记录已经处理过";
      render();
      return;
    }
    upsertTimerSessionLink(createTimerSessionLinkData(session, "ignored", "note", null, "用户忽略"));
    await refreshAfterTimerSessionAction(session, "已忽略计时器记录");
  }

  function chooseExistingWorkoutForTimerSession(session) {
    const workouts = getWorkoutsByDate(session.date);
    if (!workouts.length) return null;
    if (workouts.length === 1) return workouts[0];
    const options = workouts.map((item, index) => `${index + 1}. ${TYPE_META[item.type]?.label || item.type} ${formatDuration(item.durationSec) || ""} ${getStatusLabel(item.status, item.type)}`).join("\n");
    const answer = window.prompt(`选择要关联的正式训练记录：\n${options}`, "1");
    const index = Number(answer) - 1;
    return workouts[index] || null;
  }

  function findDefaultWorkoutForTimerSession(session) {
    const workouts = getWorkoutsByDate(session.date).filter((item) => item.type !== "rest");
    return workouts[0] || null;
  }

  function getTrainingLogIdForWorkout(workout) {
    return workout?.trainingLogId || workoutToTrainingLogData(workout)?.id || null;
  }

  function getExistingTrainingLogIdForSession(sessionId) {
    const existing = getTrainingLogForTimerSession(sessionId);
    if (!existing) return null;
    return existing.data?.id || getTrainingLogIdForWorkout(existing);
  }

  function createTimerSessionLinkData(session, action, role, targetTrainingLogId = null, note = "") {
    const now = new Date().toISOString();
    return {
      id: `timer_link_${safeIdPart(session.id)}`,
      timerSessionId: session.id,
      date: session.date,
      action,
      targetTrainingLogId,
      role: defaultTimerLinkRole(role),
      note,
      createdAt: now,
      updatedAt: now
    };
  }

  function upsertTimerSessionLink(data) {
    upsertSharedEnvelope(state.records, "timer_session_links", data, getTimerSessionLink(data.timerSessionId));
  }

  async function refreshAfterTimerSessionAction(session, message) {
    state.timerFilters.selectedSessionId = session.id;
    state.selectedDate = session.date;
    state.visibleMonth = session.date.slice(0, 7);
    if (state.activeTab === "calendar") state.detailOpen = true;
    state.editMode = false;
    clearEditorDrafts();
    await saveSnapshot(message);
    render();
  }

  function timerSessionToWorkout(session) {
    const now = new Date().toISOString();
    const type = toLegacyTrainingType(session.trainingType || session.type);
    return normalizeWorkout({
      id: makeId(),
      trainingLogId: `log_${session.date}_${safeIdPart(session.id)}`,
      timerSessionId: session.id,
      timerSessionIds: [session.id],
      dailyPlanItemId: session.dailyPlanItemId || null,
      date: session.date,
      type,
      status: timerCompletionToLegacyStatus(session),
      source: "timer",
      durationSec: toNullableNumber(session.actualSeconds),
      distanceKm: null,
      avgHeartRate: null,
      fatigue: "normal",
      pain: { calf: 0, back: 0, wrist: 0, outerThigh: 0 },
      notes: timerSessionNote(session),
      rawJson: { timerSession: session },
      createdAt: now,
      updatedAt: now
    });
  }

  function timerCompletionToLegacyStatus(session) {
    const completion = String(session.completion || "").toLowerCase();
    if (completion === "completed") return "completed";
    if (completion === "stopped") return toNullableNumber(session.actualSeconds) ? "short" : "skipped";
    return toNullableNumber(session.actualSeconds) ? "short" : "skipped";
  }

  function timerSessionNote(session) {
    return [
      session.routineId ? `routineId: ${session.routineId}` : "",
      session.routineVersion ? `routineVersion: ${session.routineVersion}` : "",
      session.completion ? `completion: ${session.completion}` : "",
      session.notes || ""
    ].filter(Boolean).join("；");
  }

  function mergeNotes(current, addition) {
    const left = String(current || "").trim();
    const right = String(addition || "").trim();
    if (!left) return right;
    if (!right || left.includes(right)) return left;
    return `${left}\n${right}`;
  }

  function safeIdPart(value) {
    return String(value || makeId()).replace(/[^a-zA-Z0-9_-]/g, "_");
  }

  function openTimerFromPlan(element) {
    const envelope = (state.records.daily_plan_items || []).find((item) => item.data?.id === element.dataset.planId || item.id === element.dataset.planId);
    if (!envelope) return;
    openTimerUrl(envelope.data || {});
  }

  function openTimerUrl(planItem) {
    const url = new URL(normalizeTimerUrl(state.syncConfig.timerUrl), window.location.href);
    const params = url.searchParams;
    if (planItem.routineId) params.set("routineId", planItem.routineId);
    if (planItem.date) params.set("date", planItem.date);
    if (planItem.id) params.set("dailyPlanItemId", planItem.id);
    if (planItem.sourcePlanId) params.set("planTemplateId", planItem.sourcePlanId);
    if (planItem.trainingType) params.set("trainingType", planItem.trainingType);
    params.set("source", "shenk");
    params.set("cloudApiBase", normalizeSyncApiBase(state.syncConfig.apiBase || DEFAULT_CLOUD_API_BASE));
    const timerOptions = planItem.timerOptions && typeof planItem.timerOptions === "object" ? planItem.timerOptions : {};
    Object.entries(timerOptions).forEach(([key, value]) => {
      if (value === null || value === undefined || typeof value === "object") return;
      params.set(key, String(value));
    });
    const timerWindow = window.open(url.toString(), "_blank");
    if (timerWindow && state.syncConfig.timerToken) {
      const targetOrigin = url.origin;
      const payload = {
        type: "shenk:cloud-config",
        cloudApiBase: normalizeSyncApiBase(state.syncConfig.apiBase || DEFAULT_CLOUD_API_BASE),
        timerToken: state.syncConfig.timerToken
      };
      window.setTimeout(() => {
        try {
          timerWindow.postMessage(payload, targetOrigin);
        } catch (error) {
          state.message = "计时器配置消息发送失败，请在计时器内保留本地配置";
          render();
        }
      }, 800);
    }
  }

  function upsertMetric(metric) {
    state.bodyMetrics = state.bodyMetrics.filter((item) => item.date !== metric.date).concat(metric).sort((a, b) => a.date.localeCompare(b.date));
  }

  async function deleteSelectedDate() {
    if (!window.confirm(`删除 ${state.selectedDate} 的训练和身体记录？`)) return;
    state.workouts = state.workouts.filter((item) => item.date !== state.selectedDate);
    state.bodyMetrics = state.bodyMetrics.filter((item) => item.date !== state.selectedDate);
    removeSharedRecordsByDate(["training_logs", "body_metrics"], state.selectedDate);
    state.editMode = false;
    clearEditorDrafts();
    await saveSnapshot(`已删除 ${state.selectedDate}`);
    render();
  }

  async function restoreSeed() {
    if (!window.confirm("恢复种子记录会替换当前本地数据。继续？")) return;
    state.workouts = seedWorkouts();
    state.bodyMetrics = [];
    state.records = createEmptySharedRecords();
    state.selectedDate = todayISO();
    state.visibleMonth = state.selectedDate.slice(0, 7);
    state.detailOpen = false;
    state.editMode = false;
    clearEditorDrafts();
    await saveSnapshot("已恢复历史种子记录");
    render();
  }

  function upsertWorkout(workout) {
    const key = workoutKey(workout);
    state.workouts = state.workouts
      .filter((item) => workoutKey(item) !== key)
      .concat(workout)
      .sort((a, b) => a.date.localeCompare(b.date) || workoutKey(a).localeCompare(workoutKey(b)));
  }

  function workoutKey(workout) {
    if (!workout) return "";
    return workout.trainingLogId || workout.timerSessionId || workout.id || workout.date;
  }

  function shiftMonth(delta) {
    const [year, month] = state.visibleMonth.split("-").map(Number);
    const next = new Date(year, month - 1 + delta, 1);
    state.visibleMonth = `${next.getFullYear()}-${pad(next.getMonth() + 1)}`;
    render();
  }

  function closeDetail() {
    state.detailOpen = false;
    state.editMode = false;
    clearEditorDrafts();
    render();
  }

  function enableSelectedEdit() {
    clearEditorDrafts();
    state.editMode = true;
    render();
  }

  function cancelSelectedEdit() {
    state.editMode = false;
    clearEditorDrafts();
    render();
  }

  function exportJson() {
    const payload = {
      ...buildSnapshot(),
      exportedAt: new Date().toISOString()
    };
    const blob = new Blob([JSON.stringify(payload, null, 2)], { type: "application/json" });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = `training-assistant-v2-${todayISO()}.json`;
    document.body.appendChild(link);
    link.click();
    link.remove();
    URL.revokeObjectURL(url);
  }

  function importJson(event) {
    const file = event.target.files && event.target.files[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = async () => {
      try {
        const payload = JSON.parse(String(reader.result));
        const hasLegacyPayload = Array.isArray(payload.workouts) || Array.isArray(payload.bodyMetrics);
        const hasSharedPayload = hasSharedRecordPayload(payload);
        if ((!hasLegacyPayload && !hasSharedPayload) || !window.confirm("导入会更新当前本地数据。继续？")) {
          event.target.value = "";
          return;
        }
        const snapshot = normalizeSnapshot(payload);
        if (Array.isArray(payload.workouts)) {
          state.workouts = snapshot.workouts;
        } else if (snapshot.workouts.length) {
          state.workouts = mergeWorkoutsByDate(state.workouts, snapshot.workouts);
        }
        if (Array.isArray(payload.bodyMetrics)) {
          state.bodyMetrics = snapshot.bodyMetrics;
        } else if (snapshot.bodyMetrics.length) {
          state.bodyMetrics = mergeBodyMetricsByDate(state.bodyMetrics, snapshot.bodyMetrics);
        }
        state.records = hasSharedPayload ? mergeSharedRecords(state.records, snapshot.records) : snapshot.records;
        await saveSnapshot("JSON 已导入");
        render();
      } catch (error) {
        state.message = "JSON 导入失败";
        render();
      } finally {
        event.target.value = "";
      }
    };
    reader.readAsText(file);
  }

  function loadSyncConfig() {
    try {
      const raw = window.localStorage.getItem(SYNC_CONFIG_KEY);
      const config = raw ? JSON.parse(raw) : {};
      return {
        apiBase: normalizeSyncApiBase(config.apiBase || DEFAULT_CLOUD_API_BASE),
        timerUrl: normalizeTimerUrl(config.timerUrl || DEFAULT_TIMER_URL),
        token: config.token || "",
        timerToken: config.timerToken || "",
        lastPullAt: config.lastPullAt || null,
        lastPushAt: config.lastPushAt || null,
        lastSyncAt: config.lastSyncAt || null
      };
    } catch (error) {
      return { apiBase: DEFAULT_CLOUD_API_BASE, timerUrl: DEFAULT_TIMER_URL, token: "", timerToken: "", lastPullAt: null, lastPushAt: null, lastSyncAt: null };
    }
  }

  function saveSyncConfig(config) {
    const next = {
      ...state.syncConfig,
      ...config,
      apiBase: normalizeSyncApiBase(config.apiBase ?? state.syncConfig.apiBase),
      timerUrl: normalizeTimerUrl(config.timerUrl ?? state.syncConfig.timerUrl)
    };
    state.syncConfig = next;
    window.localStorage.setItem(SYNC_CONFIG_KEY, JSON.stringify(next));
  }

  function normalizeSyncApiBase(value) {
    const next = String(value || "").trim().replace(/\/+$/, "");
    if (!next) return "";
    return next.endsWith("/api") ? next : `${next}/api`;
  }

  function normalizeTimerUrl(value) {
    const next = String(value || "").trim();
    if (!next) return DEFAULT_TIMER_URL;
    return next;
  }

  function getAllSharedRecords() {
    return SHARED_ENTITIES.flatMap((entity) => Array.isArray(state.records[entity]) ? state.records[entity] : []);
  }

  function getConflictRecords() {
    return getAllSharedRecords().filter((item) => item && item.conflict);
  }

  function getDirtySharedRecords() {
    return getAllSharedRecords().filter((item) => (
      item &&
      SHENK_WRITE_ENTITIES.includes(item.entity) &&
      item.syncState !== "clean" &&
      !item.conflict
    ));
  }

  async function handleSyncConfigSubmit(event) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    saveSyncConfig({
      apiBase: String(data.get("apiBase") || ""),
      timerUrl: String(data.get("timerUrl") || ""),
      token: String(data.get("token") || ""),
      timerToken: String(data.get("timerToken") || "")
    });
    state.syncStatus.lastResult = "云数据库配置已保存";
    state.syncStatus.lastError = "";
    render();
  }

  async function testSyncConnection() {
    await runSyncTask(async () => {
      const result = await syncRequest("/health", { method: "GET", auth: false });
      state.syncStatus.lastResult = result.ok ? `连接正常：${result.service || "shenke-cloud-db"}` : "连接返回异常";
      state.syncStatus.lastError = "";
    });
  }

  async function syncNow() {
    await runSyncTask(async () => {
      await doPullCloudRecords();
      await doPushDirtyRecords();
      await doPullCloudRecords();
      const now = new Date().toISOString();
      saveSyncConfig({ lastSyncAt: now });
      state.syncStatus.lastResult = "云端读写完成";
      state.syncStatus.lastError = "";
      await saveSnapshot();
    });
  }

  async function pullCloudRecords(options = {}) {
    const shouldRender = !options.silent;
    await runSyncTask(async () => {
      await doPullCloudRecords();
    }, shouldRender);
  }

  async function pushDirtyRecords(options = {}) {
    const shouldRender = !options.silent;
    await runSyncTask(async () => {
      await doPushDirtyRecords();
    }, shouldRender);
  }

  async function doPullCloudRecords() {
      const result = await syncRequest("/records/query", {
      body: {
        deviceId: getDeviceId(),
        since: state.syncConfig.lastPullAt || null,
        entities: SHARED_ENTITIES
      }
    });
    const records = Array.isArray(result.records) ? result.records : [];
    if (records.length) {
      state.records = mergeSharedRecords(state.records, recordsArrayToBucket(records));
      syncLegacyFromSharedRecords();
    }
    const now = result.serverTime || new Date().toISOString();
    saveSyncConfig({ lastPullAt: now, lastSyncAt: now });
    const pendingTimers = countPendingTimerSessions();
    state.syncStatus.lastResult = `已读取 ${records.length} 条云端记录${pendingTimers ? `，${pendingTimers} 条计时器记录待确认` : ""}`;
    state.syncStatus.lastError = "";
    await saveSnapshot();
  }

  async function doPushDirtyRecords() {
    syncSharedRecordsFromLegacy();
    const records = getDirtySharedRecords();
    if (!records.length) {
      const now = new Date().toISOString();
      saveSyncConfig({ lastPushAt: now, lastSyncAt: now });
      state.syncStatus.lastResult = "没有待写入云端的记录";
      state.syncStatus.lastError = "";
      await saveSnapshot();
      return;
    }
    const result = await syncRequest("/records/upsert", {
      body: {
        deviceId: getDeviceId(),
        records: records.map((item) => ({
          entity: item.entity,
          id: item.id,
          baseRevision: item.revision || 0,
          data: item.data,
          revision: item.revision || 1,
          deviceId: item.deviceId || getDeviceId(),
          createdAt: item.createdAt,
          updatedAt: item.updatedAt,
          deletedAt: item.deletedAt || null
        }))
      }
    });
    markAcceptedRecords(result.accepted || []);
    markConflictRecords(result.conflicts || []);
    const now = result.serverTime || new Date().toISOString();
    saveSyncConfig({ lastPushAt: now, lastSyncAt: now });
    state.syncStatus.lastResult = `已写入 ${result.accepted?.length || 0} 条，冲突 ${result.conflicts?.length || 0} 条`;
    state.syncStatus.lastError = "";
    await saveSnapshot();
  }

  async function runSyncTask(task, shouldRender = true) {
    if (state.syncStatus.busy) return;
    state.syncStatus.busy = true;
    state.syncStatus.lastError = "";
    if (shouldRender) render();
    try {
      await task();
    } catch (error) {
      state.syncStatus.lastError = error.message || "云端读写失败";
      state.syncStatus.lastResult = "";
    } finally {
      state.syncStatus.busy = false;
      if (shouldRender) render();
    }
  }

  async function syncRequest(path, options = {}) {
    const apiBase = normalizeSyncApiBase(state.syncConfig.apiBase);
    if (!apiBase) throw new Error("请先填写云数据库 API 地址");
    const headers = { "Content-Type": "application/json" };
    const needsAuth = options.auth !== false;
    if (needsAuth) {
      if (!state.syncConfig.token) throw new Error("请先填写身刻访问密钥");
      headers.Authorization = `Bearer ${state.syncConfig.token}`;
    }
    headers["X-Shenke-Device-Id"] = getDeviceId();
    const response = await fetch(`${apiBase}${path}`, {
      method: options.method || "POST",
      headers,
      body: options.body ? JSON.stringify(options.body) : undefined
    });
    const text = await response.text();
    let payload = null;
    try {
      payload = text ? JSON.parse(text) : {};
    } catch (error) {
      throw new Error(`云端返回非 JSON：${response.status}`);
    }
    if (!response.ok || payload.ok === false) {
      throw new Error(payload.error || `请求失败：${response.status}`);
    }
    return payload;
  }

  function recordsArrayToBucket(records) {
    const bucket = createEmptySharedRecords();
    records.forEach((item) => {
      if (!item || !SHARED_ENTITIES.includes(item.entity)) return;
      bucket[item.entity].push({
        ...item,
        syncState: "clean",
        lastSyncedAt: item.updatedAt || new Date().toISOString()
      });
    });
    return bucket;
  }

  function markAcceptedRecords(accepted) {
    const now = new Date().toISOString();
    accepted.forEach((item) => {
      if (!item || !SHARED_ENTITIES.includes(item.entity)) return;
      const list = state.records[item.entity] || [];
      const record = list.find((entry) => entry.id === item.id);
      if (!record) return;
      record.revision = item.revision || record.revision;
      record.updatedAt = item.updatedAt || record.updatedAt;
      record.lastSyncedAt = item.updatedAt || now;
      record.syncState = "clean";
      record.conflict = null;
    });
  }

  function markConflictRecords(conflicts) {
    conflicts.forEach((item) => {
      if (!item || !item.serverRecord || !SHARED_ENTITIES.includes(item.entity)) return;
      const list = state.records[item.entity] || [];
      const record = list.find((entry) => entry.id === item.id);
      if (!record) return;
      record.syncState = "conflict";
      record.conflict = {
        reason: item.reason || "server_revision_newer",
        serverRecord: item.serverRecord,
        happenedAt: new Date().toISOString()
      };
    });
  }

  async function resolveConflictsWithCloud() {
    const conflicts = getConflictRecords();
    if (!conflicts.length) return;
    if (!window.confirm(`使用云端版本替换 ${conflicts.length} 条冲突记录？`)) return;
    conflicts.forEach((record) => {
      const serverRecord = record.conflict?.serverRecord;
      const normalized = normalizeSharedEnvelope(record.entity, serverRecord);
      if (!normalized) return;
      normalized.syncState = "clean";
      normalized.lastSyncedAt = normalized.updatedAt || new Date().toISOString();
      normalized.conflict = null;
      state.records[record.entity] = (state.records[record.entity] || [])
        .filter((item) => item.id !== normalized.id)
        .concat(normalized)
        .sort(compareSharedEnvelopes);
    });
    syncLegacyFromSharedRecords();
    state.syncStatus.lastResult = "已使用云端版本处理冲突";
    state.syncStatus.lastError = "";
    await saveSnapshot();
    render();
  }

  async function resolveConflictsWithLocal() {
    const conflicts = getConflictRecords();
    if (!conflicts.length) return;
    if (!window.confirm(`使用本地版本覆盖 ${conflicts.length} 条云端冲突记录？`)) return;
    const now = new Date().toISOString();
    conflicts.forEach((record) => {
      const serverRevision = toNullableNumber(record.conflict?.serverRecord?.revision);
      if (serverRevision) record.revision = serverRevision;
      record.updatedAt = now;
      if (record.data) record.data.updatedAt = now;
      record.syncState = "dirty";
      record.conflict = null;
    });
    state.syncStatus.lastResult = "已标记本地版本覆盖，下一次写入云端生效";
    state.syncStatus.lastError = "";
    await saveSnapshot();
    render();
  }

  function syncLegacyFromSharedRecords() {
    const cloudWorkouts = normalizeWorkouts(state.records.training_logs.map(trainingLogEnvelopeToWorkout).filter(Boolean));
    const cloudMetrics = normalizeBodyMetrics(state.records.body_metrics.map(bodyMetricEnvelopeToLegacy).filter(Boolean));
    if (cloudWorkouts.length) state.workouts = mergeWorkoutsByDate(state.workouts, cloudWorkouts);
    if (cloudMetrics.length) state.bodyMetrics = mergeBodyMetricsByDate(state.bodyMetrics, cloudMetrics);
  }

  function formatLocalDateTime(value) {
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return String(value || "");
    return `${date.getMonth() + 1}/${date.getDate()} ${pad(date.getHours())}:${pad(date.getMinutes())}`;
  }

  function formatTimeOnly(value) {
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return "";
    return `${pad(date.getHours())}:${pad(date.getMinutes())}`;
  }

  function getRecommendation(date, sourceWorkouts = state.workouts) {
    const last7 = getWindowWorkoutsFrom(sourceWorkouts, date, 7).filter(isCompletedRecord);
    const recent = [...sourceWorkouts].filter((item) => item.date < date).sort((a, b) => b.date.localeCompare(a.date));
    const yesterday = recent[0] || null;
    const strengthCount = last7.filter((item) => item.type === "strength").length;
    const aerobicCount = last7.filter((item) => ["easyWalk", "qualityWalk", "indoorCardio"].includes(item.type)).length;
    const qualityCount = last7.filter((item) => item.type === "qualityWalk").length;
    const recoveryCount = last7.filter((item) => ["recovery", "rest"].includes(item.type)).length;
    const risk = getRecentRisk(date, sourceWorkouts);

    if (risk.level === "high") {
      return {
        type: "recovery",
        label: "降级优先",
        title: "恢复或轻拉伸",
        minutes: 12,
        reasons: [
          risk.reason,
          "状态文件要求疼痛、疲劳或异常体感优先降级。",
          "今天只保留低压力活动，不补课。"
        ]
      };
    }

    if (yesterday && ["qualityWalk", "strength"].includes(yesterday.type)) {
      return {
        type: "easyWalk",
        label: "承接昨天",
        title: "普通走或室内有氧",
        minutes: 35,
        reasons: [
          `上一条记录是${TYPE_META[yesterday.type].label}，今天避免连续偏硬。`,
          `近 7 天力量 ${strengthCount} 次，有氧 ${aerobicCount} 次。`,
          "保持可交谈强度，完成比拉高强度更重要。"
        ]
      };
    }

    if (strengthCount < 2 && !risk.backOrWrist) {
      return {
        type: "strength",
        label: "补足力量",
        title: "短版力量训练",
        minutes: 32,
        reasons: [
          `近 7 天力量 ${strengthCount} 次，目标约 2 次。`,
          "避开手腕承重动作，保护腰背。",
          "强度不按出汗判断，以动作稳定为准。"
        ]
      };
    }

    if (aerobicCount < 3) {
      return {
        type: "easyWalk",
        label: "补足有氧",
        title: "普通走",
        minutes: 40,
        reasons: [
          `近 7 天有氧 ${aerobicCount} 次，目标约 3 次。`,
          "不追求配速，保持轻松和稳定。",
          "若小腿发紧，改为室内恢复。"
        ]
      };
    }

    if (qualityCount < 1 && aerobicCount >= 2 && !risk.calf) {
      return {
        type: "qualityWalk",
        label: "可控提高",
        title: "提高走",
        minutes: 45,
        reasons: [
          "近 7 天还没有提高走，且基础有氧已有铺垫。",
          "只做可控提高，不做冲刺。",
          "小腿或腰背不适时立即降级。"
        ]
      };
    }

    return {
      type: recoveryCount < 1 ? "recovery" : "easyWalk",
      label: "维持节奏",
      title: recoveryCount < 1 ? "恢复日" : "普通走",
      minutes: recoveryCount < 1 ? 15 : 35,
      reasons: [
        `近 7 天力量 ${strengthCount} 次，有氧 ${aerobicCount} 次，提高走 ${qualityCount} 次。`,
        "当前不需要补课，按身体状态维持节奏。",
        "疲劳上来时改为恢复。"
      ]
    };
  }

  function getRecentRisk(date, sourceWorkouts = state.workouts) {
    const recentWorkout = [...sourceWorkouts]
      .filter((item) => item.date < date && item.source !== "forecast")
      .sort((a, b) => b.date.localeCompare(a.date))[0] || null;
    const recentMetric = [...state.bodyMetrics]
      .filter((item) => item.date < date)
      .sort((a, b) => b.date.localeCompare(a.date))[0] || null;
    const recent = recentMetric && (!recentWorkout || recentMetric.date >= recentWorkout.date) ? recentMetric : recentWorkout;
    if (!recent) return { level: "normal", calf: false, backOrWrist: false, reason: "" };
    const sourceName = recent === recentMetric ? "最近状态" : "最近记录";
    const pain = recent.pain || {};
    const calf = pain.calf > 0;
    const backOrWrist = pain.back > 0 || pain.wrist > 0;
    if (recent.fatigue === "severe" || recent.fatigue === "high") {
      return { level: "high", calf, backOrWrist, reason: `${sourceName}疲劳为${FATIGUE_META[recent.fatigue]}。` };
    }
    if (calf) {
      return { level: "high", calf, backOrWrist, reason: `${sourceName}有小腿不适。` };
    }
    if (pain.back > 1 || pain.wrist > 1) {
      return { level: "high", calf, backOrWrist, reason: `${sourceName}有腰背或手腕不适。` };
    }
    return { level: "normal", calf, backOrWrist, reason: "" };
  }

  function getWindowWorkouts(date, days) {
    return getWindowWorkoutsFrom(state.workouts, date, days);
  }

  function getWindowWorkoutsFrom(sourceWorkouts, date, days) {
    const end = parseIsoDate(date);
    const start = new Date(end);
    start.setDate(end.getDate() - days + 1);
    return sourceWorkouts.filter((item) => {
      const itemDate = parseIsoDate(item.date);
      return itemDate >= start && itemDate <= end;
    });
  }

  function getWorkoutByDate(date) {
    return getWorkoutsByDate(date)[0] || null;
  }

  function getMetricByDate(date) {
    return state.bodyMetrics.find((item) => item.date === date) || null;
  }

  function getWorkoutsByDate(date) {
    return state.workouts.filter((item) => item.date === date).sort((a, b) => {
      const left = a.createdAt || a.updatedAt || "";
      const right = b.createdAt || b.updatedAt || "";
      return left.localeCompare(right);
    });
  }

  function getPlanItemsByDate(date) {
    return (state.records.daily_plan_items || [])
      .filter((item) => !item.deletedAt && item.data?.date === date)
      .sort(compareSharedEnvelopes);
  }

  function getPlanAdjustmentsByDate(date) {
    return (state.records.plan_adjustments || [])
      .filter((item) => !item.deletedAt && item.data?.date === date)
      .sort(compareSharedEnvelopes);
  }

  function getTimerSessionsForDate(date) {
    return (state.records.timer_sessions || [])
      .filter((item) => !item.deletedAt && item.data?.date === date)
      .sort(compareTimerSessionsForDate);
  }

  function compareTimerSessionsForDate(a, b) {
    return String(a.data?.startedAt || a.updatedAt || "").localeCompare(String(b.data?.startedAt || b.updatedAt || ""));
  }

  function getAllTimerSessions() {
    return (state.records.timer_sessions || [])
      .filter((item) => !item.deletedAt)
      .sort((a, b) => String(b.data?.startedAt || b.updatedAt || "").localeCompare(String(a.data?.startedAt || a.updatedAt || "")));
  }

  function getFilteredTimerSessions() {
    const filters = state.timerFilters;
    return getAllTimerSessions().filter((item) => {
      const data = item.data || {};
      const handling = getTimerSessionHandling(item);
      if (filters.date && data.date !== filters.date) return false;
      if (filters.type !== "all" && data.trainingType !== filters.type) return false;
      if (filters.status !== "all" && handling.action !== filters.status) return false;
      return true;
    });
  }

  function getSelectedTimerSession(sessions = getFilteredTimerSessions()) {
    const selectedId = state.timerFilters.selectedSessionId;
    return sessions.find((item) => item.data?.id === selectedId || item.id === selectedId) || sessions[0] || null;
  }

  function getTimerTypeOptions() {
    const observed = new Set(getAllTimerSessions().map((item) => item.data?.trainingType).filter(Boolean));
    const defaultTypes = ["warmup", "stretch", "recovery", "strength", "indoor_cardio", "travel_strength", "seat_recovery", "easy_walk", "quality_walk"];
    const values = ["all", ...defaultTypes, ...Array.from(observed).filter((value) => !defaultTypes.includes(value)).sort()];
    return values.map((value) => [value, value === "all" ? "全部类型" : getTimerTypeMeta(value).label]);
  }

  function getTimerTypeMeta(type) {
    const value = normalizeTimerTrainingType(type);
    if (TIMER_TYPE_META[value]) return TIMER_TYPE_META[value];
    const legacyType = toLegacyTrainingType(value);
    return {
      label: TYPE_META[legacyType]?.label || value || "计时器",
      legacyType,
      defaultRole: "note",
      canConvert: false
    };
  }

  function getTimerSessionTitle(session) {
    return session.title || session.routineTitle || session.routineName || session.routineId || "未命名流程";
  }

  function getTimerSessionLink(sessionId) {
    if (!sessionId) return null;
    return (state.records.timer_session_links || [])
      .filter((item) => !item.deletedAt && item.data?.timerSessionId === sessionId)
      .sort(compareSharedEnvelopes)
      .slice(-1)[0] || null;
  }

  function getTimerSessionHandling(envelope) {
    const session = envelope?.data || envelope || {};
    const link = getTimerSessionLink(session.id);
    if (link) {
      const data = link.data || {};
      return {
        action: data.action,
        label: TIMER_LINK_ACTION_META[data.action] || data.action,
        role: data.role || "note",
        roleLabel: TIMER_LINK_ROLE_META[data.role] || data.role || "",
        targetTrainingLogId: data.targetTrainingLogId || null,
        link
      };
    }
    const linkedLog = getTrainingLogForTimerSession(session.id);
    if (linkedLog) {
      const targetTrainingLogId = linkedLog.data?.id || linkedLog.trainingLogId || null;
      return {
        action: "converted",
        label: TIMER_LINK_ACTION_META.converted,
        role: "main",
        roleLabel: TIMER_LINK_ROLE_META.main,
        targetTrainingLogId,
        link: null
      };
    }
    return {
      action: "pending",
      label: TIMER_LINK_ACTION_META.pending,
      role: getTimerTypeMeta(session.trainingType).defaultRole,
      roleLabel: TIMER_LINK_ROLE_META[getTimerTypeMeta(session.trainingType).defaultRole],
      targetTrainingLogId: null,
      link: null
    };
  }

  function isShortTimerSession(session) {
    return toNullableNumber(session?.actualSeconds) !== null && toNullableNumber(session.actualSeconds) < 60;
  }

  function canConvertTimerSession(session) {
    if (!isConfirmableTimerSession(session)) return false;
    const seconds = toNullableNumber(session.actualSeconds) || 0;
    if (seconds < 60) return false;
    const meta = getTimerTypeMeta(session.trainingType);
    if (seconds < 300) return false;
    return Boolean(meta.canConvert);
  }

  function dateWithinDays(date, endDate, days) {
    if (!isIsoDate(date) || !isIsoDate(endDate)) return false;
    const item = parseIsoDate(date);
    const end = parseIsoDate(endDate);
    const start = new Date(end);
    start.setDate(end.getDate() - days + 1);
    return item >= start && item <= end;
  }

  function timerSessionMatchScore(envelope, date) {
    const data = envelope.data || {};
    const plans = getPlanItemsByDate(date).map((item) => item.data || {});
    if (data.dailyPlanItemId && plans.some((item) => item.id === data.dailyPlanItemId)) return 0;
    if (data.routineId && plans.some((item) => item.routineId === data.routineId)) return 1;
    if (data.trainingType && plans.some((item) => item.trainingType === data.trainingType)) return 2;
    return 3;
  }

  function getConfirmableTimerSessions(date) {
    return getTimerSessionsForDate(date).filter((item) => isConfirmableTimerSession(item.data) && getTimerSessionHandling(item).action === "pending");
  }

  function countPendingTimerSessions() {
    return (state.records.timer_sessions || []).filter((item) => (
      !item.deletedAt &&
      isConfirmableTimerSession(item.data) &&
      getTimerSessionHandling(item).action === "pending"
    )).length;
  }

  function findTimerSessionById(id) {
    return (state.records.timer_sessions || []).find((item) => item.data?.id === id || item.id === id) || null;
  }

  function getTrainingLogForTimerSession(sessionId) {
    if (!sessionId) return null;
    const sharedLog = (state.records.training_logs || []).find((item) => (
      !item.deletedAt &&
      (item.data?.timerSessionId === sessionId || (Array.isArray(item.data?.timerSessionIds) && item.data.timerSessionIds.includes(sessionId)))
    ));
    if (sharedLog) return sharedLog;
    return state.workouts.find((item) => item.timerSessionId === sessionId || (Array.isArray(item.timerSessionIds) && item.timerSessionIds.includes(sessionId))) || null;
  }

  function isConfirmableTimerSession(session) {
    if (!session) return false;
    const completion = String(session.completion || "").toLowerCase();
    return completion === "completed" || completion === "stopped";
  }

  function getTimerCompletionLabel(completion) {
    const value = String(completion || "").toLowerCase();
    if (value === "completed") return "已完成";
    if (value === "stopped") return "已停止";
    if (value === "skipped") return "已跳过";
    return completion || "计时";
  }

  function recentWorkouts(limit) {
    const records = [...state.workouts].sort((a, b) => b.date.localeCompare(a.date));
    return Number.isFinite(limit) ? records.slice(0, limit) : records;
  }

  function formatDuration(seconds) {
    if (!seconds) return "";
    const totalSeconds = Math.round(Number(seconds));
    const minutes = Math.floor(totalSeconds / 60);
    const remain = totalSeconds % 60;
    if (minutes >= 60) {
      return `${Math.floor(minutes / 60)}:${pad(minutes % 60)}:${pad(remain)}`;
    }
    return `${minutes}:${pad(remain)}`;
  }

  function getPace(record) {
    const pace = record.avgPaceSecPerKm || (record.durationSec && record.distanceKm ? Math.round(record.durationSec / record.distanceKm) : null);
    if (!pace) return "";
    return `${Math.floor(pace / 60)}'${pad(pace % 60)}"`;
  }

  function toSharedTrainingType(type) {
    if (LEGACY_TO_SHARED_TYPE[type]) return LEGACY_TO_SHARED_TYPE[type];
    if (SHARED_TO_LEGACY_TYPE[type]) return type;
    return "easy_walk";
  }

  function normalizeTimerTrainingType(type) {
    const value = String(type || "").trim();
    if (!value) return "easy_walk";
    if (TIMER_TYPE_META[value]) return value;
    if (LEGACY_TO_SHARED_TYPE[value]) return LEGACY_TO_SHARED_TYPE[value];
    if (SHARED_TO_LEGACY_TYPE[value]) return value;
    return value;
  }

  function defaultTimerLinkRole(roleOrType) {
    if (TIMER_LINK_ROLE_META[roleOrType]) return roleOrType;
    return getTimerTypeMeta(roleOrType).defaultRole || "note";
  }

  function toLegacyTrainingType(type) {
    if (TYPE_META[type]) return type;
    return SHARED_TO_LEGACY_TYPE[type] || "easyWalk";
  }

  function toSharedCompletionStatus(status, sharedType) {
    if (sharedType === "rest" && status === "skipped") return "rested";
    return LEGACY_TO_SHARED_STATUS[status] || status || "completed";
  }

  function toLegacyCompletionStatus(status, legacyType) {
    const next = SHARED_TO_LEGACY_STATUS[status] || status || "completed";
    return getStatusForType(legacyType, next);
  }

  function normalizeLegacyFatigue(value) {
    if (FATIGUE_META[value]) return value;
    return sharedFatigueToLegacy(value);
  }

  function normalizeSharedFatigue(value) {
    if (FATIGUE_META[value]) return legacyFatigueToShared(value);
    return clamp(Math.round(toNullableNumber(value) || 2), 1, 4);
  }

  function legacyFatigueToShared(value) {
    return LEGACY_FATIGUE_TO_SHARED[value] || 2;
  }

  function sharedFatigueToLegacy(value) {
    return SHARED_FATIGUE_TO_LEGACY[clamp(Math.round(toNullableNumber(value) || 2), 1, 4)] || "normal";
  }

  function getDeviceId() {
    try {
      const existing = window.localStorage.getItem(DEVICE_KEY);
      if (existing) return existing;
      const next = `shenke_${makeId()}`;
      window.localStorage.setItem(DEVICE_KEY, next);
      return next;
    } catch (error) {
      return "shenke_local";
    }
  }

  function formatNumber(value, digits) {
    if (value === null || value === undefined || Number.isNaN(Number(value))) return "";
    return Number(value).toFixed(digits);
  }

  function formatShortDate(date) {
    if (!isIsoDate(date)) return "";
    return date.slice(5).replace("-", "/");
  }

  function todayISO() {
    const now = new Date();
    return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;
  }

  function dateToISO(date) {
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
  }

  function parseIsoDate(date) {
    const [year, month, day] = date.split("-").map(Number);
    return new Date(year, month - 1, day);
  }

  function isIsoDate(value) {
    return typeof value === "string" && /^\d{4}-\d{2}-\d{2}$/.test(value);
  }

  function pad(value) {
    return String(value).padStart(2, "0");
  }

  function toNullableNumber(value) {
    if (value === "" || value === null || value === undefined) return null;
    const number = Number(value);
    return Number.isFinite(number) ? number : null;
  }

  function clampPain(value) {
    return clamp(Math.round(toNullableNumber(value) || 0), 0, 5);
  }

  function clamp(value, min, max) {
    return Math.min(max, Math.max(min, value));
  }

  function makeId() {
    if (window.crypto && window.crypto.randomUUID) return window.crypto.randomUUID();
    return `id-${Date.now()}-${Math.random().toString(16).slice(2)}`;
  }

  function escapeHtml(value) {
    return String(value)
      .replaceAll("&", "&amp;")
      .replaceAll("<", "&lt;")
      .replaceAll(">", "&gt;")
      .replaceAll('"', "&quot;")
      .replaceAll("'", "&#039;");
  }
})();
