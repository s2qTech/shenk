(() => {
  "use strict";

  const DB_NAME = "training-assistant-v2";
  const DB_VERSION = 1;
  const SNAPSHOT_KEY = "snapshot";
  const FALLBACK_KEY = "training-assistant-v2:snapshot";
  const DEVICE_KEY = "training-assistant-v2:device-id";
  const SYNC_CONFIG_KEY = "training-assistant-v2:sync-config";
  const SYNC_CONFIG_PACKAGE_PREFIX = "shenke-config-v1:";
  const SYNC_PROFILE_PACKAGE_PREFIX = "shenk-profile-v1:";
  const SYNC_PROFILE_KDF_ITERATIONS = 210000;
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
  const TRAINING_TYPE_ALIASES = {
    easy: "easy_walk",
    walk: "easy_walk",
    walking: "easy_walk",
    easyWalk: "easy_walk",
    easy_walking: "easy_walk",
    normal_walk: "easy_walk",
    brisk_walk: "quality_walk",
    qualityWalk: "quality_walk",
    quality: "quality_walk",
    cardio: "indoor_cardio",
    indoorCardio: "indoor_cardio",
    indoor: "indoor_cardio",
    travel: "travel_strength",
    travelStrength: "travel_strength",
    seatRecovery: "seat_recovery",
    warm_up: "warmup",
    walk_warmup: "warmup",
    cool_down: "cooldown",
    cooldown_stretch: "cooldown"
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
    timer: { label: "计时", asset: "assets/app/timer.png" },
    records: { label: "记录", asset: "assets/app/list.png" },
    data: { label: "数据", asset: "assets/app/notebook.png" },
    settings: { label: "设置", asset: "assets/app/setting.png" }
  };

  const SETTINGS_SECTION_META = {
    cloud: { label: "云端", hint: "同步配置", title: "云数据库", subtitle: "Cloudflare Worker + D1，同一份云端数据，按角色读写" },
    plan: { label: "计划", hint: "草案导入", title: "计划草案", subtitle: "粘贴 Codex 输出，预览后再写入日历计划" },
    feedback: { label: "反馈", hint: "摘要导出", title: "Codex 反馈", subtitle: "导出训练、状态和计时器摘要" },
    local: { label: "本地", hint: "导入导出", title: "本地数据", subtitle: "导入、导出和种子记录" }
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

  const ROUTINE_DISPLAY_META = {
    routine_home_strength_standard_v3_1: { label: "力量训练" },
    routine_home_strength_short_v3_1: { label: "力量短版" },
    routine_indoor_cardio_v2_9: { label: "室内有氧" },
    routine_walk_warmup_v1: { label: "走前热身" },
    routine_walk_stretch_quick_v1: { label: "走后拉伸", variant: "简版" },
    routine_walk_stretch_full_v1: { label: "走后拉伸", variant: "完整版" },
    routine_recovery_stretch_v1: { label: "恢复拉伸" },
    routine_travel_hotel_v2_7: { label: "外出训练" },
    routine_seat_recovery_v1: { label: "座位活动" }
  };

  const TIMER_TYPE_DISPLAY_LABELS = {
    strength: "力量",
    indoor_cardio: "有氧",
    warmup: "热身",
    stretch: "拉伸",
    recovery: "恢复",
    travel_strength: "力量",
    seat_recovery: "活动"
  };

  const TIMER_LINK_ACTION_META = {
    pending: "未处理",
    linked: "已关联",
    converted: "已入记录",
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
  let autoPushRetryTimer = null;

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
    timerFrameUrl: "",
    dataView: "summary",
    settingsSection: "cloud",
    feedbackDays: 14,
    feedbackExport: null,
    planPatchText: "",
    planPatchPreview: null,
    editorDrafts: null,
    editorSections: null,
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
    registerOfflineSupport();
    const snapshot = normalizeSnapshot(await loadSnapshot());
    state.records = normalizeSharedRecords(snapshot.records);
    refreshLegacyCachesFromSharedRecords();
    if (!state.workouts.length) {
      seedWorkouts().forEach((workout) => upsertSharedEnvelope(
        state.records,
        "training_logs",
        workoutToTrainingLogData(workout, { compatSource: "seed" })
      ));
      refreshLegacyCachesFromSharedRecords();
      await saveSnapshot("已载入历史种子记录");
    }
    state.ready = true;
    render();
    scheduleInitialCloudSync();
  }

  function scheduleInitialCloudSync() {
    if (!hasShenkSyncConfig()) return;
    window.setTimeout(async () => {
      await runSyncTask(async () => {
        await doPullCloudRecords();
        await doPushDirtyRecords();
        await doPullCloudRecords();
      }, false);
      render();
    }, 300);
  }

  function registerOfflineSupport() {
    if (!("serviceWorker" in navigator) || !/^https?:$/.test(window.location.protocol)) return;
    navigator.serviceWorker.register("./sw.js").catch(() => {
      // Local records and recommendations remain available even if registration fails.
    });
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

  function buildSnapshot(options = {}) {
    const records = normalizeSharedRecords(state.records);
    const includeLegacy = options.includeLegacy === true;
    return {
      schemaVersion: SHARED_SCHEMA_VERSION,
      dataSource: "shared_records",
      legacySchemaVersion: includeLegacy ? 1 : 2,
      updatedAt: new Date().toISOString(),
      workouts: includeLegacy ? deriveLegacyWorkoutsFromSharedRecords(records) : [],
      bodyMetrics: includeLegacy ? deriveLegacyMetricsFromSharedRecords(records) : [],
      records: cloneJson(records)
    };
  }

  function normalizeSnapshot(payload) {
    const source = payload && typeof payload === "object" ? payload : {};
    const records = normalizeSharedRecords(collectSharedRecordSource(source));
    const workouts = normalizeWorkouts(source.workouts);
    const bodyMetrics = normalizeBodyMetrics(source.bodyMetrics);

    if (!records.training_logs.length && workouts.length) {
      records.training_logs = workouts.map((item) => workoutToTrainingLogEnvelope(item, { compatSource: "workouts" })).filter(Boolean);
    }
    if (!records.body_metrics.length && bodyMetrics.length) {
      records.body_metrics = bodyMetrics.map((item) => bodyMetricToSharedEnvelope(item, { compatSource: "bodyMetrics" })).filter(Boolean);
    }

    return {
      workouts: deriveLegacyWorkoutsFromSharedRecords(records),
      bodyMetrics: deriveLegacyMetricsFromSharedRecords(records),
      records
    };
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
    if (entity === "routine_templates") return normalizeRoutineTemplateData(data);
    return normalizeLooseSharedData(data, entity);
  }

  function normalizeLooseSharedData(data, prefix) {
    if (!data || typeof data !== "object") return null;
    return {
      ...data,
      id: data.id || `${prefix}_${makeId()}`
    };
  }

  function normalizeRoutineTemplateData(data) {
    if (!data || typeof data !== "object") return null;
    const id = data.id || data.routineId || data.routine_id || `routine_${makeId()}`;
    const trainingType = normalizeTimerTrainingType(data.trainingType || data.training_type || data.type || inferTrainingTypeFromRoutineId(id));
    const steps = Array.isArray(data.steps) ? data.steps : parseJsonArrayValue(data.stepsJson || data.steps_json);
    const explicitVisible = parseOptionalBoolean(
      data.timerVisible
      ?? data.timer_visible
      ?? data.visibleInTimer
      ?? data.visible_in_timer
      ?? data.showInTimer
      ?? data.show_in_timer
      ?? data.isTimerRoutine
      ?? data.is_timer_routine
    );
    const timerVisible = explicitVisible ?? Boolean(data.needsTimer ?? data.needs_timer ?? steps.length);
    const title = data.title || data.name || data.displayName || data.display_name || TYPE_META[toLegacyTrainingType(trainingType)]?.label || "训练方案";
    const defaultOptions = data.defaultOptions || data.default_options || data.timerOptions || data.timer_options || {};
    return {
      ...data,
      id,
      routineId: id,
      routineVersion: data.routineVersion || data.routine_version || data.version || null,
      title,
      name: data.name || title,
      variant: data.variant || data.routineVariant || data.routine_variant || "",
      trainingType,
      estimatedMinutes: toNullableNumber(data.estimatedMinutes ?? data.estimated_minutes),
      steps: steps.length ? steps : data.steps,
      defaultOptions,
      timerVisible,
      needsTimer: Boolean(data.needsTimer ?? data.needs_timer ?? timerVisible),
      source: data.source || "coach",
      createdAt: data.createdAt || data.created_at || new Date().toISOString(),
      updatedAt: data.updatedAt || data.updated_at || new Date().toISOString()
    };
  }

  function parseJsonArrayValue(value) {
    if (Array.isArray(value)) return value;
    if (!value || typeof value !== "string") return [];
    try {
      const parsed = JSON.parse(value);
      return Array.isArray(parsed) ? parsed : [];
    } catch (error) {
      return [];
    }
  }

  function parseJsonObjectValue(value) {
    if (value && typeof value === "object" && !Array.isArray(value)) return value;
    if (!value || typeof value !== "string") return {};
    try {
      const parsed = JSON.parse(value);
      return parsed && typeof parsed === "object" && !Array.isArray(parsed) ? parsed : {};
    } catch (error) {
      return {};
    }
  }

  function parseOptionalBoolean(value) {
    if (value === null || value === undefined || value === "") return null;
    if (typeof value === "boolean") return value;
    if (typeof value === "number") return value !== 0;
    const text = String(value).trim().toLowerCase();
    if (["1", "true", "yes", "on", "visible"].includes(text)) return true;
    if (["0", "false", "no", "off", "hidden"].includes(text)) return false;
    return null;
  }

  function inferTrainingTypeFromRoutineId(id) {
    const text = String(id || "").toLowerCase();
    if (text.includes("warmup")) return "warmup";
    if (text.includes("stretch")) return "stretch";
    if (text.includes("recovery")) return "recovery";
    if (text.includes("indoor") || text.includes("cardio")) return "indoor_cardio";
    if (text.includes("travel")) return "travel_strength";
    if (text.includes("seat")) return "seat_recovery";
    if (text.includes("strength")) return "strength";
    return "easy_walk";
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
      fromSnapshot: normalizePlanSnapshotData(data.fromSnapshot || data.from_snapshot || {}, data.date),
      toSnapshot: normalizePlanSnapshotData(data.toSnapshot || data.to_snapshot || {}, data.date),
      createdAt: data.createdAt || data.created_at || new Date().toISOString(),
      updatedAt: data.updatedAt || data.updated_at || new Date().toISOString()
    };
  }

  function normalizePlanSnapshotData(snapshot, date) {
    if (!snapshot || typeof snapshot !== "object") return {};
    const trainingType = toSharedTrainingType(snapshot.trainingType || snapshot.training_type || snapshot.type);
    return {
      ...snapshot,
      date: snapshot.date || date,
      trainingType,
      title: snapshot.title || TYPE_META[toLegacyTrainingType(trainingType)].label,
      estimatedMinutes: toNullableNumber(snapshot.estimatedMinutes ?? snapshot.estimated_minutes),
      needsTimer: Boolean(snapshot.needsTimer ?? snapshot.needs_timer),
      routineId: snapshot.routineId || snapshot.routine_id || null,
      routineVersion: snapshot.routineVersion || snapshot.routine_version || null,
      timerOptions: snapshot.timerOptions || snapshot.timer_options || {},
      notes: Array.isArray(snapshot.notes) ? snapshot.notes : [],
      status: snapshot.status || "planned"
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

  function upsertSharedEnvelope(records, entity, data, existingOverride = null) {
    const existing = existingOverride || records[entity].find((item) => item.id === data.id) || null;
    const envelope = makeSharedEnvelope(entity, data, existing);
    if (!envelope) return;
    records[entity] = records[entity].filter((item) => item.id !== envelope.id).concat(envelope).sort(compareSharedEnvelopes);
  }

  function markSharedRecordsDeletedByDate(entities, date) {
    const now = new Date().toISOString();
    entities.forEach((entity) => {
      if (!Array.isArray(state.records[entity])) return;
      state.records[entity] = state.records[entity].map((item) => {
        if (item.deletedAt || item.data?.date !== date) return item;
        return {
          ...item,
          data: {
            ...(item.data || {}),
            updatedAt: now,
            deletedAt: now
          },
          updatedAt: now,
          deletedAt: now,
          syncState: "dirty",
          conflict: null
        };
      });
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

  function workoutToTrainingLogEnvelope(workout, options = {}) {
    const data = workoutToTrainingLogData(workout, options);
    return data ? makeSharedEnvelope("training_logs", data) : null;
  }

  function workoutToTrainingLogData(workout, options = {}) {
    if (!workout || !isIsoDate(workout.date)) return null;
    const type = toSharedTrainingType(workout.type);
    const rawJson = { ...(workout.rawJson || {}) };
    if (options.compatSource) {
      rawJson.compatSource = options.compatSource;
      rawJson.legacyWorkoutId = workout.id;
    }
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
      rawJson,
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

  function bodyMetricToSharedEnvelope(metric, options = {}) {
    const data = bodyMetricToSharedData(metric, options);
    return data ? makeSharedEnvelope("body_metrics", data) : null;
  }

  function bodyMetricToSharedData(metric, options = {}) {
    if (!metric || !isIsoDate(metric.date)) return null;
    const pain = metric.pain || {};
    const rawJson = { ...(metric.rawJson || {}) };
    if (options.compatSource) {
      rawJson.compatSource = options.compatSource;
      rawJson.legacyMetricId = metric.id;
    }
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
      rawJson,
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
    const tabs = ["calendar", "timer", "records", "data"];
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
      return renderTimerPage();
    }

    if (state.activeTab === "records") {
      return renderTimerSessionsPage();
    }

    if (state.activeTab === "data") {
      return state.dataView === "records" ? renderAllRecordsPage() : renderDataSummaryPage();
    }

    if (state.activeTab === "settings") {
      return renderSettingsPage();
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

  function renderSettingsPage() {
    const section = SETTINGS_SECTION_META[state.settingsSection] ? state.settingsSection : "cloud";
    const current = SETTINGS_SECTION_META[section];
    return `
      <section class="content-page settings-page">
        ${renderPageHead("设置")}
        <div class="settings-shell">
          <nav class="settings-section-tabs" aria-label="设置分类">
            ${Object.entries(SETTINGS_SECTION_META).map(([id, item]) => `
              <button type="button" class="settings-section-tab ${section === id ? "active" : ""}" data-settings-section="${id}">
                <strong>${escapeHtml(item.label)}</strong>
                <span>${escapeHtml(item.hint)}</span>
              </button>
            `).join("")}
          </nav>
          <div class="settings-content settings-section-${escapeHtml(section)}">
            ${panel(current.title, current.subtitle, renderSettingsSection(section))}
          </div>
        </div>
      </section>
    `;
  }

  function renderSettingsSection(section) {
    if (section === "plan") return renderPlanPatchPanel();
    if (section === "feedback") return renderFeedbackExportPanel();
    if (section === "local") return renderDataPanel();
    return renderSyncPanel();
  }

  function renderDataSummaryPage() {
    return `
      <section class="content-page data-page">
        ${renderPageHead("数据")}
        <div class="data-grid-layout">
          <div class="data-column data-trend-grid">
            ${panel("体重趋势", "最近体重记录", renderWeightTrend())}
            ${panel("腰围趋势", "最近腰围记录", renderWaistTrend())}
            ${panel("体脂率趋势", "最近体脂记录", renderBodyFatTrend())}
            ${panel("肌肉量趋势", "最近肌肉量记录", renderMuscleTrend())}
          </div>
          <div class="data-column data-side-stack">
            ${panel("记录概览", "最近训练节奏", renderOverview())}
            ${panel(
              "最近记录",
              "只显示最近 10 条",
              renderRecordList(recentWorkouts(10), "recent-record-list"),
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

  function renderTimerPage() {
    const src = state.timerFrameUrl || buildTimerUrl();
    return `
      <section class="timer-embed-page" aria-label="计时器">
        ${renderPageHead("计时器")}
        <iframe
          data-timer-frame
          class="timer-embed-frame"
          src="${escapeHtml(src)}"
          title="计时器"
          loading="eager"
          allow="wake-lock; fullscreen"
        ></iframe>
      </section>
    `;
  }

  function renderDateDrawer() {
    const date = state.selectedDate;
    const isPast = date < todayISO();
    const hasRecord = Boolean(getWorkoutByDate(date) || getMetricByDate(date));
    const hasEditableDate = date <= todayISO();
    const canEdit = canEditSelectedDate();
    const editorTitle = getEditorTitle(date, hasRecord, isPast);
    return `
      <aside class="date-drawer" aria-label="日期详情">
        <div class="drawer-head">
          <div>
            <p class="drawer-kicker">${escapeHtml(getSelectedPanelTitle())}</p>
            <h2>${escapeHtml(date)}</h2>
          </div>
          <div class="drawer-actions">
            ${renderDrawerEditActions(date, isPast, hasEditableDate)}
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

  function renderDrawerEditActions(date, isPast, hasEditableDate) {
    if (!hasEditableDate) return "";
    if (state.editMode) {
      return `<button type="button" class="subtle" data-action="cancel-edit">${isPast ? "取消修正" : "取消编辑"}</button>`;
    }
    const hasTraining = getWorkoutsByDate(date).length > 0;
    const hasStatus = Boolean(getMetricByDate(date));
    const trainingLabel = isPast ? "修正训练" : hasTraining ? "编辑训练" : "补训练";
    const statusLabel = isPast ? "修正状态" : hasStatus ? "编辑状态" : "补状态";
    return `
      <button type="button" class="subtle" data-action="enable-edit-status">${statusLabel}</button>
      <button type="button" class="subtle" data-action="enable-edit-training">${trainingLabel}</button>
    `;
  }

  function getEditorTitle(date, hasRecord, isPast) {
    const sections = getEditorSections();
    if (sections.status && !sections.training) return isPast ? "修正状态" : getMetricByDate(date) ? "编辑状态" : "补状态";
    if (sections.training && !sections.status) return isPast ? "修正训练" : getWorkoutsByDate(date).length ? "编辑训练" : "补训练";
    return isPast ? "修正记录" : hasRecord ? "编辑今天" : "记录今天";
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
    const label = kind === "forecast" ? "预测" : "建议";
    return `
      <div class="suggestion advice-${kind}">
        <div class="suggestion-top">
          <div>
            <span class="suggestion-label">${label} · ${escapeHtml(recommendation.sourceLabel || "本地规则")} · ${escapeHtml(recommendation.label)}</span>
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

  function renderFeedbackExportPanel() {
    const output = state.feedbackExport;
    const generated = output ? `已生成 ${output.period.from} - ${output.period.to}` : "选择时间范围后生成";
    return `
      <div class="feedback-export">
        <div class="feedback-controls">
          <label>
            <span>范围</span>
            <select data-feedback-range>
              ${[7, 14, 30].map((days) => `<option value="${days}" ${state.feedbackDays === days ? "selected" : ""}>最近 ${days} 天</option>`).join("")}
            </select>
          </label>
          <div class="button-row settings-actions feedback-action-row">
            <button type="button" class="primary" data-action="generate-feedback">生成摘要</button>
            <button type="button" data-action="copy-feedback" ${output ? "" : "disabled"}>复制</button>
            <button type="button" data-action="download-feedback" ${output ? "" : "disabled"}>下载</button>
          </div>
        </div>
        <p class="feedback-status">${escapeHtml(generated)}</p>
        <textarea class="feedback-output" readonly placeholder="生成后可复制给训练计划对话。">${output ? escapeHtml(output.text) : ""}</textarea>
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

  function renderBodyFatTrend() {
    return renderBodyTrend({
      key: "bodyFatPct",
      title: "体脂率趋势",
      unit: "%",
      emptyText: "还没有体脂率记录。编辑状态记录时填写体脂率后，这里会显示趋势。",
      rangeFloor: 0.8,
      deltaThreshold: 0.1,
      className: "body-fat-trend-chart"
    });
  }

  function renderMuscleTrend() {
    return renderBodyTrend({
      key: "muscleKg",
      title: "肌肉量趋势",
      unit: "kg",
      emptyText: "还没有肌肉量记录。编辑状态记录时填写肌肉量后，这里会显示趋势。",
      rangeFloor: 0.8,
      deltaThreshold: 0.05,
      className: "muscle-trend-chart"
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
      <div class="weight-trend body-trend ${className}">
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
        <svg class="trend-chart" viewBox="0 0 ${width} ${height}" preserveAspectRatio="none" role="img" aria-label="${escapeHtml(title)}">
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
    const trainingLogId = draftValue(trainingDraft, "trainingLogId", record ? record.trainingLogId : "");
    const dailyPlanItemId = draftValue(trainingDraft, "dailyPlanItemId", record ? record.dailyPlanItemId : "");
    const timerSessionId = draftValue(trainingDraft, "timerSessionId", record ? record.timerSessionId : "");
    const timerSessionIds = draftValue(trainingDraft, "timerSessionIds", record ? record.timerSessionIds : []);
    const trainingSource = draftValue(trainingDraft, "source", record ? record.source : "");
    const timerSessionJson = draftValue(trainingDraft, "timerSessionJson", record?.rawJson?.timerSession || "");
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
    const hasRecord = Boolean(record && (!trainingLogId || record.trainingLogId === trainingLogId || record.timerSessionId === timerSessionId));
    const visibleSections = getEditorSections();

    return `
      ${visibleSections.training ? `<form class="editor-form" id="training-form">
        ${renderTrainingHiddenFields({
          trainingLogId,
          dailyPlanItemId,
          timerSessionId,
          timerSessionIds,
          source: trainingSource,
          timerSessionJson
        })}
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
      </form>` : ""}
      ${visibleSections.status ? `<form class="editor-form" id="status-form">
        <input name="date" type="hidden" value="${state.selectedDate}">
        ${formSection("晨起状态", "按当天起床后的状态填写，主要用于调整当天建议。", `
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
      </form>` : ""}
    `;
  }

  function renderTrainingHiddenFields(values) {
    return Object.entries(values)
      .filter(([, value]) => value !== null && value !== undefined && value !== "")
      .map(([name, value]) => `<input name="${name}" type="hidden" value="${escapeHtml(serializeHiddenValue(value))}">`)
      .join("");
  }

  function serializeHiddenValue(value) {
    if (Array.isArray(value) || (value && typeof value === "object")) return JSON.stringify(value);
    return String(value);
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
        const adjustment = getPlanAdjustmentsByDate(date).at(-1);
        const planItem = getPlanItemsByDate(date)[0];
        if (adjustment) entries.set(date, calendarEntryFromPlanAdjustment(adjustment));
        else if (planItem) entries.set(date, calendarEntryFromPlanItem(planItem));
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
      marker: "建议",
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

  function calendarEntryFromPlanItem(envelope) {
    const data = envelope.data || {};
    return calendarEntryFromPlanData(data, "plan", "计划");
  }

  function calendarEntryFromPlanAdjustment(envelope) {
    const data = envelope.data || {};
    return calendarEntryFromPlanData(data.toSnapshot || data.to_snapshot || {}, "adjustment", "计划");
  }

  function calendarEntryFromPlanData(data, kind, marker) {
    const type = toLegacyTrainingType(data.trainingType || data.type);
    const meta = TYPE_META[type] || TYPE_META.easyWalk;
    return {
      kind,
      type,
      icon: meta.icon,
      marker,
      text: formatCalendarPlanText(data, meta),
      className: meta.className,
      statusClass: `status-${data.status || "planned"}`
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
      const showIcon = entry && ["actual", "plan", "adjustment"].includes(entry.kind);
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
            <span><i class="legend-dot legend-plan"></i>计划</span>
            <span><i class="legend-dot legend-suggestion"></i>建议</span>
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
    const visibleTimerSessions = timerSessions.filter((session) => getTimerSessionHandling(session).action !== "converted");
    const sections = [];
    const hasActualRecords = records.length > 0;

    if (!hasActualRecords && planItems.length) {
      sections.push(renderLayerSection("计划", planItems.map(renderPlanItemCard).join("")));
    } else if (!hasActualRecords && date >= todayISO()) {
      sections.push(renderLayerSection(date === todayISO() ? "今日建议" : "未来预测", renderAdviceCard(date, date === todayISO() ? "suggestion" : "forecast")));
    }

    if (!hasActualRecords && adjustments.length) {
      sections.push(renderLayerSection("调整", adjustments.map(renderPlanAdjustmentCard).join("")));
    }

    if (hasActualRecords) {
      sections.push(renderLayerSection("正式训练记录", records.map(renderRecordCard).join("")));
    } else if (visibleTimerSessions.some((session) => getTimerSessionHandling(session).action === "pending")) {
      sections.push(renderLayerSection("正式训练记录", `<div class="empty-state compact">有计时器记录待处理，确认后才会进入正式训练记录。</div>`));
    }

    if (visibleTimerSessions.length) {
      sections.push(renderLayerSection("计时器记录", visibleTimerSessions.map(renderTimerSessionCard).join("")));
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
    const variant = getRoutineVariantLabel(data);
    const details = [
      data.estimatedMinutes ? `${data.estimatedMinutes} 分` : "",
      variant
    ].filter(Boolean).join(" · ");
    const notes = formatPublicNotes(data.notes);
    return `
      <article class="record-card plan-card">
        <div class="record-top">
          <h3>${renderTypeIcon(legacyType, "record-type-icon")}${escapeHtml(getPlanItemDisplayTitle(data) || meta.label)}</h3>
          <span class="tag">计划</span>
        </div>
        ${details ? `<p>${escapeHtml(details)}</p>` : ""}
        ${data.goal ? `<p>${escapeHtml(data.goal)}</p>` : ""}
        ${notes ? `<p>${escapeHtml(notes)}</p>` : ""}
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
    const toTitle = getPlanItemDisplayTitle(data.toSnapshot || {}) || "计划调整";
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
    const variant = getRoutineVariantLabel(data);
    const notes = formatPublicNotes(data.notes);
    const details = [
      data.startedAt ? `开始 ${formatLocalDateTime(data.startedAt)}` : "",
      data.actualSeconds ? formatDuration(data.actualSeconds) : "",
      variant,
      data.completion ? getTimerCompletionLabel(data.completion) : ""
    ].filter(Boolean).join(" · ");
    return `
      <article class="record-card timer-card timer-${handling.action}">
        <div class="record-top">
          <h3>${renderTypeIcon(legacyType, "record-type-icon")}${escapeHtml(timerMeta.label)} · ${escapeHtml(getTimerSessionTitle(data))}</h3>
          <span class="tag">${escapeHtml(handling.label)}</span>
        </div>
        ${details ? `<p>${escapeHtml(details)}</p>` : ""}
        ${notes ? `<p>${escapeHtml(notes)}</p>` : ""}
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
          <h3>晨起状态</h3>
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
      <section class="content-page records-log-page">
        ${renderPageHead("记录")}
        ${renderTimerStats()}
        <div class="timer-workspace">
          <section class="panel timer-list-panel">
            <div class="panel-header timer-panel-header">
              <div>
                <h2 class="panel-title">最近运动记录</h2>
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
                <p class="panel-subtitle">补全训练、标记辅助流程，不改写 timer_sessions</p>
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
        <div class="metric"><strong>${linked}</strong><span>已处理</span></div>
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
      return `<div class="empty-state">没有符合条件的运动记录。</div>`;
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
    const canDraft = canDraftTimerSessionTraining(data) && handling.action === "pending";
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
          ${canDraft ? `<button type="button" data-action="draft-timer-session-training" data-session-id="${escapeHtml(data.id)}">补全</button>` : ""}
          ${handling.action === "pending" ? `<button type="button" data-action="ignore-timer-session" data-session-id="${escapeHtml(data.id)}">忽略</button>` : ""}
        </div>
      </article>
    `;
  }

  function renderTimerSessionDetail(envelope) {
    if (!envelope) {
      return `<div class="empty-state">选择一条运动记录查看详情。</div>`;
    }
    const data = envelope.data || {};
    const typeMeta = getTimerTypeMeta(data.trainingType);
    const handling = getTimerSessionHandling(envelope);
    const variant = getRoutineVariantLabel(data);
    const notes = formatPublicNotes(data.notes);
    const details = [
      ["日期", data.date],
      ["开始时间", formatLocalDateTime(data.startedAt)],
      ["结束时间", data.endedAt ? formatLocalDateTime(data.endedAt) : "-"],
      ["类型", typeMeta.label],
      ["流程", getTimerSessionTitle(data)],
      variant ? ["版本", variant] : null,
      ["实际时长", formatDuration(data.actualSeconds) || "-"],
      ["完成状态", getTimerCompletionLabel(data.completion)],
      ["处理状态", handling.label],
      ["关联", handling.targetTrainingLogId ? "已关联" : "-"]
    ].filter(Boolean);
    return `
      <div class="timer-detail">
        <div class="timer-detail-title">
          <h3>${escapeHtml(typeMeta.label)} · ${escapeHtml(getTimerSessionTitle(data))}</h3>
          <span class="tag">${escapeHtml(handling.label)}</span>
        </div>
        <dl class="timer-detail-grid">
          ${details.map(([label, value]) => `<div><dt>${escapeHtml(label)}</dt><dd>${escapeHtml(value)}</dd></div>`).join("")}
        </dl>
        ${notes ? `<p class="timer-note">${escapeHtml(notes)}</p>` : ""}
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
    const canDraft = canDraftTimerSessionTraining(data);
    return `
      <div class="button-row timer-action-row">
        ${canDraft ? `<button type="button" class="primary" data-action="draft-timer-session-training" data-session-id="${escapeHtml(data.id)}">补全训练记录</button>` : ""}
        <button type="button" data-action="mark-timer-session-role" data-role="warmup" data-session-id="${escapeHtml(data.id)}">标记为热身</button>
        <button type="button" data-action="mark-timer-session-role" data-role="stretch" data-session-id="${escapeHtml(data.id)}">标记为拉伸/冷身</button>
        <button type="button" data-action="ignore-timer-session" data-session-id="${escapeHtml(data.id)}">忽略</button>
      </div>
      ${!canDraft ? `<p class="timer-note">这条记录更像辅助流程或测试记录，默认不建议进入正式训练记录。</p>` : ""}
    `;
  }

  function renderDataPanel() {
    const snapshotSize = JSON.stringify({
      records: state.records
    }).length;
    return `
      <div class="data-grid">
        <div class="metric"><strong>${state.workouts.length}</strong><span>训练记录</span></div>
        <div class="metric"><strong>${state.bodyMetrics.length}</strong><span>身体记录</span></div>
        <div class="metric"><strong>${Math.ceil(snapshotSize / 1024)}</strong><span>KB 本地数据</span></div>
      </div>
      <div class="button-row settings-actions local-data-actions">
        <button type="button" class="primary" data-action="export">导出 JSON</button>
        <label class="file-button">
          导入 JSON
          <input type="file" accept="application/json,.json" data-action="import">
        </label>
      </div>
      <div class="settings-danger-zone">
        <button type="button" class="danger" data-action="restore-seed">恢复种子记录</button>
      </div>
    `;
  }

  function renderPlanPatchPanel() {
    const preview = state.planPatchPreview;
    const result = preview && preview.patch ? previewPlanPatch(preview.patch) : null;
    const invalid = preview && preview.error;
    return `
      <div class="plan-patch-panel">
        <textarea
          id="plan-patch-input"
          class="plan-patch-input"
          rows="7"
          placeholder="粘贴包含 coach_plan_patch 的 JSON 或 Codex 回复。"
        >${escapeHtml(state.planPatchText)}</textarea>
        <div class="button-row settings-actions plan-patch-actions">
          <button type="button" data-action="parse-plan-patch">校验预览</button>
          <button type="button" class="primary" data-action="apply-plan-patch" ${result && result.valid ? "" : "disabled"}>确认写入</button>
          <button type="button" data-action="clear-plan-patch" ${state.planPatchText || preview ? "" : "disabled"}>清空</button>
        </div>
        ${invalid ? `<div class="empty-state compact danger-text">${escapeHtml(preview.error)}</div>` : ""}
        ${result ? renderPlanPatchPreview(result) : `<div class="empty-state compact">草案不会自动生效，必须先预览再确认。</div>`}
      </div>
    `;
  }

  function renderPlanPatchPreview(result) {
    const rows = [
      ["计划模板", formatPatchCounts(result.planPreview)],
      ["动作模板", formatPatchCounts(result.routinePreview)],
      ["日计划", `${formatPatchCounts(result.dailyPreviewCounts)}，跳过 ${result.skippedDailyCount}`],
      ["计划调整", formatPatchCounts(result.adjustmentPreview)]
    ];
    return `
      <div class="plan-patch-preview">
        <div class="metric-row compact-metrics">
          ${rows.map(([label, value]) => `<div class="metric"><strong>${escapeHtml(value)}</strong><span>${escapeHtml(label)}</span></div>`).join("")}
        </div>
        ${result.deleteCount ? `<p class="preview-note danger-text">本次将删除 ${result.deleteCount} 条记录，写入前会再次确认。</p>` : ""}
        ${result.skippedDates.length ? `<p class="preview-note">已有实际训练，不覆盖：${escapeHtml(result.skippedDates.join("、"))}</p>` : ""}
        ${result.warnings.length ? `<ul class="preview-list">${result.warnings.map((item) => `<li>${escapeHtml(item)}</li>`).join("")}</ul>` : ""}
        ${result.previewRows.length ? `
          <div class="preview-timeline">
            ${result.previewRows.map((item) => `
              <div class="preview-row ${item.action === "跳过" ? "is-skipped" : ""}">
                <strong>${escapeHtml(item.id || item.entity)}</strong>
                <span>${escapeHtml(item.entity)}</span>
                <em>${escapeHtml(item.reason ? `${item.action}：${item.reason}` : item.action)}</em>
              </div>
            `).join("")}
          </div>
        ` : ""}
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
    const notes = formatPublicNotes(record.notes);
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
        ${notes ? `<p>${escapeHtml(notes)}</p>` : ""}
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

  function formatCalendarPlanText(data, meta) {
    const title = getPlanItemDisplayTitle(data) || meta.label;
    const minutes = toNullableNumber(data.estimatedMinutes ?? data.estimated_minutes);
    return minutes ? `${title} ${Math.round(minutes)}分` : title;
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
    const configProfileId = config.configProfileId || "";
    const status = state.syncStatus.busy ? "云端读写中..." : state.syncStatus.lastResult || "未连接";
    const error = state.syncStatus.lastError;
    return `
      <form class="sync-form" id="sync-config-form">
        <section class="settings-block sync-config-block">
          <div class="settings-block-head">
            <strong>连接配置</strong>
            <span>保存到当前浏览器，不写入代码或 URL。</span>
          </div>
          <div class="settings-form-grid">
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
          </div>
          <div class="button-row settings-actions">
            <button type="submit" class="primary">保存配置</button>
            <button type="button" data-action="sync-health">测试连接</button>
          </div>
        </section>

        <section class="settings-block sync-status-block">
          <div class="settings-block-head">
            <strong>同步状态</strong>
            <span>日常只需要使用云端同步。</span>
          </div>
          <div class="data-grid sync-metrics">
            <div class="metric"><strong>${dirtyCount}</strong><span>待写入云端</span></div>
            <div class="metric"><strong>${totalCount}</strong><span>共享记录</span></div>
            <div class="metric"><strong>${conflictCount}</strong><span>冲突</span></div>
            <div class="metric"><strong>${escapeHtml(lastSyncAt)}</strong><span>最近云端读写</span></div>
          </div>
          <div class="button-row settings-actions">
            <button type="button" class="primary" data-action="sync-now">云端同步</button>
          </div>
          <details class="sync-advanced">
            <summary>手动同步</summary>
            <div class="button-row settings-actions secondary-actions">
              <button type="button" data-action="sync-pull">只读取云端</button>
              <button type="button" data-action="sync-push">只写入云端</button>
            </div>
          </details>
          ${conflictCount ? `
            <div class="button-row settings-actions conflict-actions">
            <button type="button" data-action="resolve-conflicts-cloud">使用云端</button>
            <button type="button" data-action="resolve-conflicts-local">使用本地覆盖</button>
            </div>
          ` : ""}
          <p class="sync-status ${error ? "sync-error" : ""}">${escapeHtml(error || status)}</p>
        </section>

        <details class="settings-block sync-transfer sync-profile-block" open>
          <summary>
            <strong>多端配置</strong>
            <span>用一个加密档案连接自己的新设备</span>
          </summary>
          <p>云端只保存加密后的配置档案；密码只在当前设备用于加密和解密，不上传云端。</p>
          <div class="settings-form-grid">
            <label>
              <span>配置档案 ID</span>
              <input type="text" name="configProfileId" data-sync-profile-id placeholder="例如 shenk_qi_main" value="${escapeHtml(configProfileId)}">
            </label>
            <label>
              <span>配置密码</span>
              <input type="password" data-sync-profile-password autocomplete="new-password" placeholder="用于加密或解密，不会保存">
            </label>
          </div>
          <div class="button-row settings-actions secondary-actions">
            <button type="button" class="primary" data-action="save-sync-profile">保存加密档案</button>
            <button type="button" data-action="load-sync-profile">读取加密档案</button>
            <button type="button" data-action="copy-sync-profile-package">复制配置字符串</button>
          </div>
          <details class="sync-advanced">
            <summary>明文配置包（过渡）</summary>
            <p>旧配置包包含访问密钥，只给自己的设备临时使用；长期建议使用上面的加密档案。</p>
            <div class="button-row settings-actions secondary-actions">
              <button type="button" data-action="copy-sync-config-package">复制明文配置包</button>
              <button type="button" data-action="paste-sync-config-package">从剪贴板导入</button>
            </div>
            <textarea id="sync-config-package" rows="3" placeholder="也可以把配置包粘贴到这里，然后点击导入。"></textarea>
            <div class="button-row settings-actions secondary-actions">
              <button type="button" data-action="import-sync-config-package">导入明文配置包</button>
            </div>
          </details>
        </details>
      </form>
    `;
  }

  function bindEvents() {
    app.querySelectorAll("[data-tab]").forEach((button) => {
      button.addEventListener("click", () => {
        state.activeTab = button.dataset.tab;
        if (state.activeTab === "data") state.dataView = "summary";
        if (state.activeTab === "timer") state.timerFrameUrl = "";
        if (state.activeTab === "settings" && !SETTINGS_SECTION_META[state.settingsSection]) state.settingsSection = "cloud";
        state.detailOpen = false;
        state.editMode = false;
        clearEditorDrafts();
        state.message = "";
        render();
      });
    });

    app.querySelectorAll("[data-settings-section]").forEach((button) => {
      button.addEventListener("click", () => {
        state.settingsSection = SETTINGS_SECTION_META[button.dataset.settingsSection] ? button.dataset.settingsSection : "cloud";
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

    const timerFrame = app.querySelector("[data-timer-frame]");
    if (timerFrame) {
      const sendConfig = () => sendTimerConfigToFrame(timerFrame);
      timerFrame.addEventListener("load", sendConfig);
      window.setTimeout(sendConfig, 250);
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

    app.querySelectorAll("[data-action='copy-sync-config-package']").forEach((button) => {
      button.addEventListener("click", copySyncConfigPackage);
    });

    app.querySelectorAll("[data-action='paste-sync-config-package']").forEach((button) => {
      button.addEventListener("click", pasteSyncConfigPackage);
    });

    app.querySelectorAll("[data-action='import-sync-config-package']").forEach((button) => {
      button.addEventListener("click", importSyncConfigPackage);
    });

    app.querySelectorAll("[data-action='save-sync-profile']").forEach((button) => {
      button.addEventListener("click", saveEncryptedSyncProfile);
    });

    app.querySelectorAll("[data-action='load-sync-profile']").forEach((button) => {
      button.addEventListener("click", loadEncryptedSyncProfile);
    });

    app.querySelectorAll("[data-action='copy-sync-profile-package']").forEach((button) => {
      button.addEventListener("click", copySyncProfilePackage);
    });

    app.querySelectorAll("[data-timer-filter]").forEach((input) => {
      input.addEventListener("change", () => {
        state.timerFilters[input.dataset.timerFilter] = input.value || (input.dataset.timerFilter === "date" ? "" : "all");
        state.timerFilters.selectedSessionId = "";
        render();
      });
    });

    app.querySelectorAll("[data-feedback-range]").forEach((input) => {
      input.addEventListener("change", () => {
        state.feedbackDays = clamp(Math.round(toNullableNumber(input.value) || 14), 7, 30);
        state.feedbackExport = null;
        render();
      });
    });

    const planPatchInput = app.querySelector("#plan-patch-input");
    if (planPatchInput) {
      planPatchInput.addEventListener("input", () => {
        state.planPatchText = planPatchInput.value;
        state.planPatchPreview = null;
      });
    }

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
    bindAction("enable-edit-training", () => enableSelectedEditorSection("training"));
    bindAction("enable-edit-status", () => enableSelectedEditorSection("status"));
    bindAction("cancel-edit", cancelSelectedEdit);
    bindAction("delete-date", deleteSelectedDate);
    bindAction("restore-seed", restoreSeed);
    bindAction("confirm-timer-session", confirmTimerSession);
    bindAction("select-timer-session", selectTimerSession);
    bindAction("draft-timer-session-training", openTimerSessionTrainingDraft);
    bindAction("convert-timer-session", convertTimerSession);
    bindAction("link-timer-session", linkTimerSessionToExistingLog);
    bindAction("mark-timer-session-role", markTimerSessionRole);
    bindAction("ignore-timer-session", ignoreTimerSession);
    bindAction("open-timer-plan", openTimerFromPlan);
    bindAction("open-all-records", openAllRecords);
    bindAction("back-to-data", backToDataSummary);
    bindAction("generate-feedback", generateFeedbackExport);
    bindAction("copy-feedback", copyFeedbackExport);
    bindAction("download-feedback", downloadFeedbackExport);
    bindAction("parse-plan-patch", parsePlanPatchFromInput);
    bindAction("apply-plan-patch", applyPlanPatchFromPreview);
    bindAction("clear-plan-patch", clearPlanPatchInput);
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

  function getEditorSections() {
    return state.editorSections || { training: true, status: true };
  }

  function openAllEditorSections() {
    state.editorSections = { training: true, status: true };
  }

  function openEditorSection(section) {
    state.editorSections = {
      training: section === "training",
      status: section === "status"
    };
  }

  function closeEditorSection(section) {
    const sections = { ...getEditorSections(), [section]: false };
    state.editorSections = sections;
    clearEditorDraftSection(section);
    if (!sections.training && !sections.status) {
      state.editMode = false;
      clearEditorDrafts();
    }
  }

  function clearEditorDraftSection(section) {
    if (!state.editorDrafts) return;
    state.editorDrafts = {
      ...state.editorDrafts,
      [section]: {}
    };
  }

  function clearEditorDrafts() {
    state.editorDrafts = null;
    state.editorSections = null;
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

  async function generateFeedbackExport() {
    await persistFeedbackExport();
    render();
  }

  async function copyFeedbackExport() {
    const output = state.feedbackExport || await persistFeedbackExport();
    try {
      await navigator.clipboard.writeText(output.text);
      state.message = "反馈摘要已复制";
    } catch (error) {
      window.prompt("复制这段反馈摘要给 Codex：", output.text);
      state.message = "浏览器未允许自动复制，已弹出摘要文本";
    }
    render();
  }

  async function downloadFeedbackExport() {
    const output = state.feedbackExport || await persistFeedbackExport();
    downloadTextFile(`shenk-feedback-${output.period.from}_${output.period.to}.md`, output.text, "text/markdown");
    state.message = "反馈摘要已下载";
    render();
  }

  async function persistFeedbackExport() {
    const output = buildFeedbackExport(state.feedbackDays);
    state.feedbackExport = output;
    upsertSharedEnvelope(state.records, "feedback_summaries", output.summary);
    const message = `已生成 ${output.period.from} - ${output.period.to} 反馈摘要`;
    await saveSnapshot(message);
    await autoPushDirtyRecords(message);
    return output;
  }

  function buildFeedbackExport(days) {
    const period = getPeriodForRecentDays(days);
    const dates = enumerateDates(period.from, period.to);
    const workouts = state.workouts.filter((item) => dateInRange(item.date, period.from, period.to));
    const metrics = state.bodyMetrics.filter((item) => dateInRange(item.date, period.from, period.to));
    const timerSessions = getAllTimerSessions().filter((item) => dateInRange(item.data?.date, period.from, period.to));
    const planItems = (state.records.daily_plan_items || []).filter((item) => !item.deletedAt && dateInRange(item.data?.date, period.from, period.to));
    const adjustments = (state.records.plan_adjustments || []).filter((item) => !item.deletedAt && dateInRange(item.data?.date, period.from, period.to));
    const todayRecommendation = recommendationToFeedback(todayISO(), getDisplayRecommendation(todayISO()));
    const upcomingRecommendations = dates
      .filter((date) => date >= todayISO())
      .slice(0, 7)
      .map((date) => recommendationToFeedback(date, getDisplayRecommendation(date)));
    const generatedAt = new Date().toISOString();
    const summary = {
      id: `feedback_${period.from}_${period.to}`,
      schema: "shenk_feedback_summary",
      schemaVersion: "1.0",
      generatedAt,
      period,
      currentPlan: getCurrentPlanForFeedback(),
      actualSummary: summarizeFeedbackWorkouts(workouts),
      planSummary: summarizeFeedbackPlans(planItems, adjustments, dates, workouts),
      timerSessions: timerSessions.map(timerSessionToFeedback),
      trainingLogs: workouts.map(workoutToFeedback),
      bodyMetrics: metrics.map(metricToFeedback),
      bodyTrend: summarizeBodyTrend(metrics),
      painTrend: summarizePainTrend(metrics),
      localRecommendations: {
        today: todayRecommendation,
        upcoming: upcomingRecommendations
      },
      openQuestions: buildFeedbackOpenQuestions(workouts, metrics, timerSessions)
    };
    return {
      period,
      summary,
      text: renderFeedbackMarkdown(summary)
    };
  }

  function getPeriodForRecentDays(days) {
    const to = todayISO();
    const start = parseIsoDate(to);
    start.setDate(start.getDate() - Math.max(1, days) + 1);
    return { from: dateToISO(start), to, days };
  }

  function enumerateDates(from, to) {
    const dates = [];
    const cursor = parseIsoDate(from);
    const end = parseIsoDate(to);
    while (cursor <= end) {
      dates.push(dateToISO(cursor));
      cursor.setDate(cursor.getDate() + 1);
    }
    return dates;
  }

  function dateInRange(date, from, to) {
    return isIsoDate(date) && date >= from && date <= to;
  }

  function workoutToFeedback(record) {
    const meta = TYPE_META[record.type] || TYPE_META.easyWalk;
    return {
      date: record.date,
      type: toSharedTrainingType(record.type),
      typeLabel: meta.label,
      status: toSharedCompletionStatus(record.status, toSharedTrainingType(record.type)),
      statusLabel: getStatusLabel(record.status, record.type),
      source: record.source || "manual",
      durationSec: toNullableNumber(record.durationSec),
      distanceKm: toNullableNumber(record.distanceKm),
      avgHeartRate: toNullableNumber(record.avgHeartRate),
      avgPace: getPace(record) || "",
      timerLinked: Boolean(record.timerSessionId || (Array.isArray(record.timerSessionIds) && record.timerSessionIds.length)),
      notes: formatPublicNotes(record.notes)
    };
  }

  function metricToFeedback(metric) {
    return {
      date: metric.date,
      weightKg: toNullableNumber(metric.weightKg),
      waistCm: toNullableNumber(metric.waistCm),
      bodyFatPct: toNullableNumber(metric.bodyFatPct),
      muscleKg: toNullableNumber(metric.muscleKg),
      sleepQuality: metric.sleepQuality || "normal",
      sleepLabel: SLEEP_META[metric.sleepQuality] || "一般",
      energy: toNullableNumber(metric.energy),
      fatigue: normalizeSharedFatigue(metric.fatigue),
      fatigueLabel: FATIGUE_META[metric.fatigue] || FATIGUE_META[sharedFatigueToLegacy(metric.fatigue)] || "正常",
      painSummary: formatPainSummary(metric.pain) || "无",
      notes: formatPublicNotes(metric.notes)
    };
  }

  function timerSessionToFeedback(envelope) {
    const data = envelope.data || {};
    const handling = getTimerSessionHandling(envelope);
    return {
      date: data.date,
      title: getTimerSessionTitle(data),
      type: data.trainingType || "",
      typeLabel: getTimerTypeMeta(data.trainingType).label,
      startedAt: data.startedAt || "",
      endedAt: data.endedAt || "",
      actualSeconds: toNullableNumber(data.actualSeconds),
      duration: data.actualSeconds ? formatDuration(data.actualSeconds) : "",
      completion: data.completion || "",
      completionLabel: getTimerCompletionLabel(data.completion),
      handling: handling.action,
      handlingLabel: handling.label,
      role: handling.role || "",
      roleLabel: handling.roleLabel || "",
      notes: formatPublicNotes(data.notes)
    };
  }

  function recommendationToFeedback(date, recommendation) {
    const type = recommendation.type || "easyWalk";
    return {
      date,
      type: toSharedTrainingType(type),
      typeLabel: TYPE_META[type]?.label || getTimerTypeMeta(type).label,
      title: recommendation.title || TYPE_META[type]?.label || "普通走",
      label: recommendation.label || "",
      minutes: recommendation.minutes || null,
      reasons: Array.isArray(recommendation.reasons) ? recommendation.reasons : [],
      sourceLabel: recommendation.sourceLabel || "本地规则"
    };
  }

  function summarizeFeedbackWorkouts(workouts) {
    const completed = workouts.filter(isCompletedRecord).length;
    const distanceKm = workouts.reduce((sum, item) => sum + (toNullableNumber(item.distanceKm) || 0), 0);
    const durationSec = workouts.reduce((sum, item) => sum + (toNullableNumber(item.durationSec) || 0), 0);
    return {
      total: workouts.length,
      completed,
      skipped: workouts.filter((item) => item.status === "skipped").length,
      short: workouts.filter((item) => item.status === "short").length,
      stretchOnly: workouts.filter((item) => item.status === "stretchOnly").length,
      distanceKm: Number(distanceKm.toFixed(2)),
      durationSec,
      byType: countBy(workouts, (item) => TYPE_META[item.type]?.label || item.type)
    };
  }

  function summarizeFeedbackPlans(planItems, adjustments, dates, workouts) {
    const workoutDates = new Set(workouts.map((item) => item.date));
    const plannedDates = new Set(planItems.map((item) => item.data?.date).filter(Boolean));
    return {
      plannedDays: plannedDates.size,
      adjustmentCount: adjustments.length,
      plannedWithoutActual: Array.from(plannedDates).filter((date) => date <= todayISO() && !workoutDates.has(date)),
      fallbackDays: dates.filter((date) => !plannedDates.has(date) && !workoutDates.has(date)).length,
      planItems: planItems.map((item) => {
        const data = item.data || {};
        return {
          date: data.date,
          title: getPlanItemDisplayTitle(data) || data.title || "",
          trainingType: data.trainingType || "",
          estimatedMinutes: toNullableNumber(data.estimatedMinutes),
          status: data.status || "planned",
          notes: formatPublicNotes(data.notes)
        };
      }),
      adjustments: adjustments.map((item) => {
        const data = item.data || {};
        return {
          date: data.date,
          reason: formatPublicNotes(data.reason),
          from: getPlanItemDisplayTitle(data.fromSnapshot || {}) || data.fromSnapshot?.title || "",
          to: getPlanItemDisplayTitle(data.toSnapshot || {}) || data.toSnapshot?.title || ""
        };
      })
    };
  }

  function summarizeBodyTrend(metrics) {
    return {
      count: metrics.length,
      weight: numericTrend(metrics, "weightKg", "kg"),
      waist: numericTrend(metrics, "waistCm", "cm"),
      bodyFat: numericTrend(metrics, "bodyFatPct", "%"),
      muscle: numericTrend(metrics, "muscleKg", "kg")
    };
  }

  function numericTrend(records, key, unit) {
    const values = records.filter((item) => item[key] !== null && item[key] !== undefined);
    if (!values.length) return null;
    const first = values[0];
    const latest = values[values.length - 1];
    const delta = latest[key] - first[key];
    return {
      first: { date: first.date, value: first[key] },
      latest: { date: latest.date, value: latest[key] },
      delta: Number(delta.toFixed(1)),
      unit
    };
  }

  function summarizePainTrend(metrics) {
    return metrics
      .map((metric) => ({ date: metric.date, pain: formatPainSummary(metric.pain), fatigue: FATIGUE_META[metric.fatigue] || "正常" }))
      .filter((item) => item.pain || item.fatigue !== "正常");
  }

  function getCurrentPlanForFeedback() {
    const plans = (state.records.plan_templates || []).filter((item) => !item.deletedAt).map((item) => item.data || {});
    if (!plans.length) return null;
    const active = plans.find((item) => item.status === "active") || plans.sort((a, b) => String(b.updatedAt || "").localeCompare(String(a.updatedAt || "")))[0];
    return {
      title: active.title || "",
      version: active.version || "",
      status: active.status || "",
      effectiveFrom: active.effectiveFrom || null,
      effectiveTo: active.effectiveTo || null,
      goal: active.goal || [],
      rules: active.rules || {}
    };
  }

  function buildFeedbackOpenQuestions(workouts, metrics, timerSessions) {
    const questions = [];
    const recentPain = metrics.filter((item) => formatPainSummary(item.pain)).slice(-3);
    if (recentPain.length) questions.push("近期仍有疼痛记录，是否需要继续降低提高走或力量密度？");
    if (workouts.some((item) => item.status === "skipped" || item.status === "short")) questions.push("最近存在跳过或缩短训练，是否需要调整下一阶段计划容量？");
    if (timerSessions.some((item) => getTimerSessionHandling(item).action === "pending")) questions.push("仍有计时器记录未确认，是否先补齐正式训练记录再改计划？");
    return questions;
  }

  function countBy(items, keyFn) {
    return items.reduce((acc, item) => {
      const key = keyFn(item) || "其他";
      acc[key] = (acc[key] || 0) + 1;
      return acc;
    }, {});
  }

  function renderFeedbackMarkdown(summary) {
    const actual = summary.actualSummary;
    const body = summary.bodyTrend;
    const recommendation = summary.localRecommendations.today;
    const lines = [
      "# 身刻反馈摘要",
      "",
      `范围：${summary.period.from} 至 ${summary.period.to}（${summary.period.days} 天）`,
      `生成时间：${summary.generatedAt}`,
      "",
      "## 快速概览",
      `- 训练记录：${actual.total} 条，完成 ${actual.completed} 条，跳过 ${actual.skipped} 条，短版 ${actual.short} 条，只拉伸 ${actual.stretchOnly} 条。`,
      `- 训练总量：${formatDuration(actual.durationSec)}，距离 ${formatNumber(actual.distanceKm, 2)} km。`,
      `- 计时器记录：${summary.timerSessions.length} 条，其中待处理 ${summary.timerSessions.filter((item) => item.handling === "pending").length} 条。`,
      `- 状态记录：${summary.bodyMetrics.length} 条。`,
      body.weight ? `- 体重：${body.weight.latest.value} ${body.weight.unit}，区间变化 ${body.weight.delta} ${body.weight.unit}。` : "- 体重：无记录。",
      body.waist ? `- 腰围：${body.waist.latest.value} ${body.waist.unit}，区间变化 ${body.waist.delta} ${body.waist.unit}。` : "- 腰围：无记录。",
      "",
      "## 今日本地建议",
      `- ${recommendation.title}${recommendation.minutes ? `，${recommendation.minutes} 分钟` : ""}。`,
      ...recommendation.reasons.map((reason) => `- ${reason}`),
      "",
      "## 最近训练",
      ...(summary.trainingLogs.length ? summary.trainingLogs.map((item) => `- ${item.date}：${item.typeLabel}，${item.statusLabel}${item.durationSec ? `，${formatDuration(item.durationSec)}` : ""}${item.distanceKm ? `，${formatNumber(item.distanceKm, 2)} km` : ""}${item.notes ? `。备注：${item.notes}` : ""}`) : ["- 无训练记录。"]),
      "",
      "## 身体状态",
      ...(summary.bodyMetrics.length ? summary.bodyMetrics.map((item) => `- ${item.date}：睡眠${item.sleepLabel}，精力 ${item.energy || "-"}，疲劳${item.fatigueLabel}，疼痛 ${item.painSummary}${item.weightKg ? `，体重 ${item.weightKg} kg` : ""}${item.waistCm ? `，腰围 ${item.waistCm} cm` : ""}${item.notes ? `。备注：${item.notes}` : ""}`) : ["- 无身体状态记录。"]),
      "",
      "## 结构化 JSON",
      "```json",
      JSON.stringify(summary, null, 2),
      "```"
    ];
    return lines.join("\n");
  }

  function downloadTextFile(filename, text, type) {
    const blob = new Blob([text], { type: type || "text/plain" });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = filename;
    document.body.appendChild(link);
    link.click();
    link.remove();
    URL.revokeObjectURL(url);
  }

  function parsePlanPatchFromInput() {
    const input = app.querySelector("#plan-patch-input");
    state.planPatchText = input ? input.value : state.planPatchText;
    try {
      const patch = extractCoachPlanPatch(state.planPatchText);
      const preview = previewPlanPatch(patch);
      state.planPatchPreview = { patch, error: null };
      state.message = preview.valid ? "计划草案已通过校验" : "计划草案需要修正";
    } catch (error) {
      state.planPatchPreview = { patch: null, error: error.message || "无法识别计划草案" };
      state.message = "计划草案解析失败";
    }
    render();
  }

  async function applyPlanPatchFromPreview() {
    const patch = state.planPatchPreview?.patch || extractCoachPlanPatch(state.planPatchText);
    const preview = previewPlanPatch(patch);
    if (!preview.valid) {
      state.planPatchPreview = { patch, error: null };
      state.message = "计划草案未通过校验";
      render();
      return;
    }
    if (!window.confirm(`确认写入计划草案？将新增 ${preview.totals.add} 条，更新 ${preview.totals.update} 条，删除 ${preview.totals.delete} 条。`)) return;
    if (preview.deleteCount && !window.confirm(`本次包含 ${preview.deleteCount} 条删除。删除只会处理草案里明确 operation: "delete" 或 deletedAt 的记录。确认继续？`)) return;
    const result = applyCoachPlanPatch(patch);
    state.planPatchText = "";
    state.planPatchPreview = null;
    if (result.firstDate) state.visibleMonth = result.firstDate.slice(0, 7);
    const message = `计划草案已写入：新增 ${result.added}，更新 ${result.updated}，删除 ${result.deleted}`;
    await saveSnapshot(message);
    await autoPushDirtyRecords(message);
    state.message = message;
    render();
  }

  function clearPlanPatchInput() {
    state.planPatchText = "";
    state.planPatchPreview = null;
    render();
  }

  function extractCoachPlanPatch(text) {
    const candidates = extractJsonCandidates(String(text || ""));
    for (const candidate of candidates) {
      try {
        const parsed = JSON.parse(candidate);
        const patch = unwrapCoachPlanPatch(parsed);
        if (patch) return patch;
      } catch (error) {
        // Keep trying later JSON candidates from the pasted Codex reply.
      }
    }
    throw new Error("未找到 schema 为 coach_plan_patch 的 JSON。");
  }

  function extractJsonCandidates(text) {
    const codeBlocks = Array.from(text.matchAll(/```(?:json)?\s*([\s\S]*?)```/gi)).map((match) => match[1].trim());
    const candidates = [...codeBlocks];
    let start = -1;
    let depth = 0;
    let inString = false;
    let escape = false;
    for (let index = 0; index < text.length; index += 1) {
      const char = text[index];
      if (inString) {
        if (escape) {
          escape = false;
        } else if (char === "\\") {
          escape = true;
        } else if (char === "\"") {
          inString = false;
        }
        continue;
      }
      if (char === "\"") {
        inString = true;
      } else if (char === "{") {
        if (depth === 0) start = index;
        depth += 1;
      } else if (char === "}") {
        depth -= 1;
        if (depth === 0 && start >= 0) {
          candidates.push(text.slice(start, index + 1));
          start = -1;
        }
      }
    }
    return candidates.length ? candidates : [text.trim()];
  }

  function unwrapCoachPlanPatch(parsed) {
    if (!parsed || typeof parsed !== "object") return null;
    if (parsed.schema === "coach_plan_patch") return parsed;
    if (parsed.coach_plan_patch) return unwrapCoachPlanPatch(parsed.coach_plan_patch);
    if (parsed.patch) return unwrapCoachPlanPatch(parsed.patch);
    return null;
  }

  function getPatchArray(patch, key) {
    const value = patch?.[key];
    return Array.isArray(value) && value.length ? value : [];
  }

  function getPatchPlanTemplates(patch) {
    const list = getPatchArray(patch, "planTemplates");
    if (list.length) return list;
    const single = patch?.planTemplate;
    return isNonEmptyObject(single) ? [single] : [];
  }

  function isNonEmptyObject(value) {
    return value && typeof value === "object" && !Array.isArray(value) && Object.keys(value).length > 0;
  }

  function getPatchItemId(item) {
    if (!item || typeof item !== "object") return "";
    return String(item.id || item.routineId || item.routine_id || "").trim();
  }

  function isPatchDeleteItem(item) {
    if (!item || typeof item !== "object") return false;
    const operation = String(item.operation || item.op || item.action || "").trim().toLowerCase();
    return operation === "delete" || Boolean(item.deletedAt || item.deleted_at);
  }

  function findSharedRecordById(entity, id, includeDeleted = false) {
    if (!id) return null;
    return (state.records[entity] || []).find((item) => {
      if (!includeDeleted && item.deletedAt) return false;
      return item.id === id || item.data?.id === id;
    }) || null;
  }

  function buildPatchEntityPreview(entity, rawItems, normalizeItem, options = {}) {
    const rows = [];
    const counts = { add: 0, update: 0, delete: 0, skipped: 0, invalid: 0, rows };
    rawItems.forEach((item, index) => {
      const id = getPatchItemId(item);
      if (isPatchDeleteItem(item)) {
        if (!id) {
          counts.invalid += 1;
          rows.push({ entity, id: "", action: "无效删除", reason: `${options.label || entity}[${index}] 缺少 id。` });
          return;
        }
        const existing = findSharedRecordById(entity, id, true);
        if (!existing || existing.deletedAt) {
          counts.skipped += 1;
          rows.push({ entity, id, action: "跳过", reason: "记录不存在或已删除。" });
          return;
        }
        counts.delete += 1;
        rows.push({ entity, id, action: "删除", reason: "" });
        return;
      }

      if (options.requireInputId && !id) {
        counts.invalid += 1;
        rows.push({ entity, id: "", action: "无效", reason: `${options.label || entity}[${index}] 缺少 id。` });
        return;
      }

      const normalized = normalizeItem(item);
      if (!normalized?.id) {
        counts.invalid += 1;
        rows.push({ entity, id, action: "无效", reason: `${options.label || entity}[${index}] 缺少必填字段。` });
        return;
      }
      const invalidReason = options.invalidReason ? options.invalidReason(normalized, item, index) : "";
      if (invalidReason) {
        counts.invalid += 1;
        rows.push({ entity, id: normalized.id, action: "无效", reason: invalidReason });
        return;
      }
      const skipReason = options.skipReason ? options.skipReason(normalized) : "";
      if (skipReason) {
        counts.skipped += 1;
        rows.push({ entity, id: normalized.id, action: "跳过", reason: skipReason });
        return;
      }
      const existing = findSharedRecordById(entity, normalized.id, true);
      if (existing && !existing.deletedAt) {
        counts.update += 1;
        rows.push({ entity, id: normalized.id, action: "更新", reason: "" });
      } else {
        counts.add += 1;
        rows.push({ entity, id: normalized.id, action: "新增", reason: "" });
      }
    });
    return counts;
  }

  function sumPatchCounts(counts) {
    return counts.reduce((total, item) => ({
      add: total.add + item.add,
      update: total.update + item.update,
      delete: total.delete + item.delete,
      skipped: total.skipped + item.skipped,
      invalid: total.invalid + item.invalid
    }), { add: 0, update: 0, delete: 0, skipped: 0, invalid: 0 });
  }

  function formatPatchCounts(counts) {
    return `新增 ${counts.add}，更新 ${counts.update}，删除 ${counts.delete}`;
  }

  function getAvailableRoutineIdsForPatch(patch) {
    const ids = new Set();
    const deletedIds = new Set();
    getPatchArray(patch, "routineTemplates").forEach((item) => {
      const id = getPatchItemId(item);
      if (!id) return;
      if (isPatchDeleteItem(item)) deletedIds.add(id);
      else ids.add(id);
    });
    (state.records.routine_templates || []).forEach((item) => {
      const id = item.data?.id || item.id;
      if (!id || item.deletedAt || deletedIds.has(id)) return;
      ids.add(id);
    });
    return ids;
  }

  function getDailyPlanRoutineIssue(item, availableRoutineIds) {
    if (!item) return "";
    const routineId = String(item.routineId || item.routine_id || "").trim();
    if ((item.needsTimer || item.needs_timer) && !routineId) return "需要计时器执行的日计划缺少 routineId。";
    if (routineId && !availableRoutineIds.has(routineId)) return `routineId 未找到对应的 routine_templates：${routineId}`;
    return "";
  }

  function previewPlanPatch(patch) {
    const errors = validateCoachPlanPatch(patch);
    const warnings = [...errors];
    if (patch?.replaceMode) warnings.push("检测到 replaceMode: true。身刻仍按安全合并处理，只有明确 operation: delete 或 deletedAt 的记录才会删除。");
    const now = new Date().toISOString();
    const availableRoutineIds = getAvailableRoutineIdsForPatch(patch);
    const planPreview = buildPatchEntityPreview(
      "plan_templates",
      getPatchPlanTemplates(patch),
      (item) => normalizeLooseSharedData({
        ...item,
        createdBy: item.createdBy || "coach",
        generatedBy: patch.generatedBy || "codex",
        effectiveFrom: item.effectiveFrom || patch.effectiveFrom,
        effectiveTo: item.effectiveTo ?? patch.effectiveTo ?? null,
        updatedAt: now,
        createdAt: item.createdAt || now
      }, "plan"),
      { label: "planTemplates", requireInputId: true }
    );
    const routinePreview = buildPatchEntityPreview(
      "routine_templates",
      getPatchArray(patch, "routineTemplates"),
      (item) => normalizeRoutineTemplateData({ ...item, updatedAt: now, createdAt: item.createdAt || now }),
      { label: "routineTemplates", requireInputId: true }
    );
    const dailyPreviewCounts = buildPatchEntityPreview(
      "daily_plan_items",
      getPatchArray(patch, "dailyPlanItems"),
      (item) => normalizeDailyPlanItemData({ ...item, updatedAt: now, createdAt: item.createdAt || now }),
      {
        label: "dailyPlanItems",
        invalidReason: (item) => getDailyPlanRoutineIssue(item, availableRoutineIds),
        skipReason: (item) => {
          if (item.date < todayISO()) return "过去日期不改写。";
          if (getWorkoutsByDate(item.date).length) return "已有实际训练记录，不覆盖。";
          return "";
        }
      }
    );
    const adjustmentPreview = buildPatchEntityPreview(
      "plan_adjustments",
      getPatchArray(patch, "planAdjustments"),
      (item) => normalizePlanAdjustmentData({
        ...item,
        adjustedBy: item.adjustedBy || patch.generatedBy || "coach",
        reason: item.reason || patch.reason || "",
        updatedAt: now,
        createdAt: item.createdAt || now
      }),
      { label: "planAdjustments" }
    );
    const totals = sumPatchCounts([planPreview, routinePreview, dailyPreviewCounts, adjustmentPreview]);
    const previewRows = [
      ...planPreview.rows,
      ...routinePreview.rows,
      ...dailyPreviewCounts.rows,
      ...adjustmentPreview.rows
    ];
    previewRows.filter((row) => row.action === "无效" || row.action === "无效删除").forEach((row) => warnings.push(row.reason));
    return {
      valid: !errors.length && totals.invalid === 0,
      warnings,
      planPreview,
      routinePreview,
      dailyPreviewCounts,
      adjustmentPreview,
      totals,
      deleteCount: totals.delete,
      newDailyCount: dailyPreviewCounts.add,
      updatedDailyCount: dailyPreviewCounts.update,
      skippedDailyCount: dailyPreviewCounts.skipped,
      adjustmentCount: adjustmentPreview.add + adjustmentPreview.update + adjustmentPreview.delete,
      skippedDates: dailyPreviewCounts.rows.filter((item) => item.action === "跳过").map((item) => item.id),
      dailyPreview: dailyPreviewCounts.rows.slice(0, 16),
      previewRows: previewRows.slice(0, 24)
    };
  }

  function validateCoachPlanPatch(patch) {
    const errors = [];
    if (!patch || typeof patch !== "object") return ["计划草案不是有效对象。"];
    if (patch.schema !== "coach_plan_patch") errors.push("schema 必须是 coach_plan_patch。");
    if (!isIsoDate(patch.effectiveFrom)) errors.push("effectiveFrom 必须是 YYYY-MM-DD。");
    if (patch.effectiveTo && !isIsoDate(patch.effectiveTo)) errors.push("effectiveTo 必须是 YYYY-MM-DD。");
    const hasPlan = getPatchPlanTemplates(patch).length > 0;
    const hasRoutines = Array.isArray(patch.routineTemplates) && patch.routineTemplates.length;
    const hasDaily = Array.isArray(patch.dailyPlanItems) && patch.dailyPlanItems.length;
    const hasAdjustments = Array.isArray(patch.planAdjustments) && patch.planAdjustments.length;
    if (!hasPlan && !hasRoutines && !hasDaily && !hasAdjustments) errors.push("草案没有可写入的计划内容。");
    if (hasDaily) {
      patch.dailyPlanItems.forEach((item, index) => {
        if (!item || typeof item !== "object") {
          errors.push(`dailyPlanItems[${index}] 不是有效对象。`);
        } else if (isPatchDeleteItem(item)) {
          if (!getPatchItemId(item)) errors.push(`dailyPlanItems[${index}] 删除操作缺少 id。`);
        } else if (!isIsoDate(item.date)) {
          errors.push(`dailyPlanItems[${index}] 缺少有效日期。`);
        }
      });
    }
    return errors;
  }

  function applyCoachPlanPatch(patch) {
    const preview = previewPlanPatch(patch);
    if (!preview.valid) throw new Error("计划草案未通过校验。");
    const now = new Date().toISOString();
    const counters = { added: 0, updated: 0, deleted: 0, skipped: 0 };
    let firstDate = null;

    getPatchPlanTemplates(patch).forEach((item) => {
      if (!getPatchItemId(item)) {
        counters.skipped += 1;
        return;
      }
      const data = normalizeLooseSharedData({
        ...item,
        createdBy: item.createdBy || "coach",
        generatedBy: patch.generatedBy || "codex",
        effectiveFrom: item.effectiveFrom || patch.effectiveFrom,
        effectiveTo: item.effectiveTo ?? patch.effectiveTo ?? null,
        updatedAt: now,
        createdAt: item.createdAt || now
      }, "plan");
      applyPatchEntityRecord("plan_templates", item, data, counters, now);
    });

    getPatchArray(patch, "routineTemplates").forEach((routine) => {
      if (!getPatchItemId(routine)) {
        counters.skipped += 1;
        return;
      }
      const data = normalizeRoutineTemplateData({
        ...routine,
        updatedAt: now,
        createdAt: routine.createdAt || now
      });
      applyPatchEntityRecord("routine_templates", routine, data, counters, now);
    });

    getPatchArray(patch, "dailyPlanItems").forEach((item) => {
      const data = normalizeDailyPlanItemData({
        ...item,
        updatedAt: now,
        createdAt: item.createdAt || now
      });
      if (!isPatchDeleteItem(item) && (!data || data.date < todayISO() || getWorkoutsByDate(data.date).length)) {
        counters.skipped += 1;
        return;
      }
      const result = applyPatchEntityRecord("daily_plan_items", item, data, counters, now);
      if (result.applied && data?.date) firstDate = firstDate || data.date;
    });

    getPatchArray(patch, "planAdjustments").forEach((adjustment) => {
      const data = normalizePlanAdjustmentData({
        ...adjustment,
        adjustedBy: adjustment.adjustedBy || patch.generatedBy || "coach",
        reason: adjustment.reason || patch.reason || "",
        updatedAt: now,
        createdAt: adjustment.createdAt || now
      });
      const result = applyPatchEntityRecord("plan_adjustments", adjustment, data, counters, now);
      if (result.applied && data?.date) firstDate = firstDate || data.date;
    });

    return { ...counters, firstDate };
  }

  function applyPatchEntityRecord(entity, rawItem, normalizedData, counters, now) {
    if (isPatchDeleteItem(rawItem)) {
      const deleted = markSharedRecordDeleted(entity, rawItem, now);
      counters[deleted ? "deleted" : "skipped"] += 1;
      return { applied: deleted, action: deleted ? "deleted" : "skipped" };
    }
    if (!normalizedData?.id) {
      counters.skipped += 1;
      return { applied: false, action: "skipped" };
    }
    const existing = findSharedRecordById(entity, normalizedData.id, true);
    upsertSharedEnvelope(state.records, entity, normalizedData, existing);
    const added = !existing || existing.deletedAt;
    counters[added ? "added" : "updated"] += 1;
    return { applied: true, action: added ? "added" : "updated" };
  }

  function markSharedRecordDeleted(entity, rawItem, now) {
    const id = getPatchItemId(rawItem);
    if (!id) return false;
    const existing = findSharedRecordById(entity, id, true);
    if (!existing || existing.deletedAt) return false;
    const deletedAt = rawItem.deletedAt || rawItem.deleted_at || now;
    const data = {
      ...(existing.data || {}),
      id,
      updatedAt: now,
      deletedAt
    };
    const envelope = {
      ...existing,
      data,
      updatedAt: now,
      deletedAt,
      syncState: "dirty",
      conflict: null
    };
    state.records[entity] = (state.records[entity] || [])
      .filter((item) => item.id !== existing.id && item.data?.id !== id)
      .concat(envelope)
      .sort(compareSharedEnvelopes);
    return true;
  }

  function previousDate(date) {
    if (!isIsoDate(date)) return null;
    const value = parseIsoDate(date);
    value.setDate(value.getDate() - 1);
    return dateToISO(value);
  }

  function findSharedRecord(entity, id) {
    if (!id) return null;
    return (state.records[entity] || []).find((item) => !item.deletedAt && (item.id === id || item.data?.id === id)) || null;
  }

  function findPlanItemForDate(date) {
    return getPlanItemsByDate(date)[0] || null;
  }

  async function handleTrainingSubmit(event) {
    event.preventDefault();
    const previousTrainingDraft = state.editorDrafts?.training || {};
    captureEditorDrafts();
    const data = new FormData(event.currentTarget);
    const date = String(data.get("date"));
    if (!isIsoDate(date)) return;
    const requestedTrainingLogId = optionalString(data.get("trainingLogId") || previousTrainingDraft.trainingLogId);
    const requestedTimerSessionId = optionalString(data.get("timerSessionId") || previousTrainingDraft.timerSessionId);
    const existing = findWorkoutForTrainingSubmit(date, requestedTrainingLogId, requestedTimerSessionId);
    const now = new Date().toISOString();
    const durationMin = toNullableNumber(data.get("durationMin"));
    const workoutType = TYPE_META[String(data.get("type"))] ? String(data.get("type")) : "easyWalk";
    const workoutStatus = getStatusForType(workoutType, String(data.get("status")));
    const isRest = workoutType === "rest";
    const timerSessionIds = collectTimerSessionIds(
      data.get("timerSessionIds") || previousTrainingDraft.timerSessionIds,
      requestedTimerSessionId,
      existing?.timerSessionIds
    );
    const dailyPlanItemId = optionalString(data.get("dailyPlanItemId") || previousTrainingDraft.dailyPlanItemId || existing?.dailyPlanItemId);
    const source = optionalString(data.get("source") || previousTrainingDraft.source || existing?.source) || (requestedTimerSessionId ? "timer" : "manual");
    const rawJson = mergeTrainingRawJson(existing?.rawJson, data.get("timerSessionJson") || previousTrainingDraft.timerSessionJson);
    const workout = normalizeWorkout({
      id: existing ? existing.id : makeId(),
      trainingLogId: requestedTrainingLogId || existing?.trainingLogId || (requestedTimerSessionId ? `log_${date}_${safeIdPart(requestedTimerSessionId)}` : null),
      dailyPlanItemId,
      timerSessionId: requestedTimerSessionId || existing?.timerSessionId || null,
      timerSessionIds,
      date,
      type: workoutType,
      status: workoutStatus,
      source,
      durationSec: isRest || durationMin === null ? null : Math.round(durationMin * 60),
      distanceKm: isRest ? null : toNullableNumber(data.get("distanceKm")),
      avgHeartRate: isRest ? null : toNullableNumber(data.get("avgHeartRate")),
      fatigue: existing ? existing.fatigue : "normal",
      pain: existing ? existing.pain : { calf: 0, back: 0, wrist: 0, outerThigh: 0 },
      notes: String(data.get("trainingNotes") || "").trim(),
      rawJson,
      createdAt: existing ? existing.createdAt : now,
      updatedAt: now
    });

    upsertWorkout(workout);
    state.selectedDate = date;
    state.visibleMonth = date.slice(0, 7);
    state.detailOpen = true;
    closeEditorSection("training");
    const message = `已保存训练 ${date}`;
    await saveSnapshot(message);
    await autoPushDirtyRecords(message);
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
    closeEditorSection("status");
    const message = `已保存状态 ${date}`;
    await saveSnapshot(message);
    await autoPushDirtyRecords(message);
    render();
  }

  function findWorkoutForTrainingSubmit(date, trainingLogId, timerSessionId) {
    const workouts = getWorkoutsByDate(date);
    if (trainingLogId) {
      const byTrainingLogId = workouts.find((item) => item.trainingLogId === trainingLogId);
      if (byTrainingLogId) return byTrainingLogId;
    }
    if (timerSessionId) {
      const byTimerSessionId = workouts.find((item) => (
        item.timerSessionId === timerSessionId ||
        (Array.isArray(item.timerSessionIds) && item.timerSessionIds.includes(timerSessionId))
      ));
      if (byTimerSessionId) return byTimerSessionId;
      return null;
    }
    return workouts[0] || null;
  }

  function optionalString(value) {
    const text = String(value || "").trim();
    return text || null;
  }

  function collectTimerSessionIds(rawValue, primaryId, existingIds = []) {
    const ids = new Set(Array.isArray(existingIds) ? existingIds.filter(Boolean) : []);
    parseJsonArrayValue(rawValue).forEach((id) => {
      if (id) ids.add(String(id));
    });
    if (primaryId) ids.add(primaryId);
    return Array.from(ids);
  }

  function mergeTrainingRawJson(existingRawJson, timerSessionValue) {
    const rawJson = { ...(existingRawJson || {}) };
    const timerSession = parseJsonObjectValue(timerSessionValue);
    if (Object.keys(timerSession).length) rawJson.timerSession = timerSession;
    return rawJson;
  }

  async function confirmTimerSession(element) {
    openTimerSessionTrainingDraft(element);
  }

  function selectTimerSession(element) {
    state.timerFilters.selectedSessionId = element.dataset.sessionId || "";
    render();
  }

  function convertTimerSession(element) {
    openTimerSessionTrainingDraft(element);
  }

  function openTimerSessionTrainingDraft(element) {
    const envelope = findTimerSessionById(element.dataset.sessionId);
    if (!envelope) return;
    const session = envelope.data;
    const existingLink = getTimerSessionLink(session.id);
    if (existingLink) {
      state.message = "这条运动记录已经处理过";
      render();
      return;
    }
    if (getTrainingLogForTimerSession(session.id)) {
      state.message = "这条运动记录已进入正式训练记录";
      render();
      return;
    }
    if (!canDraftTimerSessionTraining(session)) return;

    const workout = timerSessionToWorkout(session);
    if (!workout) return;
    state.selectedDate = session.date;
    state.visibleMonth = session.date.slice(0, 7);
    state.activeTab = "calendar";
    state.detailOpen = true;
    state.editMode = true;
    state.timerFilters.selectedSessionId = session.id;
    openEditorSection("training");
    state.editorDrafts = {
      date: session.date,
      training: workoutToEditorTrainingDraft(workout),
      status: {}
    };
    state.message = "已带入计时器数据，请补完心率、距离或备注后保存训练";
    render();
  }

  async function linkTimerSessionToExistingLog(element) {
    const envelope = findTimerSessionById(element.dataset.sessionId);
    if (!envelope) return;
    const session = envelope.data;
    if (getTimerSessionLink(session.id)) {
      state.message = "这条运动记录已经处理过";
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
    await refreshAfterTimerSessionAction(session, "已关联到已有训练记录");
  }

  async function markTimerSessionRole(element) {
    const envelope = findTimerSessionById(element.dataset.sessionId);
    if (!envelope) return;
    const session = envelope.data;
    if (getTimerSessionLink(session.id)) {
      state.message = "这条运动记录已经处理过";
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
      state.message = "这条运动记录已经处理过";
      render();
      return;
    }
    upsertTimerSessionLink(createTimerSessionLinkData(session, "ignored", "note", null, "用户忽略"));
    await refreshAfterTimerSessionAction(session, "已忽略运动记录");
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
    await autoPushDirtyRecords(message);
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

  function workoutToEditorTrainingDraft(workout) {
    return {
      date: workout.date,
      type: workout.type,
      status: workout.status,
      durationMin: workout.durationSec ? String(Math.round(workout.durationSec / 60)) : "",
      distanceKm: workout.distanceKm ?? "",
      avgHeartRate: workout.avgHeartRate ?? "",
      trainingNotes: workout.notes || "",
      trainingLogId: workout.trainingLogId || "",
      dailyPlanItemId: workout.dailyPlanItemId || "",
      timerSessionId: workout.timerSessionId || "",
      timerSessionIds: workout.timerSessionIds || [],
      source: workout.source || "",
      timerSessionJson: workout.rawJson?.timerSession || ""
    };
  }

  function timerCompletionToLegacyStatus(session) {
    const completion = String(session.completion || "").toLowerCase();
    if (completion === "completed") return "completed";
    if (completion === "stopped") return toNullableNumber(session.actualSeconds) ? "short" : "skipped";
    return toNullableNumber(session.actualSeconds) ? "short" : "skipped";
  }

  function timerSessionNote(session) {
    const variant = getRoutineVariantLabel(session);
    return [
      `计时器：${getTimerSessionTitle(session)}`,
      variant,
      session.completion ? `完成状态：${getTimerCompletionLabel(session.completion)}` : "",
      formatPublicNotes(session.notes)
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
    state.timerFrameUrl = buildTimerUrl(planItem);
    state.activeTab = "timer";
    state.detailOpen = false;
    state.editMode = false;
    clearEditorDrafts();
    render();
  }

  function getBaseTimerUrl() {
    return normalizeTimerUrl(state.syncConfig.timerUrl || DEFAULT_TIMER_URL);
  }

  function buildTimerUrl(planItem = {}) {
    const url = new URL(getBaseTimerUrl(), window.location.href);
    const params = url.searchParams;
    const timerOptions = planItem.timerOptions && typeof planItem.timerOptions === "object" ? planItem.timerOptions : {};
    const preset = planItem.preset || timerOptions.preset;
    if (planItem.routineId) params.set("routineId", planItem.routineId);
    if (preset) params.set("preset", String(preset));
    if (planItem.date) params.set("date", planItem.date);
    if (planItem.dailyPlanItemId || planItem.id) params.set("dailyPlanItemId", planItem.dailyPlanItemId || planItem.id);
    if (planItem.planTemplateId || planItem.sourcePlanId) params.set("planTemplateId", planItem.planTemplateId || planItem.sourcePlanId);
    if (state.syncConfig.apiBase) params.set("cloudApiBase", state.syncConfig.apiBase);
    params.set("source", "shenk");
    return url.toString();
  }

  function upsertMetric(metric) {
    const data = bodyMetricToSharedData(metric);
    if (!data) return;
    upsertSharedEnvelope(state.records, "body_metrics", data);
    refreshLegacyCachesFromSharedRecords();
  }

  async function deleteSelectedDate() {
    if (!window.confirm(`删除 ${state.selectedDate} 的训练和身体记录？`)) return;
    markSharedRecordsDeletedByDate(["training_logs", "body_metrics"], state.selectedDate);
    refreshLegacyCachesFromSharedRecords();
    state.editMode = false;
    clearEditorDrafts();
    await saveSnapshot(`已删除 ${state.selectedDate}`);
    render();
  }

  async function restoreSeed() {
    if (!window.confirm("恢复种子记录会替换当前本地数据。继续？")) return;
    state.records = createEmptySharedRecords();
    seedWorkouts().forEach((workout) => upsertSharedEnvelope(
      state.records,
      "training_logs",
      workoutToTrainingLogData(workout, { compatSource: "seed" })
    ));
    refreshLegacyCachesFromSharedRecords();
    state.selectedDate = todayISO();
    state.visibleMonth = state.selectedDate.slice(0, 7);
    state.detailOpen = false;
    state.editMode = false;
    clearEditorDrafts();
    await saveSnapshot("已恢复历史种子记录");
    render();
  }

  function upsertWorkout(workout) {
    const data = workoutToTrainingLogData(workout);
    if (!data) return;
    upsertSharedEnvelope(state.records, "training_logs", data);
    refreshLegacyCachesFromSharedRecords();
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
    openAllEditorSections();
    state.editMode = true;
    render();
  }

  function enableSelectedEditorSection(section) {
    clearEditorDrafts();
    openEditorSection(section);
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
      ...buildSnapshot({ includeLegacy: true }),
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
        state.records = mergeSharedRecords(state.records, snapshot.records);
        refreshLegacyCachesFromSharedRecords();
        const message = "JSON 已导入";
        await saveSnapshot(message);
        await autoPushDirtyRecords(message);
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
        configProfileId: normalizeSyncProfileId(config.configProfileId || ""),
        lastPullAt: config.lastPullAt || null,
        lastPushAt: config.lastPushAt || null,
        lastSyncAt: config.lastSyncAt || null
      };
    } catch (error) {
      return { apiBase: DEFAULT_CLOUD_API_BASE, timerUrl: DEFAULT_TIMER_URL, token: "", timerToken: "", configProfileId: "", lastPullAt: null, lastPushAt: null, lastSyncAt: null };
    }
  }

  function saveSyncConfig(config) {
    const next = {
      ...state.syncConfig,
      ...config,
      apiBase: normalizeSyncApiBase(config.apiBase ?? state.syncConfig.apiBase),
      timerUrl: normalizeTimerUrl(config.timerUrl ?? state.syncConfig.timerUrl),
      configProfileId: normalizeSyncProfileId(config.configProfileId ?? state.syncConfig.configProfileId ?? "")
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

  function normalizeSyncProfileId(value) {
    return String(value || "").trim();
  }

  function assertSyncProfileId(value) {
    const id = normalizeSyncProfileId(value);
    if (!/^[a-zA-Z0-9_-]{6,80}$/.test(id)) {
      throw new Error("配置档案 ID 只能包含英文、数字、下划线和短横线，长度 6-80。");
    }
    return id;
  }

  function generateSyncProfileId() {
    return `shenk_${Date.now().toString(36)}_${Math.random().toString(36).slice(2, 8)}`;
  }

  function encodeBase64Url(value) {
    const bytes = new TextEncoder().encode(value);
    let binary = "";
    bytes.forEach((byte) => {
      binary += String.fromCharCode(byte);
    });
    return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
  }

  function decodeBase64Url(value) {
    const base64 = String(value || "").replace(/-/g, "+").replace(/_/g, "/");
    const padded = base64.padEnd(base64.length + ((4 - base64.length % 4) % 4), "=");
    const binary = atob(padded);
    const bytes = Uint8Array.from(binary, (char) => char.charCodeAt(0));
    return new TextDecoder().decode(bytes);
  }

  function bytesToBase64Url(bytes) {
    let binary = "";
    bytes.forEach((byte) => {
      binary += String.fromCharCode(byte);
    });
    return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
  }

  function base64UrlToBytes(value) {
    const base64 = String(value || "").replace(/-/g, "+").replace(/_/g, "/");
    const padded = base64.padEnd(base64.length + ((4 - base64.length % 4) % 4), "=");
    const binary = atob(padded);
    return Uint8Array.from(binary, (char) => char.charCodeAt(0));
  }

  function ensureWebCrypto() {
    if (!window.crypto?.subtle) {
      throw new Error("当前浏览器不支持本地加密配置档案，请使用 HTTPS 页面或现代浏览器。");
    }
  }

  async function deriveSyncProfileKey(password, salt) {
    ensureWebCrypto();
    const material = await window.crypto.subtle.importKey(
      "raw",
      new TextEncoder().encode(password),
      "PBKDF2",
      false,
      ["deriveKey"]
    );
    return window.crypto.subtle.deriveKey(
      { name: "PBKDF2", salt, iterations: SYNC_PROFILE_KDF_ITERATIONS, hash: "SHA-256" },
      material,
      { name: "AES-GCM", length: 256 },
      false,
      ["encrypt", "decrypt"]
    );
  }

  async function encryptSyncProfilePayload(payload, password) {
    ensureWebCrypto();
    const salt = window.crypto.getRandomValues(new Uint8Array(16));
    const iv = window.crypto.getRandomValues(new Uint8Array(12));
    const key = await deriveSyncProfileKey(password, salt);
    const encoded = new TextEncoder().encode(JSON.stringify(payload));
    const ciphertext = new Uint8Array(await window.crypto.subtle.encrypt({ name: "AES-GCM", iv }, key, encoded));
    return {
      schema: "shenk_sync_profile/v1",
      cipher: "AES-GCM",
      kdf: "PBKDF2-SHA256",
      iterations: SYNC_PROFILE_KDF_ITERATIONS,
      salt: bytesToBase64Url(salt),
      iv: bytesToBase64Url(iv),
      ciphertext: bytesToBase64Url(ciphertext),
      updatedAt: new Date().toISOString()
    };
  }

  async function decryptSyncProfilePayload(profile, password) {
    ensureWebCrypto();
    if (!profile || profile.schema !== "shenk_sync_profile/v1") throw new Error("配置档案格式不正确");
    const salt = base64UrlToBytes(profile.salt);
    const iv = base64UrlToBytes(profile.iv);
    const ciphertext = base64UrlToBytes(profile.ciphertext);
    const key = await deriveSyncProfileKey(password, salt);
    let plaintext = null;
    try {
      plaintext = await window.crypto.subtle.decrypt({ name: "AES-GCM", iv }, key, ciphertext);
    } catch (error) {
      throw new Error("配置密码不正确，或配置档案已损坏。");
    }
    const payload = JSON.parse(new TextDecoder().decode(new Uint8Array(plaintext)));
    return parseSyncConfigPayload(payload);
  }

  function createSyncConfigPackage() {
    const payload = {
      schema: "shenke_config_v1",
      exportedAt: new Date().toISOString(),
      apiBase: state.syncConfig.apiBase || DEFAULT_CLOUD_API_BASE,
      timerUrl: state.syncConfig.timerUrl || DEFAULT_TIMER_URL,
      token: state.syncConfig.token || "",
      timerToken: state.syncConfig.timerToken || ""
    };
    return `${SYNC_CONFIG_PACKAGE_PREFIX}${encodeBase64Url(JSON.stringify(payload))}`;
  }

  function createSyncProfilePackage(profileId) {
    const payload = {
      schema: "shenk_profile_pointer/v1",
      apiBase: state.syncConfig.apiBase || DEFAULT_CLOUD_API_BASE,
      profileId
    };
    return `${SYNC_PROFILE_PACKAGE_PREFIX}${encodeBase64Url(JSON.stringify(payload))}`;
  }

  function createSyncProfilePayload() {
    return {
      schema: "shenke_config_v1",
      exportedAt: new Date().toISOString(),
      apiBase: state.syncConfig.apiBase || DEFAULT_CLOUD_API_BASE,
      timerUrl: state.syncConfig.timerUrl || DEFAULT_TIMER_URL,
      token: state.syncConfig.token || "",
      timerToken: state.syncConfig.timerToken || ""
    };
  }

  function hasCompleteSyncConfig() {
    return Boolean(state.syncConfig?.apiBase && state.syncConfig?.token && state.syncConfig?.timerToken);
  }

  function hasShenkSyncConfig() {
    return Boolean(state.syncConfig?.apiBase && state.syncConfig?.token);
  }

  function parseSyncConfigPackage(rawValue) {
    const raw = String(rawValue || "").trim();
    if (!raw) throw new Error("请先粘贴配置包");
    const body = raw.startsWith(SYNC_CONFIG_PACKAGE_PREFIX) ? raw.slice(SYNC_CONFIG_PACKAGE_PREFIX.length) : raw;
    let payload = null;
    try {
      payload = body.trim().startsWith("{") ? JSON.parse(body) : JSON.parse(decodeBase64Url(body));
    } catch (error) {
      throw new Error("配置包格式不正确");
    }
    if (!payload || typeof payload !== "object") throw new Error("配置包内容无效");
    return parseSyncConfigPayload(payload);
  }

  function parseSyncConfigPayload(payload) {
    const config = {
      apiBase: normalizeSyncApiBase(payload.apiBase || payload.cloudApiBase || ""),
      timerUrl: normalizeTimerUrl(payload.timerUrl || DEFAULT_TIMER_URL),
      token: String(payload.token || payload.shenkToken || ""),
      timerToken: String(payload.timerToken || "")
    };
    if (!config.apiBase || !config.token || !config.timerToken) {
      throw new Error("配置包缺少 API、身刻密钥或计时器密钥");
    }
    return config;
  }

  function parseSyncProfilePackage(rawValue) {
    const raw = String(rawValue || "").trim();
    if (!raw) return null;
    if (!raw.startsWith(SYNC_PROFILE_PACKAGE_PREFIX)) return null;
    const payload = JSON.parse(decodeBase64Url(raw.slice(SYNC_PROFILE_PACKAGE_PREFIX.length)));
    if (!payload || payload.schema !== "shenk_profile_pointer/v1") throw new Error("配置字符串格式不正确");
    return {
      apiBase: normalizeSyncApiBase(payload.apiBase || DEFAULT_CLOUD_API_BASE),
      profileId: assertSyncProfileId(payload.profileId)
    };
  }

  function setSyncPanelMessage(message, isError = false) {
    state.syncStatus.lastResult = isError ? "" : message;
    state.syncStatus.lastError = isError ? message : "";
    state.message = message;
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
      timerToken: String(data.get("timerToken") || ""),
      configProfileId: String(data.get("configProfileId") || "")
    });
    const message = "云数据库配置已保存";
    state.syncStatus.lastResult = message;
    state.syncStatus.lastError = "";
    if (hasShenkSyncConfig()) {
      await autoPushDirtyRecords(message);
    }
    render();
  }

  function readSyncProfileFields(options = {}) {
    const idInput = app.querySelector("[data-sync-profile-id]");
    const passwordInput = app.querySelector("[data-sync-profile-password]");
    let rawId = String(idInput?.value || state.syncConfig.configProfileId || "").trim();
    const pointer = parseSyncProfilePackage(rawId);
    if (pointer) {
      rawId = pointer.profileId;
      saveSyncConfig({ apiBase: pointer.apiBase, configProfileId: pointer.profileId });
      if (idInput) idInput.value = pointer.profileId;
    }
    if (!rawId && options.generateId) {
      rawId = generateSyncProfileId();
      if (idInput) idInput.value = rawId;
    }
    const profileId = assertSyncProfileId(rawId);
    const password = String(passwordInput?.value || "");
    if (options.requirePassword !== false && password.length < 8) {
      throw new Error("配置密码至少 8 位。");
    }
    return { profileId, password };
  }

  async function saveEncryptedSyncProfile() {
    try {
      if (!hasCompleteSyncConfig()) {
        throw new Error("请先保存 API 地址、身刻访问密钥和计时器访问密钥。");
      }
      const { profileId, password } = readSyncProfileFields({ generateId: true });
      const profile = await encryptSyncProfilePayload(createSyncProfilePayload(), password);
      await syncRequest(`/sync-profiles/${encodeURIComponent(profileId)}`, {
        method: "PUT",
        body: { profile }
      });
      saveSyncConfig({ configProfileId: profileId });
      setSyncPanelMessage("加密配置档案已保存到云端。新设备可用配置字符串和密码读取。");
    } catch (error) {
      setSyncPanelMessage(error.message || "加密配置档案保存失败", true);
    }
    render();
  }

  async function loadEncryptedSyncProfile() {
    try {
      const { profileId, password } = readSyncProfileFields();
      const result = await syncRequest(`/sync-profiles/${encodeURIComponent(profileId)}`, {
        method: "GET",
        auth: false
      });
      const config = await decryptSyncProfilePayload(result.profile, password);
      saveSyncConfig({
        ...config,
        configProfileId: profileId,
        lastPullAt: null,
        lastPushAt: null,
        lastSyncAt: null
      });
      setSyncPanelMessage("加密配置档案已读取。现在可以进行云端同步。");
    } catch (error) {
      setSyncPanelMessage(error.message || "加密配置档案读取失败", true);
    }
    render();
  }

  async function copySyncProfilePackage() {
    try {
      const { profileId } = readSyncProfileFields({ requirePassword: false });
      const packageText = createSyncProfilePackage(profileId);
      try {
        await navigator.clipboard.writeText(packageText);
        setSyncPanelMessage("配置字符串已复制。新设备粘贴配置字符串并输入密码即可读取加密档案。");
      } catch (error) {
        window.prompt("复制这段配置字符串到新设备：", packageText);
        setSyncPanelMessage("浏览器没有允许自动复制，已弹出配置字符串。");
      }
    } catch (error) {
      setSyncPanelMessage(error.message || "配置字符串生成失败", true);
    }
    render();
  }

  async function copySyncConfigPackage() {
    if (!hasCompleteSyncConfig()) {
      setSyncPanelMessage("请先保存 API 地址、身刻访问密钥和计时器访问密钥，再复制配置包。", true);
      render();
      return;
    }
    const packageText = createSyncConfigPackage();
    try {
      await navigator.clipboard.writeText(packageText);
      setSyncPanelMessage("配置包已复制。到新设备的身刻设置页点击“从剪贴板导入”即可。");
    } catch (error) {
      window.prompt("复制这段配置包到新设备：", packageText);
      setSyncPanelMessage("浏览器没有允许自动复制，已弹出配置包。");
    }
    render();
  }

  async function pasteSyncConfigPackage() {
    try {
      const text = await navigator.clipboard.readText();
      applySyncConfigPackage(text);
    } catch (error) {
      setSyncPanelMessage("浏览器没有允许读取剪贴板，请手动粘贴配置包后点击导入。", true);
      render();
    }
  }

  function importSyncConfigPackage() {
    const input = app.querySelector("#sync-config-package");
    applySyncConfigPackage(input?.value || "");
  }

  function applySyncConfigPackage(rawValue) {
    try {
      const config = parseSyncConfigPackage(rawValue);
      saveSyncConfig(config);
      setSyncPanelMessage("配置包已导入，新设备可以直接使用云数据库和计时器。");
    } catch (error) {
      setSyncPanelMessage(error.message || "配置包导入失败", true);
    }
    render();
  }

  function getTimerConfigPayload() {
    return {
      apiBase: state.syncConfig.apiBase || DEFAULT_CLOUD_API_BASE,
      cloudApiBase: state.syncConfig.apiBase || DEFAULT_CLOUD_API_BASE,
      timerToken: state.syncConfig.timerToken || "",
      token: state.syncConfig.timerToken || "",
      hostOrigin: window.location.origin === "null" ? "" : window.location.origin
    };
  }

  function getFrameTargetOrigin(frame) {
    try {
      const origin = new URL(frame.getAttribute("src") || frame.src, window.location.href).origin;
      return origin === "null" ? "*" : origin;
    } catch (error) {
      return window.location.origin;
    }
  }

  function sendTimerConfigToFrame(frame) {
    if (!frame?.contentWindow || !state.syncConfig?.timerToken) return;
    frame.contentWindow.postMessage({
      type: "shenke.timer.config",
      payload: getTimerConfigPayload()
    }, getFrameTargetOrigin(frame));
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

  async function autoPushDirtyRecords(localMessage) {
    if (!hasShenkSyncConfig()) {
      state.syncStatus.lastError = "云同步未配置，已仅保存到本地";
      state.syncStatus.lastResult = "";
      state.message = `${localMessage}（仅本地）`;
      return;
    }
    if (state.syncStatus.busy) {
      state.message = `${localMessage}，云端稍后同步`;
      if (!autoPushRetryTimer) {
        autoPushRetryTimer = window.setTimeout(async () => {
          autoPushRetryTimer = null;
          await autoPushDirtyRecords(localMessage);
          render();
        }, 1200);
      }
      return;
    }

    await pushDirtyRecords({ silent: true });
    if (state.syncStatus.lastError) {
      state.message = `${localMessage}，云端同步失败`;
      return;
    }
    state.message = `${localMessage}，已同步云端`;
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
      refreshLegacyCachesFromSharedRecords();
    }
    const now = result.serverTime || new Date().toISOString();
    saveSyncConfig({ lastPullAt: now, lastSyncAt: now });
    const pendingTimers = countPendingTimerSessions();
    state.syncStatus.lastResult = `已读取 ${records.length} 条云端记录${pendingTimers ? `，${pendingTimers} 条计时器记录待确认` : ""}`;
    state.syncStatus.lastError = "";
    await saveSnapshot();
  }

  async function doPushDirtyRecords() {
    state.records = normalizeSharedRecords(state.records);
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
    refreshLegacyCachesFromSharedRecords();
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
    refreshLegacyCachesFromSharedRecords();
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

  function refreshLegacyCachesFromSharedRecords() {
    state.workouts = deriveLegacyWorkoutsFromSharedRecords(state.records);
    state.bodyMetrics = deriveLegacyMetricsFromSharedRecords(state.records);
  }

  function deriveLegacyWorkoutsFromSharedRecords(records) {
    const source = normalizeSharedRecords(records);
    return normalizeWorkouts(source.training_logs.map(trainingLogEnvelopeToWorkout).filter(Boolean));
  }

  function deriveLegacyMetricsFromSharedRecords(records) {
    const source = normalizeSharedRecords(records);
    return normalizeBodyMetrics(source.body_metrics.map(bodyMetricEnvelopeToLegacy).filter(Boolean));
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
    if (!window.ShenkeRecommendationEngine) {
      return {
        type: "easyWalk",
        label: "基础建议",
        title: "普通走",
        minutes: 35,
        reasons: ["建议模块暂未载入，先保持轻松活动。"],
        sourceLabel: "本地规则"
      };
    }
    return window.ShenkeRecommendationEngine.getRecommendation(date, sourceWorkouts, state.bodyMetrics);
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
    if (TIMER_TYPE_META[value]) {
      return {
        ...TIMER_TYPE_META[value],
        label: TIMER_TYPE_DISPLAY_LABELS[value] || TIMER_TYPE_META[value].label
      };
    }
    const legacyType = toLegacyTrainingType(value);
    return {
      label: TYPE_META[legacyType]?.label || "其他",
      legacyType,
      defaultRole: "note",
      canConvert: false
    };
  }

  function getTimerSessionTitle(session) {
    const title = cleanUserRoutineName(session.title || session.routineTitle || session.routineName);
    const variant = cleanUserRoutineName(session.routineVariant || session.routine_variant);
    if (title && variant) return `${title} · ${variant}`;
    if (title) return title;
    const routine = getRoutineDisplayMeta(session);
    if (routine?.label) return routine.label;
    return getTimerTypeMeta(session.trainingType || session.type).label || "计时器记录";
  }

  function getRoutineDisplayMeta(source) {
    const routineId = typeof source === "string" ? source : source?.routineId || source?.routine_id || "";
    if (ROUTINE_DISPLAY_META[routineId]) return ROUTINE_DISPLAY_META[routineId];
    const title = typeof source === "string" ? "" : source?.routineTitle || source?.routine_title || source?.routineName || source?.title || "";
    if (ROUTINE_DISPLAY_META[title]) return ROUTINE_DISPLAY_META[title];
    return null;
  }

  function getRoutineVariantLabel(source) {
    const explicit = cleanUserRoutineName(typeof source === "string" ? "" : source?.routineVariant || source?.routine_variant);
    if (explicit) return explicit;
    const routine = getRoutineDisplayMeta(source);
    if (routine?.variant) return routine.variant;
    const routineId = typeof source === "string" ? source : source?.routineId || source?.routine_id || "";
    if (routineId.includes("_quick_")) return "简版";
    if (routineId.includes("_full_")) return "完整版";
    return "";
  }

  function getPlanItemDisplayTitle(data) {
    const routine = getRoutineDisplayMeta(data);
    if (routine?.label) return routine.label;
    const title = cleanUserRoutineName(data.title || data.name);
    if (title) return title;
    if (data.trainingType || data.type) return getTimerTypeMeta(data.trainingType || data.type).label;
    return "";
  }

  function cleanUserRoutineName(value) {
    const text = String(value || "").trim();
    if (!text) return "";
    if (ROUTINE_DISPLAY_META[text]) return ROUTINE_DISPLAY_META[text].label;
    const cleaned = text
      .replace(/\s*[（(]?\s*(?:v|版本)\s*\d+(?:[._]\d+)*\s*[)）]?/gi, "")
      .replace(/\s+/g, " ")
      .trim();
    return isInternalIdentifierText(cleaned) ? "" : cleaned;
  }

  function isInternalIdentifierText(value) {
    const text = String(value || "").trim();
    if (!text) return false;
    return /^routine_/i.test(text) || /^[a-z]+(?:_[a-z0-9]+)+$/i.test(text) || /\bv\d+(?:[._]\d+)*\b/i.test(text);
  }

  function formatPublicNotes(value) {
    const raw = Array.isArray(value) ? value.join("；") : String(value || "");
    return raw
      .split(/[；;\n]/)
      .map((item) => item.trim())
      .filter((item) => item && !/routine(?:Id|Version)?\s*[:：]/i.test(item))
      .filter((item) => item && !/completion\s*[:：]/i.test(item))
      .filter((item) => item && !isInternalIdentifierText(item))
      .join("；");
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

  function canDraftTimerSessionTraining(session) {
    return canConvertTimerSession(session);
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
    const value = String(type || "").trim();
    const alias = TRAINING_TYPE_ALIASES[value] || TRAINING_TYPE_ALIASES[value.replace(/-/g, "_")];
    if (alias) return alias;
    if (LEGACY_TO_SHARED_TYPE[value]) return LEGACY_TO_SHARED_TYPE[value];
    if (SHARED_TO_LEGACY_TYPE[value]) return value;
    return "easy_walk";
  }

  function normalizeTimerTrainingType(type) {
    const value = String(type || "").trim();
    if (!value) return "easy_walk";
    const alias = TRAINING_TYPE_ALIASES[value] || TRAINING_TYPE_ALIASES[value.replace(/-/g, "_")];
    if (alias) return alias;
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
    const value = String(type || "").trim();
    if (TYPE_META[value]) return value;
    const alias = TRAINING_TYPE_ALIASES[value] || TRAINING_TYPE_ALIASES[value.replace(/-/g, "_")];
    return SHARED_TO_LEGACY_TYPE[alias || value] || "easyWalk";
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
