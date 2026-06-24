(function attachRecommendationEngine(root, factory) {
  const api = factory();
  if (typeof module === "object" && module.exports) module.exports = api;
  if (root) root.ShenkeRecommendationEngine = api;
})(typeof globalThis !== "undefined" ? globalThis : this, function createRecommendationEngine() {
  "use strict";

  const AEROBIC_TYPES = new Set(["easyWalk", "qualityWalk", "indoorCardio"]);
  const HARD_TYPES = new Set(["strength", "qualityWalk"]);
  const RECOVERY_TYPES = new Set(["recovery", "rest"]);
  const TYPE_LABELS = {
    strength: "力量",
    easyWalk: "普通走",
    qualityWalk: "提高走",
    indoorCardio: "室内有氧",
    recovery: "恢复",
    rest: "休息"
  };
  const FATIGUE_RANK = {
    low: 0,
    normal: 1,
    high: 2,
    severe: 3
  };
  const FATIGUE_LABELS = {
    low: "轻松",
    normal: "正常",
    high: "累",
    severe: "很累"
  };

  function getRecommendation(date, sourceWorkouts, bodyMetrics) {
    const workouts = Array.isArray(sourceWorkouts) ? sourceWorkouts : [];
    const metrics = Array.isArray(bodyMetrics) ? bodyMetrics : [];
    const completed = workouts
      .filter((item) => item && item.date < date && isCompletedRecord(item))
      .sort((left, right) => left.date.localeCompare(right.date));
    const last7 = completed.filter((item) => isWithinPreviousDays(item.date, date, 7));
    const strengthCount = countType(last7, "strength");
    const aerobicCount = last7.filter((item) => AEROBIC_TYPES.has(item.type)).length;
    const qualityCount = countType(last7, "qualityWalk");
    const recoveryCount = last7.filter((item) => RECOVERY_TYPES.has(item.type)).length;
    const activeCount = last7.filter((item) => !RECOVERY_TYPES.has(item.type)).length;
    const previousDay = getRecordOnDate(completed, addDays(date, -1));
    const daysSinceStrength = daysSinceLastType(completed, date, new Set(["strength"]));
    const daysSinceHard = daysSinceLastType(completed, date, HARD_TYPES);
    const activeStreak = getActiveStreak(completed, date);
    const risk = getRecentRisk(date, workouts, metrics);
    const countReason = `近 7 天力量 ${strengthCount} 次，有氧 ${aerobicCount} 次，提高走 ${qualityCount} 次。`;

    if (!completed.length) {
      return recommendation("easyWalk", "建立基础", "普通走", 35, [
        "还没有足够的近期记录，先用轻松有氧建立基线。",
        "保持能够完整说话的强度，不追求配速。",
        "完成后记录体感，后续建议会自动重排。"
      ], risk);
    }

    if (risk.level === "high") {
      return recommendation("recovery", "状态降级", "恢复活动", 15, [
        risk.reason,
        "今天保留低压力活动，不安排力量或提高走。",
        "后续正常状态或一次恢复记录会解除持续降级。"
      ], risk);
    }

    if (risk.level === "caution") {
      const type = risk.preferRecovery || activeStreak >= 3 ? "recovery" : "easyWalk";
      const title = type === "recovery" ? "恢复活动" : "轻松普通走";
      const minutes = type === "recovery" ? 15 : 30;
      return recommendation(type, "谨慎安排", title, minutes, [
        risk.reason,
        type === "recovery" ? "今天以活动开身体为主，不追求训练量。" : "只走轻松强度，出现不适立即结束。",
        "风险状态只影响短期，不会锁住后续整段预测。"
      ], risk);
    }

    if (previousDay && HARD_TYPES.has(previousDay.type)) {
      return recommendation("easyWalk", "承接硬日", "普通走", 45, [
        `昨天是${TYPE_LABELS[previousDay.type]}，今天不连续安排偏硬训练。`,
        countReason,
        "室外条件不合适时可等量改为室内有氧。"
      ], risk);
    }

    if (activeStreak >= 4 || (recoveryCount < 1 && activeCount >= 5)) {
      return recommendation("recovery", "主动恢复", "恢复活动", 15, [
        `已连续 ${activeStreak} 天安排活动，需要主动降低一天负荷。`,
        countReason,
        "恢复日用于维持活动和消除累积疲劳，不是补课日。"
      ], risk);
    }

    if (strengthCount < 2 && daysSinceStrength >= 2 && !risk.backOrWrist) {
      return recommendation("strength", "补足力量", "力量训练", 47, [
        `近 7 天力量 ${strengthCount} 次，目标约 2 次。`,
        daysSinceStrength === Infinity ? "近期没有力量记录。" : `距上次力量已 ${daysSinceStrength} 天。`,
        "按标准动作完成，不用依靠心率或出汗判断效果。"
      ], risk);
    }

    if (aerobicCount < 3) {
      return recommendation("easyWalk", "补足有氧", "普通走", 50, [
        `近 7 天有氧 ${aerobicCount} 次，目标约 3 次。`,
        "保持稳定、可交谈强度，不需要冲刺。",
        "天气不合适时可改为室内有氧。"
      ], risk);
    }

    if (qualityCount < 1 && aerobicCount >= 2 && daysSinceHard >= 2 && !risk.calf) {
      return recommendation("qualityWalk", "可控提高", "提高走", 50, [
        "近 7 天基础有氧已满足，但还没有提高走。",
        "提高段保持可控，不做全力冲刺。",
        "小腿发紧、步态改变或呼吸失控时立即降级。"
      ], risk);
    }

    if (strengthCount < 2 && daysSinceStrength >= 2 && !risk.backOrWrist) {
      return recommendation("strength", "维持力量", "力量训练", 47, [
        countReason,
        "力量间隔已经满足，按标准版完成。",
        "动作质量下降时使用短版，不追加补课。"
      ], risk);
    }

    if (recoveryCount < 1) {
      return recommendation("recovery", "补充恢复", "恢复活动", 15, [
        countReason,
        "近 7 天还没有恢复或休息记录。",
        "今天降低负荷，为下一轮力量或提高走留出恢复空间。"
      ], risk);
    }

    return recommendation("easyWalk", "维持节奏", "普通走", 45, [
      countReason,
      "当前训练构成已经较完整，继续安排轻松有氧。",
      "实际完成内容变化后，后续预测会从当天起重新计算。"
    ], risk);
  }

  function getRecentRisk(date, sourceWorkouts, bodyMetrics) {
    const workouts = Array.isArray(sourceWorkouts) ? sourceWorkouts : [];
    const metrics = Array.isArray(bodyMetrics) ? bodyMetrics : [];
    const actualWorkouts = workouts.filter((item) => item && item.source !== "forecast" && item.date <= date);
    const candidates = actualWorkouts.concat(metrics.filter((item) => item && item.date <= date));
    if (!candidates.length) return normalRisk();

    const latestDate = candidates.reduce((latest, item) => item.date > latest ? item.date : latest, "");
    const ageDays = daysBetween(latestDate, date);
    if (ageDays > 2) return normalRisk();

    const recoveryAfterSignal = workouts.some((item) => {
      return item && item.date > latestDate && item.date < date && RECOVERY_TYPES.has(item.type) && isCompletedRecord(item);
    });
    if (recoveryAfterSignal) return normalRisk();

    const latestSignals = candidates.filter((item) => item.date === latestDate);
    const snapshot = latestSignals.reduce((result, item) => {
      const pain = item.pain || {};
      result.fatigueRank = Math.max(result.fatigueRank, FATIGUE_RANK[item.fatigue] ?? FATIGUE_RANK.normal);
      result.sleepPoor = result.sleepPoor || item.sleepQuality === "poor";
      result.energy = Math.min(result.energy, numberOr(item.energy, 3));
      result.pain.calf = Math.max(result.pain.calf, painLevel(pain.calf));
      result.pain.back = Math.max(result.pain.back, painLevel(pain.back));
      result.pain.wrist = Math.max(result.pain.wrist, painLevel(pain.wrist));
      result.pain.outerThigh = Math.max(result.pain.outerThigh, painLevel(pain.outerThigh));
      return result;
    }, {
      fatigueRank: FATIGUE_RANK.normal,
      sleepPoor: false,
      energy: 5,
      pain: { calf: 0, back: 0, wrist: 0, outerThigh: 0 }
    });

    const maxPain = Math.max(...Object.values(snapshot.pain));
    const calf = snapshot.pain.calf > 0;
    const backOrWrist = snapshot.pain.back > 0 || snapshot.pain.wrist > 0;
    const severeFatigue = snapshot.fatigueRank >= FATIGUE_RANK.severe;
    const highFatigue = snapshot.fatigueRank >= FATIGUE_RANK.high;
    const lowReadiness = snapshot.sleepPoor || snapshot.energy <= 2;
    const sourceLabel = latestDate === date ? "今天记录的状态" : "上一条状态";

    if ((ageDays <= 1 && (severeFatigue || maxPain >= 2)) || (ageDays === 0 && highFatigue && lowReadiness)) {
      return {
        level: "high",
        calf,
        backOrWrist,
        preferRecovery: true,
        reason: `${sourceLabel}${describeRisk(snapshot)}，先安排恢复。`,
        sourceDate: latestDate,
        ageDays
      };
    }

    if ((ageDays <= 1 && (highFatigue || lowReadiness || maxPain === 1)) || (ageDays === 2 && (severeFatigue || maxPain >= 2))) {
      return {
        level: "caution",
        calf,
        backOrWrist,
        preferRecovery: highFatigue || lowReadiness || maxPain >= 2,
        reason: `${sourceLabel}${describeRisk(snapshot)}，今天降低强度。`,
        sourceDate: latestDate,
        ageDays
      };
    }

    return normalRisk();
  }

  function recommendation(type, label, title, minutes, reasons, risk) {
    return {
      type,
      label,
      title,
      minutes,
      reasons,
      sourceLabel: "本地规则",
      riskLevel: risk.level
    };
  }

  function normalRisk() {
    return {
      level: "normal",
      calf: false,
      backOrWrist: false,
      preferRecovery: false,
      reason: "",
      sourceDate: "",
      ageDays: Infinity
    };
  }

  function describeRisk(snapshot) {
    const details = [];
    if (snapshot.fatigueRank >= FATIGUE_RANK.high) {
      details.push(`疲劳为${FATIGUE_LABELS[rankToFatigue(snapshot.fatigueRank)]}`);
    }
    if (snapshot.sleepPoor) details.push("睡眠较差");
    if (snapshot.energy <= 2) details.push(`精力 ${snapshot.energy}/5`);
    const painLabels = {
      calf: "小腿",
      back: "腰背",
      wrist: "手腕",
      outerThigh: "大腿外侧"
    };
    Object.entries(painLabels).forEach(([key, label]) => {
      if (snapshot.pain[key] > 0) details.push(`${label}不适 ${snapshot.pain[key]} 级`);
    });
    return details.length ? `显示${details.join("、")}` : "需要谨慎";
  }

  function rankToFatigue(rank) {
    if (rank >= FATIGUE_RANK.severe) return "severe";
    if (rank >= FATIGUE_RANK.high) return "high";
    if (rank <= FATIGUE_RANK.low) return "low";
    return "normal";
  }

  function countType(records, type) {
    return records.filter((item) => item.type === type).length;
  }

  function getRecordOnDate(records, date) {
    return [...records].reverse().find((item) => item.date === date) || null;
  }

  function daysSinceLastType(records, date, types) {
    const last = [...records].reverse().find((item) => types.has(item.type));
    return last ? daysBetween(last.date, date) : Infinity;
  }

  function getActiveStreak(records, date) {
    const recordDates = new Map();
    records.forEach((item) => {
      const existing = recordDates.get(item.date);
      if (!existing || RECOVERY_TYPES.has(existing.type)) recordDates.set(item.date, item);
    });
    let streak = 0;
    let cursor = addDays(date, -1);
    while (recordDates.has(cursor)) {
      const item = recordDates.get(cursor);
      if (RECOVERY_TYPES.has(item.type)) break;
      streak += 1;
      cursor = addDays(cursor, -1);
    }
    return streak;
  }

  function isWithinPreviousDays(itemDate, targetDate, days) {
    const distance = daysBetween(itemDate, targetDate);
    return distance >= 1 && distance <= days;
  }

  function isCompletedRecord(record) {
    return record.type === "rest" || ["completed", "short", "stretchOnly"].includes(record.status);
  }

  function painLevel(value) {
    const number = Number(value);
    return Number.isFinite(number) ? Math.max(0, Math.round(number)) : 0;
  }

  function numberOr(value, fallback) {
    const number = Number(value);
    return Number.isFinite(number) ? number : fallback;
  }

  function daysBetween(fromDate, toDate) {
    const dayMs = 24 * 60 * 60 * 1000;
    return Math.round((parseIsoDate(toDate) - parseIsoDate(fromDate)) / dayMs);
  }

  function addDays(date, amount) {
    const next = parseIsoDate(date);
    next.setDate(next.getDate() + amount);
    return dateToISO(next);
  }

  function parseIsoDate(value) {
    const [year, month, day] = String(value).split("-").map(Number);
    return new Date(year, month - 1, day);
  }

  function dateToISO(date) {
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
  }

  function pad(value) {
    return String(value).padStart(2, "0");
  }

  return {
    getRecommendation,
    getRecentRisk
  };
});
