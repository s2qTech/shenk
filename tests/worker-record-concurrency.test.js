const { test } = require("node:test");
const assert = require("node:assert/strict");
const { loadRecordWorker, recordDatabase } = require("./helpers/worker-sqlite");
const worker = loadRecordWorker();
const client = { role: "shenk" };
const record = (id, value, baseRevision = 0) => ({ entity: "body_metrics", id, baseRevision, data: { id, date: "2099-01-01", weightKg: value } });
const write = (DB, ...records) => worker.upsertRecords({ DB }, { contractVersion: "1.0", records }, client);
const pull = (DB, since = null, cursor = undefined, limit = 2) => worker.queryRecords({ DB }, { entities: ["body_metrics"], since, cursor, limit }, client);

test("concurrent same-revision updates accept one and expose the other as conflict", async () => {
  const DB = recordDatabase();
  await write(DB, record("one", 70));
  const results = await Promise.all([write(DB, record("one", 71, 1)), write(DB, record("one", 72, 1))]);
  assert.equal(results.reduce((sum, r) => sum + r.accepted.length, 0), 1);
  assert.equal(results.reduce((sum, r) => sum + r.conflicts.length, 0), 1);
  assert.equal(DB.sql.prepare("SELECT COUNT(*) n FROM cloud_events").get().n, 2);
  assert.equal(DB.sql.prepare("SELECT revision FROM cloud_records").get().revision, 2);
  DB.sql.close();
});

test("concurrent creates cannot overwrite and identical retries are idempotent", async () => {
  const DB = recordDatabase();
  const results = await Promise.all([write(DB, record("one", 70)), write(DB, record("one", 70))]);
  assert.equal(results.reduce((sum, r) => sum + r.accepted.length, 0), 2);
  assert.equal(DB.sql.prepare("SELECT COUNT(*) n FROM cloud_events").get().n, 1);
  const conflict = await write(DB, record("one", 71));
  assert.equal(conflict.conflicts.length, 1);
  DB.sql.close();
});

test("event failure rolls back the record write", async () => {
  const DB = recordDatabase();
  DB.sql.exec("CREATE TRIGGER reject_event BEFORE INSERT ON cloud_events BEGIN SELECT RAISE(ABORT, 'synthetic failure'); END;");
  await assert.rejects(write(DB, record("one", 70)));
  assert.equal(DB.sql.prepare("SELECT COUNT(*) n FROM cloud_records").get().n, 0);
  DB.sql.close();
});

test("commits between and after pages are never skipped, including future legacy timestamps", async () => {
  const DB = recordDatabase();
  await write(DB, record("one", 70), record("two", 71), record("three", 72));
  DB.sql.exec("UPDATE cloud_records SET updated_at='2099-01-01T00:00:00.000Z' WHERE id='three'");
  const first = await pull(DB);
  await write(DB, record("four", 73));
  const second = await pull(DB, null, first.nextCursor);
  assert.equal(second.records.length, 2);
  assert(second.records[1].updatedAt > "2099-01-01T00:00:00.000Z");
  await write(DB, record("five", 74));
  const next = await pull(DB, second.serverTime);
  assert.equal(next.records[0].id, "five");
  const empty = await pull(DB, next.serverTime);
  assert.equal(empty.serverTime, next.serverTime);
  await write(DB, record("six", 75));
  assert.equal((await pull(DB, empty.serverTime)).records[0].id, "six");
  DB.sql.close();
});
