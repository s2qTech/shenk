import { Capacitor } from "@capacitor/core";
import { SecureStorage } from "@aparajita/capacitor-secure-storage";
import {
  buildTodayModel,
  displayTitle,
  getMetricSeries,
  getRecentLogs,
  typeMeta
} from "./domain/mobile-domain.js";
import { createSecureConfigStore } from "./platform/secure-config-store.js";
import { createMobileRepository } from "./platform/mobile-repository.js";
import { createCloudSync } from "./platform/cloud-sync.js";
import { createRuntimeAdapters } from "./platform/runtime-adapters.js";
import "./styles.css";

const today = new Date().toISOString().slice(0, 10);
const DEVICE_ID_KEY = "shenk-mobile-device-id-v1";
function getDeviceId() {
  const existing = globalThis.localStorage?.getItem(DEVICE_ID_KEY);
  if (existing) return existing;
  const next = crypto.randomUUID ? crypto.randomUUID() : `mobile-${Date.now()}`;
  globalThis.localStorage?.setItem(DEVICE_ID_KEY, next);
  return next;
}
const deviceId = getDeviceId();
const nativeSecureStore = Capacitor.isNativePlatform() ? {
  get: async ({ key }) => ({ value: await SecureStorage.getItem(key) }),
  set: async ({ key, value }) => SecureStorage.setItem(key, value),
  remove: async ({ key }) => SecureStorage.removeItem(key)
} : null;
const repository = createMobileRepository();
const secureConfig = createSecureConfigStore({ secureStore: nativeSecureStore });
const cloudSync = createCloudSync({ repository, deviceId });
const runtime = createRuntimeAdapters();

let state = { view: "today", records: [], config: {}, message: "正在准备离线数据…" };

function escape(value) {
  return String(value ?? "").replace(/[&<>"]/g, (character) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", "\"": "&quot;" })[character]);
}

function minutes(item) {
  const value = item?.data || item || {};
  const seconds = Number(value.durationSec || value.actualSeconds || 0);
  return Number(value.estimatedMinutes || value.durationMinutes || (seconds ? Math.round(seconds / 60) : 0)) || 0;
}

function guideCard(guide) {
  if (!guide) return `<section class="empty"><h2>今天还没有安排</h2><p>离线状态下仍可添加身体状态，联网后会同步日计划。</p></section>`;
  const item = guide.item?.data || guide.item || {};
  const meta = typeMeta(item.trainingType || item.type);
  const sourceText = { actual: "已完成", adjustment: "今日安排", plan: "计划", fallback: "建议" }[guide.source] || "计划";
  return `<section class="guide tone-${meta.tone}">
    <div class="eyebrow">${sourceText}</div>
    <h2>${escape(displayTitle(item))}</h2>
    <p>${minutes(item) ? `${minutes(item)} 分钟` : "按状态安排"}</p>
    <p class="guide-note">${escape(item.notes || item.reason || "以动作质量和身体感受为准。")}</p>
    ${guide.source !== "actual" ? `<button data-action="start-guide" class="primary">开始训练</button>` : ""}
  </section>`;
}

function renderToday(records) {
  const model = buildTodayModel(records, today, { title: "恢复或轻活动", trainingType: "recovery", estimatedMinutes: 15 });
  const metric = model.latestMetrics?.data || {};
  return `<main class="screen today-screen">
    <header class="screen-head"><div><p class="eyebrow">${today}</p><h1>今天</h1></div><span class="sync-dot">离线优先</span></header>
    ${guideCard(model.guide)}
    <section class="summary-grid">
      <article><span>身体状态</span><strong>${metric.weight ? `${escape(metric.weight)} kg` : "待记录"}</strong></article>
      <article><span>正式记录</span><strong>${model.formalLogs.length} 条</strong></article>
      <article><span>计时器</span><strong>${model.timerSessions.length} 条</strong></article>
    </section>
    <section class="section-head"><h2>快捷入口</h2></section>
    <div class="quick-actions">
      <button data-view="training"><b>选择流程</b><span>查看可用训练方案</span></button>
      <button data-view="logs"><b>查看记录</b><span>正式训练与状态</span></button>
      <button data-view="data"><b>查看数据</b><span>身体趋势</span></button>
    </div>
  </main>`;
}

function renderTraining(records) {
  const routines = buildTodayModel(records, today).availableRoutines;
  return `<main class="screen"><header class="screen-head"><div><p class="eyebrow">按场景选择</p><h1>训练</h1></div></header>
    <p class="intro">只展示当前可用方案。计时器独立执行，并把事实记录写回云端。</p>
    <div class="routine-list">${routines.length ? routines.map((record) => {
      const item = record.data || record;
      const meta = typeMeta(item.trainingType || item.type);
      return `<article class="routine-item"><div><span class="type-chip tone-${meta.tone}">${meta.label}</span><h2>${escape(displayTitle(item))}</h2><p>${escape(item.scene || "未标注场景")} · ${minutes(item) || "--"} 分钟</p></div><button class="icon-action" data-action="start-routine" data-id="${escape(record.id)}" aria-label="打开 ${escape(displayTitle(item))} 计时器">开始</button></article>`;
    }).join("") : `<section class="empty"><h2>暂无可用方案</h2><p>先在身刻 Web 导入并同步方案；手机端不会自行生成或猜测场景。</p></section>`}</div>
  </main>`;
}

function renderLogs(records) {
  const logs = getRecentLogs(records, 30);
  return `<main class="screen"><header class="screen-head"><div><p class="eyebrow">只显示正式记录</p><h1>记录</h1></div></header>
  <div class="log-list">${logs.length ? logs.map((record) => {
    const item = record.data || record;
    const meta = typeMeta(item.trainingType || item.type);
    return `<article class="log-item"><span class="type-chip tone-${meta.tone}">${meta.label}</span><div><h2>${escape(displayTitle(item))}</h2><p>${escape(item.date || "")} · ${minutes(item) || "--"} 分钟${item.status ? ` · ${escape(item.status)}` : ""}</p></div></article>`;
  }).join("") : `<section class="empty"><h2>还没有正式训练记录</h2><p>计时器 session 需要在身刻确认并补充实际训练信息后才会进入这里。</p></section>`}</div>
  </main>`;
}

function trend(field, label, unit) {
  const series = getMetricSeries(state.records, field);
  const latest = series.at(-1);
  const values = series.map((item) => item.value);
  const min = Math.min(...values, 0);
  const max = Math.max(...values, 1);
  const points = values.map((value, index) => `${index * (100 / Math.max(values.length - 1, 1))},${100 - ((value - min) / Math.max(max - min, 0.1)) * 100}`).join(" ");
  return `<article class="metric-card"><div><h2>${label}</h2><strong>${latest ? `${latest.value} ${unit}` : "待记录"}</strong></div>${series.length > 1 ? `<svg viewBox="0 0 100 100" preserveAspectRatio="none" aria-label="${label}趋势"><polyline points="${points}" /></svg>` : `<p>至少再记录一次即可显示趋势。</p>`}</article>`;
}

function renderData() {
  return `<main class="screen"><header class="screen-head"><div><p class="eyebrow">身体趋势</p><h1>数据</h1></div></header><div class="metric-list">
    ${trend("weight", "体重", "kg")}${trend("waist", "腰围", "cm")}${trend("bodyFat", "体脂率", "%")}${trend("muscleMass", "肌肉量", "kg")}
  </div></main>`;
}

function renderSettings() {
  const config = state.config || {};
  const secureHint = Capacitor.isNativePlatform() ? "访问密钥会保存到 Android Keystore。" : "Web 预览不允许保存访问密钥，请在 Android 容器中完成配置。";
  return `<main class="screen"><header class="screen-head"><div><p class="eyebrow">本机与同步</p><h1>设置</h1></div></header>
    <section class="settings-card"><h2>云端连接</h2><label>API 地址<input id="apiBase" value="${escape(config.apiBase)}" placeholder="https://…/api" inputmode="url" /></label>
      <label>计时器地址<input id="timerUrl" value="${escape(config.timerUrl)}" placeholder="https://…/home-training-timer/" inputmode="url" /></label>
      <label>身刻访问密钥<input id="token" type="password" placeholder="仅保存到 Android 安全存储" /></label>
      <label>计时器访问密钥<input id="timerToken" type="password" placeholder="仅保存到 Android 安全存储" /></label>
      <p class="security-note">${secureHint}</p><button data-action="save-config" class="primary">保存本机配置</button><button data-action="sync" class="secondary">立即同步</button>
    </section>
    <section class="settings-card"><h2>离线队列</h2><p>${repository.snapshot().outbox.length} 条本地修改等待写入云端。网络恢复后可在这里手动同步。</p></section>
  </main>`;
}

function render() {
  const renderers = { today: () => renderToday(state.records), training: () => renderTraining(state.records), logs: () => renderLogs(state.records), data: renderData, settings: renderSettings };
  document.querySelector("#app").innerHTML = `${renderers[state.view]()}<nav class="bottom-nav" aria-label="主导航">${[
    ["today", "今天"], ["training", "训练"], ["logs", "记录"], ["data", "数据"], ["settings", "设置"]
  ].map(([view, label]) => `<button data-view="${view}" class="${state.view === view ? "active" : ""}">${label}</button>`).join("")}</nav><div class="toast" role="status">${escape(state.message || "")}</div>`;
}

async function refreshFromRepository() {
  state.records = repository.snapshot().records;
  render();
}

document.addEventListener("click", async (event) => {
  const button = event.target.closest("button");
  if (!button) return;
  if (button.dataset.view) {
    state.view = button.dataset.view;
    state.message = "";
    render();
    return;
  }
  try {
    if (button.dataset.action === "start-guide") {
      const guide = buildTodayModel(state.records, today).guide?.item?.data || {};
      await runtime.openTimer(state.config.timerUrl, { routineId: guide.routineId, date: today, dailyPlanItemId: guide.id });
    }
    if (button.dataset.action === "start-routine") {
      const routine = state.records.find((record) => record.entity === "routine_templates" && record.id === button.dataset.id)?.data || {};
      await runtime.openTimer(state.config.timerUrl, { routineId: routine.routineId || button.dataset.id, date: today });
    }
    if (button.dataset.action === "save-config") {
      const next = {
        apiBase: document.querySelector("#apiBase").value.trim(), timerUrl: document.querySelector("#timerUrl").value.trim(),
        token: document.querySelector("#token").value.trim() || state.config.token,
        timerToken: document.querySelector("#timerToken").value.trim() || state.config.timerToken
      };
      await secureConfig.save({ ...state.config, ...next });
      state.config = await secureConfig.load();
      state.message = "本机配置已保存。";
    }
    if (button.dataset.action === "sync") {
      const pulled = await cloudSync.pull(state.config);
      const pushed = await cloudSync.push(state.config);
      state.message = `已读取 ${pulled} 条，已写入 ${pushed.written} 条。`;
      await refreshFromRepository();
    }
    await runtime.impact();
    render();
  } catch (error) {
    state.message = error.message || "操作失败。";
    render();
  }
});

await repository.initialize();
state.config = await secureConfig.load();
state.records = repository.snapshot().records;
state.message = "离线数据已就绪。";
render();
