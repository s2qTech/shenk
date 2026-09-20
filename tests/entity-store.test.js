"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");

function createLocalStorage() {
  const values = new Map();
  return {
    getItem(key) { return values.has(key) ? values.get(key) : null; },
    setItem(key, value) { values.set(key, String(value)); },
    keys() { return [...values.keys()]; }
  };
}

const { createIndexedDb } = require("./helpers/indexed-db");

function loadStore(browser) {
  const source = fs.readFileSync(path.join(__dirname, "..", "src", "entity-store.js"), "utf8");
  const context = { window: browser, setTimeout, Date, JSON };
  context.globalThis = context;
  vm.createContext(context);
  vm.runInContext(source, context, { filename: "entity-store.js" });
  return browser.ShenkeEntityStore.create({
    window: browser,
    dbName: "fixture-db",
    dbVersion: 2,
    backupKeyPrefix: "fixture:backup:",
    migrationKey: "fixture-migration"
  });
}

async function run() {
  const localStorage = createLocalStorage();
  const indexedDB = createIndexedDb();
  const store = loadStore({ indexedDB, localStorage });
  const records = [{
    id: "log_1",
    entity: "training_logs",
    revision: 1,
    syncState: "dirty",
    data: { id: "log_1", date: "2099-01-01" }
  }];
  const outbox = [{
    key: "training_logs:log_1",
    entity: "training_logs",
    recordId: "log_1",
    operation: "upsert",
    baseRevision: 1,
    envelope: records[0]
  }];

  const legacySnapshot = { schemaVersion: "v1", workouts: [], records };
  const migrated = await store.initializeFromSnapshot(records, outbox, legacySnapshot);
  assert.equal(migrated.available, true);
  assert.equal(migrated.migrated, true);
  assert.equal(migrated.records.length, 1);
  assert.equal(migrated.outbox.length, 1);
  assert.equal(localStorage.keys().length, 1);
  assert.equal(JSON.parse(localStorage.getItem(localStorage.keys()[0])).snapshot.schemaVersion, "v1");

  const second = await store.initializeFromSnapshot([], []);
  assert.equal(second.migrated, false);
  assert.equal(second.records.length, 1);

  await store.recordFailure(["training_logs:log_1"], "offline", "2099-01-01T00:00:10.000Z");
  const failed = await store.loadOutbox();
  assert.equal(failed[0].attempts, 1);
  assert.equal(failed[0].lastError, "offline");

  await store.scheduleRetry(["training_logs:log_1"], "2099-01-01T00:00:30.000Z");
  const scheduled = await store.loadOutbox();
  assert.equal(scheduled[0].nextAttemptAt, "2099-01-01T00:00:30.000Z");

  const reopened = loadStore({ indexedDB, localStorage });
  assert.equal((await reopened.loadOutbox())[0].nextAttemptAt, "2099-01-01T00:00:30.000Z");

  await store.setMetaValue("legacy-snapshot-checkpoint-v2", {
    createdAt: "2099-01-01T00:00:31.000Z",
    reason: "background"
  });
  assert.deepEqual(
    JSON.parse(JSON.stringify(await reopened.getMetaValue("legacy-snapshot-checkpoint-v2"))),
    { createdAt: "2099-01-01T00:00:31.000Z", reason: "background" }
  );

  const cleanRecord = { ...records[0], syncState: "clean", revision: 2 };
  await store.persist([cleanRecord], []);
  const finalRecords = await store.loadRecords();
  assert.equal(finalRecords[0].revision, 2);
  assert.deepEqual(JSON.parse(JSON.stringify(await store.loadOutbox())), []);

  const scansBeforeSave = indexedDB.stats.recordScans;
  const changed = { ...cleanRecord, syncState: "dirty", data: { ...cleanRecord.data, notes: "synthetic change" } };
  indexedDB.failNextWrite();
  await assert.rejects(store.persist([changed], [{ ...outbox[0], envelope: changed }], { "pull-fixture": "2099-01-02" }));
  assert.equal((await reopened.loadRecords())[0].revision, 2);
  assert.equal((await reopened.loadRecords())[0].data.notes, undefined);
  assert.equal((await reopened.loadOutbox()).length, 0);
  assert.equal(await reopened.getMetaValue("pull-fixture"), null);
  const scansAfterVerification = indexedDB.stats.recordScans;
  await store.persist([changed], [{ ...outbox[0], envelope: changed }], { "pull-fixture": "2099-01-02" });
  assert.equal(indexedDB.stats.recordScans, scansAfterVerification, "ordinary save must not rescan the record store");
  assert.equal(await reopened.getMetaValue("pull-fixture"), "2099-01-02");
  assert.equal((await reopened.loadOutbox()).length, 1);
  const staleChange = { ...cleanRecord, data: { ...cleanRecord.data, notes: "stale tab" } };
  await assert.rejects(reopened.persist([staleChange], []), /entity_store_changed_in_another_tab/);
  assert.equal((await store.loadRecords())[0].data.notes, "synthetic change");
  assert.equal((await store.loadOutbox()).length, 1);
  assert(scansBeforeSave > 0);

  console.log("entity-store.test.js passed");
}

run().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
