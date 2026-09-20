const fs = require("node:fs");
const vm = require("node:vm");
const { DatabaseSync } = require("node:sqlite");
const crypto = require("node:crypto").webcrypto;

function loadRecordWorker() {
  const source = fs.readFileSync(require.resolve("../../cloudflare/worker.js"), "utf8")
    .replace('import { WorkflowEntrypoint } from "cloudflare:workers";', "class WorkflowEntrypoint {}")
    .replace("export default {", "globalThis.worker = {")
    .replace("export class DailyReviewWorkflow", "class DailyReviewWorkflow");
  const context = vm.createContext({ crypto, URL, Request, Response, Headers, TextEncoder, TextDecoder, btoa, atob });
  vm.runInContext(source + "\nglobalThis.records = { queryRecords, upsertRecords };", context);
  return context.records;
}

function recordDatabase() {
  const sql = new DatabaseSync(":memory:");
  sql.exec(fs.readFileSync(require.resolve("../../cloudflare/migrations/0001_cloud_records.sql"), "utf8"));
  const DB = {
    sql,
    prepare(query) {
      const bind = (...args) => ({
        query, args,
        first: async () => sql.prepare(query).get(...args) || null,
        all: async () => ({ results: sql.prepare(query).all(...args) }),
        run: async () => ({ meta: sql.prepare(query).run(...args) })
      });
      return { ...bind(), bind };
    },
    async batch(statements) {
      sql.exec("BEGIN");
      try {
        const results = statements.map(({ query, args }) => ({ results: sql.prepare(query).all(...args) }));
        sql.exec("COMMIT");
        return results;
      } catch (error) {
        sql.exec("ROLLBACK");
        throw error;
      }
    }
  };
  return DB;
}
module.exports = { loadRecordWorker, recordDatabase };
