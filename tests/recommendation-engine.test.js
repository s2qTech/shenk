"use strict";

const assert = require("node:assert/strict");
const engine = require("../src/recommendation-engine.js");

function workout(date, type, overrides = {}) {
  return {
    date,
    type,
    status: "completed",
    source: "manual",
    fatigue: "normal",
    pain: { calf: 0, back: 0, wrist: 0, outerThigh: 0 },
    ...overrides
  };
}

function metric(date, overrides = {}) {
  return {
    date,
    fatigue: "normal",
    sleepQuality: "normal",
    energy: 3,
    pain: { calf: 0, back: 0, wrist: 0, outerThigh: 0 },
    ...overrides
  };
}

function forecast(startDate, days, workouts, metrics = []) {
  const virtual = workouts.map((item) => ({ ...item }));
  const result = [];
  const [year, month, day] = startDate.split("-").map(Number);
  const cursor = new Date(year, month - 1, day);
  for (let index = 0; index < days; index += 1) {
    const date = `${cursor.getFullYear()}-${String(cursor.getMonth() + 1).padStart(2, "0")}-${String(cursor.getDate()).padStart(2, "0")}`;
    const recommendation = engine.getRecommendation(date, virtual, metrics);
    result.push(recommendation);
    virtual.push(workout(date, recommendation.type, { source: "forecast" }));
    cursor.setDate(cursor.getDate() + 1);
  }
  return result;
}

const recentHistory = [
  workout("2026-06-18", "recovery"),
  workout("2026-06-19", "qualityWalk"),
  workout("2026-06-20", "qualityWalk"),
  workout("2026-06-21", "easyWalk"),
  workout("2026-06-22", "strength"),
  workout("2026-06-23", "recovery"),
  workout("2026-06-24", "easyWalk")
];

{
  const result = forecast("2026-06-25", 6, recentHistory);
  assert.equal(result[0].type, "strength", "normal history should continue with strength");
  assert.notEqual(result[1].type, "recovery", "future should not collapse into recovery");
  assert.ok(new Set(result.map((item) => item.type)).size >= 3, "forecast should preserve a varied cycle");
}

{
  const tired = metric("2026-06-24", {
    fatigue: "severe",
    sleepQuality: "poor",
    energy: 1
  });
  const result = forecast("2026-06-25", 4, recentHistory, [tired]);
  assert.equal(result[0].type, "recovery", "severe recent fatigue should downgrade the next day");
  assert.notEqual(result[1].type, "recovery", "one stale status must not lock all future dates");
}

{
  const calfPain = metric("2026-06-24", {
    pain: { calf: 1, back: 0, wrist: 0, outerThigh: 0 }
  });
  const result = engine.getRecommendation("2026-06-25", recentHistory, [calfPain]);
  assert.ok(["easyWalk", "recovery"].includes(result.type), "mild calf pain should avoid hard training");
}

{
  const hardYesterday = recentHistory.concat(workout("2026-06-25", "strength"));
  const result = engine.getRecommendation("2026-06-26", hardYesterday, []);
  assert.equal(result.type, "easyWalk", "a hard day should be followed by easy aerobic work");
}

{
  const predictedStrength = forecast("2026-06-25", 1, recentHistory)[0];
  assert.equal(predictedStrength.type, "strength");
  const actualDeviation = recentHistory.concat(workout("2026-06-25", "easyWalk"));
  const recalculated = engine.getRecommendation("2026-06-26", actualDeviation, []);
  assert.equal(recalculated.type, "strength", "an actual deviation should recalculate the next recommendation");
}

console.log("recommendation-engine tests passed");
