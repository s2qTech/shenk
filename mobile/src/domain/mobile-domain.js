export const CONTRACT_VERSION = "1.0";

export const TYPE_META = Object.freeze({
  strength: { label: "力量", icon: "力量", tone: "strength" },
  easy_walk: { label: "普通走", icon: "步行", tone: "walk" },
  quality_walk: { label: "提高走", icon: "快走", tone: "quality" },
  indoor_cardio: { label: "室内有氧", icon: "有氧", tone: "cardio" },
  recovery: { label: "恢复", icon: "拉伸", tone: "recovery" },
  warmup: { label: "热身", icon: "热身", tone: "warmup" },
  stretch: { label: "拉伸", icon: "拉伸", tone: "recovery" },
  travel_strength: { label: "力量", icon: "力量", tone: "strength" },
  seat_recovery: { label: "活动", icon: "活动", tone: "recovery" },
  rest: { label: "休息", icon: "休息", tone: "rest" }
});

const ACTIVE = new Set(["planned", "confirmed", "active", "adjusted"]);

function payload(record) {
  return record?.data || record || {};
}

function timestamp(record) {
  return Date.parse(record?.updatedAt || payload(record).updatedAt || record?.createdAt || payload(record).createdAt || 0) || 0;
}

function isCurrent(record) {
  return Boolean(record) && !record.deletedAt && !payload(record).deletedAt;
}

function byNewest(a, b) {
  return timestamp(b) - timestamp(a);
}

export function typeMeta(type) {
  return TYPE_META[type] || { label: "训练", icon: "训练", tone: "default" };
}

export function displayTitle(item) {
  const value = payload(item);
  return value.title || value.routineTitle || typeMeta(value.trainingType || value.type).label;
}

export function getFormalLogs(records, date) {
  return records
    .filter((record) => isCurrent(record) && record.entity === "training_logs" && payload(record).date === date)
    .sort(byNewest);
}

export function getEffectiveGuide(records, date, fallbackGuide = null) {
  const actual = getFormalLogs(records, date)[0];
  if (actual) return { source: "actual", item: actual };

  const adjustment = records
    .filter((record) => isCurrent(record) && record.entity === "plan_adjustments" && payload(record).date === date && ACTIVE.has(payload(record).status || "planned"))
    .sort(byNewest)[0];
  if (adjustment) return { source: "adjustment", item: adjustment };

  const plan = records
    .filter((record) => isCurrent(record) && record.entity === "daily_plan_items" && payload(record).date === date && ACTIVE.has(payload(record).status || "planned"))
    .sort(byNewest)[0];
  if (plan) return { source: "plan", item: plan };

  return fallbackGuide ? { source: "fallback", item: fallbackGuide } : null;
}

export function getRecentLogs(records, limit = 8) {
  return records
    .filter((record) => isCurrent(record) && record.entity === "training_logs")
    .sort((a, b) => String(payload(b).date || "").localeCompare(String(payload(a).date || "")) || byNewest(a, b))
    .slice(0, limit);
}

export function getLatestBodyMetrics(records) {
  return records
    .filter((record) => isCurrent(record) && record.entity === "body_metrics")
    .sort((a, b) => String(payload(b).date || "").localeCompare(String(payload(a).date || "")) || byNewest(a, b))[0] || null;
}

export function getMetricSeries(records, field, limit = 14) {
  return records
    .filter((record) => isCurrent(record) && record.entity === "body_metrics" && Number.isFinite(Number(payload(record)[field])))
    .sort((a, b) => String(payload(a).date || "").localeCompare(String(payload(b).date || "")))
    .slice(-limit)
    .map((record) => ({ date: payload(record).date, value: Number(payload(record)[field]) }));
}

export function getAvailableRoutines(records) {
  return records
    .filter((record) => isCurrent(record) && record.entity === "routine_templates")
    .filter((record) => payload(record).lifecycle !== "archived" && payload(record).enabled !== false)
    .sort((a, b) => displayTitle(a).localeCompare(displayTitle(b), "zh-CN"));
}

export function getVisibleTimerSessions(records, date) {
  return records
    .filter((record) => isCurrent(record) && record.entity === "timer_sessions" && payload(record).date === date)
    .sort((a, b) => String(payload(a).startedAt || "").localeCompare(String(payload(b).startedAt || "")));
}

export function buildTimerUrl(timerUrl, parameters = {}) {
  if (!timerUrl) return null;
  const url = new URL(timerUrl, "https://placeholder.invalid");
  ["routineId", "preset", "date", "dailyPlanItemId", "planTemplateId", "source"].forEach((key) => {
    if (parameters[key]) url.searchParams.set(key, parameters[key]);
  });
  return url.href.replace("https://placeholder.invalid", "");
}

export function buildTodayModel(records, date, fallbackGuide = null) {
  const guide = getEffectiveGuide(records, date, fallbackGuide);
  return {
    date,
    guide,
    formalLogs: getFormalLogs(records, date),
    timerSessions: getVisibleTimerSessions(records, date),
    latestMetrics: getLatestBodyMetrics(records),
    availableRoutines: getAvailableRoutines(records)
  };
}
