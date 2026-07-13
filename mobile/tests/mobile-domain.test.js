import test from "node:test";
import assert from "node:assert/strict";
import { buildTimerUrl, getEffectiveGuide, getFormalLogs, typeMeta } from "../src/domain/mobile-domain.js";

const record = (entity, id, data, updatedAt = "2026-07-13T08:00:00Z") => ({ entity, id, data, updatedAt });

test("移动端当天指导遵守正式记录、调整、日计划、兜底的优先级", () => {
  const records = [
    record("daily_plan_items", "plan", { date: "2026-07-13", title: "普通走", status: "planned" }),
    record("plan_adjustments", "adjust", { date: "2026-07-13", title: "恢复", status: "planned" }, "2026-07-13T09:00:00Z"),
    record("training_logs", "actual", { date: "2026-07-13", title: "力量", status: "completed" }, "2026-07-13T10:00:00Z")
  ];
  assert.equal(getEffectiveGuide(records, "2026-07-13").source, "actual");
  assert.equal(getEffectiveGuide(records.filter((item) => item.entity !== "training_logs"), "2026-07-13").source, "adjustment");
  assert.equal(getEffectiveGuide(records.filter((item) => item.entity === "daily_plan_items"), "2026-07-13").source, "plan");
});

test("timer session 不是正式记录", () => {
  const records = [record("timer_sessions", "timer", { date: "2026-07-13", title: "力量训练" })];
  assert.equal(getFormalLogs(records, "2026-07-13").length, 0);
});

test("打开计时器的 URL 不携带 token", () => {
  const url = buildTimerUrl("https://timer.example/", { routineId: "routine_a", date: "2026-07-13", source: "shenk", token: "forbidden" });
  assert.match(url, /routineId=routine_a/);
  assert.doesNotMatch(url, /forbidden|token=/);
  assert.equal(typeMeta("easy_walk").label, "普通走");
});
