const READ_ENTITIES = [
  "plan_templates", "routine_templates", "daily_plan_items", "plan_adjustments", "training_logs",
  "body_metrics", "weather_logs", "media_assets", "feedback_summaries", "timer_sessions"
];

async function post(apiBase, path, token, payload) {
  const response = await fetch(`${String(apiBase || "").replace(/\/$/, "")}${path}`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "Authorization": `Bearer ${token}`
    },
    body: JSON.stringify(payload)
  });
  const body = await response.json().catch(() => ({}));
  if (!response.ok || body.ok === false) throw new Error(body.error || `cloud_${response.status}`);
  return body;
}

export function createCloudSync({ repository, deviceId }) {
  return {
    async pull(config) {
      if (!config?.apiBase || !config?.token) throw new Error("请先完成云端连接配置。");
      const records = [];
      let cursor = "";
      do {
        const body = await post(config.apiBase, "/records/query", config.token, {
          contractVersion: "1.0", deviceId, entities: READ_ENTITIES, cursor
        });
        records.push(...(body.records || []));
        cursor = body.nextCursor || "";
      } while (cursor);
      await repository.replaceFromCloud(records);
      return records.length;
    },
    async push(config) {
      if (!config?.apiBase || !config?.token) throw new Error("请先完成云端连接配置。");
      const { outbox } = repository.snapshot();
      if (!outbox.length) return { written: 0, conflicts: [] };
      const body = await post(config.apiBase, "/records/upsert", config.token, {
        contractVersion: "1.0", deviceId,
        records: outbox.map((item) => ({ ...item.record, baseRevision: item.baseRevision }))
      });
      const accepted = (body.accepted || []).map((record) => `${record.entity}:${record.id}`);
      await repository.markSynced(accepted);
      return { written: accepted.length, conflicts: body.conflicts || [] };
    }
  };
}
