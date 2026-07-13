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

function createIndexedDb() {
  const stores = new Map();
  const database = {
    objectStoreNames: { contains(name) { return stores.has(name); } },
    createObjectStore(name) {
      stores.set(name, new Map());
      return null;
    },
    transaction(name) {
      const rows = stores.get(name);
      const tx = { oncomplete: null, onerror: null, error: null };
      const finish = () => setTimeout(() => tx.oncomplete?.(), 0);
      tx.objectStore = () => {
          return {
            getAll() {
              const request = { onsuccess: null, onerror: null, result: null };
              setTimeout(() => {
                request.result = [...rows.values()];
                request.onsuccess?.();
              }, 0);
              return request;
            },
            get(key) {
              const request = { onsuccess: null, onerror: null, result: null };
              setTimeout(() => {
                request.result = rows.get(key) || null;
                request.onsuccess?.();
              }, 0);
              return request;
            },
            put(value) {
              rows.set(value.key, value);
              finish();
            },
            delete(key) {
              rows.delete(key);
              finish();
            }
          };
      };
      return tx;
    }
  };
  return {
    open() {
      const request = { result: database, onupgradeneeded: null, onsuccess: null, onerror: null };
      setTimeout(() => {
        request.onupgradeneeded?.();
        request.onsuccess?.();
      }, 0);
      return request;
    }
  };
}

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

  console.log("entity-store.test.js passed");
}

run().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
