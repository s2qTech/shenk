import "../../../src/entity-store.js";

const WRITABLE_ENTITIES = new Set([
  "plan_templates", "routine_templates", "daily_plan_items", "plan_adjustments", "training_logs",
  "body_metrics", "weather_logs", "media_assets", "feedback_summaries"
]);

function clone(value) {
  return JSON.parse(JSON.stringify(value));
}

function recordKey(record) {
  return `${record.entity}:${record.id}`;
}

export function createMobileRepository({ window = globalThis } = {}) {
  const entityStore = window.ShenkeEntityStore.create({ window, dbName: "shenk-mobile" });
  let records = [];
  let outbox = [];

  async function initialize() {
    const result = await entityStore.initializeFromSnapshot([], [], { source: "mobile" });
    records = result.records || [];
    outbox = result.outbox || [];
    return snapshot();
  }

  function snapshot() {
    return { records: clone(records), outbox: clone(outbox) };
  }

  async function persist() {
    const result = await entityStore.persist(records, outbox);
    outbox = result.outbox || outbox;
    return snapshot();
  }

  async function upsert(record, { enqueue = false } = {}) {
    if (!record?.entity || !record?.id) throw new Error("记录必须包含 entity 和 id。");
    if (record.entity === "timer_sessions") throw new Error("身刻移动端只读 timer_sessions。");
    if (!WRITABLE_ENTITIES.has(record.entity)) throw new Error(`不允许写入 ${record.entity}。`);
    const index = records.findIndex((item) => recordKey(item) === recordKey(record));
    const next = clone(record);
    if (index >= 0) records[index] = next;
    else records.push(next);
    if (enqueue) {
      const key = recordKey(next);
      outbox = [...outbox.filter((item) => item.key !== key), {
        key,
        entity: next.entity,
        id: next.id,
        record: next,
        baseRevision: next.baseRevision || 0,
        createdAt: new Date().toISOString()
      }];
    }
    return persist();
  }

  async function replaceFromCloud(incoming) {
    const localDirty = new Map(outbox.map((item) => [item.key, item.record]));
    const next = new Map(records.map((item) => [recordKey(item), item]));
    (incoming || []).forEach((record) => {
      const key = recordKey(record);
      if (!localDirty.has(key)) next.set(key, clone(record));
    });
    records = [...next.values()];
    return persist();
  }

  async function markSynced(keys) {
    const accepted = new Set(keys || []);
    outbox = outbox.filter((item) => !accepted.has(item.key));
    return persist();
  }

  return { initialize, snapshot, upsert, replaceFromCloud, markSynced, persist };
}
