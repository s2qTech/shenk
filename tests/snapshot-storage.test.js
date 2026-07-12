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
    dump(key) { return values.get(key); }
  };
}

function createCore(browser) {
  const corePath = path.join(__dirname, "..", "src", "snapshot-storage.js");
  const source = fs.readFileSync(corePath, "utf8");
  const context = { window: browser };
  context.globalThis = context;
  vm.createContext(context);
  vm.runInContext(source, context, { filename: corePath });
  return browser.ShenkeSnapshotStorage.create({
    window: browser,
    dbName: "fixture-db",
    snapshotKey: "snapshot",
    fallbackKey: "fixture:snapshot"
  });
}

async function run() {
  const localStorage = createLocalStorage();
  const browser = { localStorage };
  const storage = createCore(browser);

  assert.deepEqual(
    JSON.parse(JSON.stringify(await storage.load())),
    { snapshot: null, mode: "localStorage" }
  );

  const snapshot = { records: { training_logs: [{ id: "log_1" }] } };
  assert.deepEqual(
    JSON.parse(JSON.stringify(await storage.save(snapshot))),
    { mode: "localStorage" }
  );
  assert.deepEqual(JSON.parse(localStorage.dump("fixture:snapshot")), snapshot);
  assert.deepEqual(
    JSON.parse(JSON.stringify(await storage.load())),
    { snapshot, mode: "localStorage" }
  );

  console.log("snapshot-storage.test.js passed");
}

run().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
