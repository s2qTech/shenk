"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");
const { webcrypto } = require("node:crypto");

function loadSyncTransferApi() {
  const appPath = path.join(__dirname, "..", "src", "app.js");
  const corePath = path.join(__dirname, "..", "src", "sync-profile-core.js");
  const storagePath = path.join(__dirname, "..", "src", "snapshot-storage.js");
  const entityStorePath = path.join(__dirname, "..", "src", "entity-store.js");
  const source = fs.readFileSync(appPath, "utf8");
  const coreSource = fs.readFileSync(corePath, "utf8");
  const storageSource = fs.readFileSync(storagePath, "utf8");
  const entityStoreSource = fs.readFileSync(entityStorePath, "utf8");
  const hook = `
  globalThis.__shenkeSyncTransferTest = {
    deriveSyncProfileIdFromTransferCode,
    encryptSyncProfilePayload,
    decryptSyncProfilePayload
  };
`;
  const instrumented = source.replace(/\}\)\(\);\s*$/, `${hook}\n})();`);
  const element = {
    innerHTML: "",
    className: "",
    dataset: {},
    classList: { add() {}, remove() {}, toggle() {} },
    addEventListener() {},
    appendChild() {},
    remove() {},
    querySelector() { return null; },
    querySelectorAll() { return []; },
    setAttribute() {},
    getAttribute() { return null; }
  };
  const context = {
    console,
    setTimeout,
    clearTimeout,
    TextEncoder,
    TextDecoder,
    Uint8Array,
    Blob: function Blob() {},
    URL: { createObjectURL: () => "", revokeObjectURL() {} },
    FileReader: function FileReader() {},
    btoa(value) { return Buffer.from(value, "binary").toString("base64"); },
    atob(value) { return Buffer.from(value, "base64").toString("binary"); },
    window: {
      localStorage: { getItem() { return null; }, setItem() {}, removeItem() {} },
      btoa(value) { return Buffer.from(value, "binary").toString("base64"); },
      atob(value) { return Buffer.from(value, "base64").toString("binary"); },
      crypto: {
        randomUUID: () => "uuid_fixture",
        getRandomValues: webcrypto.getRandomValues.bind(webcrypto),
        subtle: webcrypto.subtle
      },
      addEventListener() {},
      setTimeout,
      clearTimeout,
      confirm: () => true,
      prompt: () => null,
      navigator: {},
      ShenkeRecommendationEngine: { getRecommendation: () => ({ type: "easyWalk" }) }
    },
    document: {
      body: element,
      getElementById: () => element,
      addEventListener() {},
      createElement: () => ({ ...element, click() {} }),
      querySelector: () => null,
      querySelectorAll: () => []
    },
    navigator: {},
    indexedDB: undefined
  };
  context.globalThis = context;
  vm.createContext(context);
  vm.runInContext(coreSource, context, { filename: corePath });
  vm.runInContext(storageSource, context, { filename: storagePath });
  vm.runInContext(entityStoreSource, context, { filename: entityStorePath });
  vm.runInContext(instrumented, context, { filename: appPath });
  return context.__shenkeSyncTransferTest;
}

async function run() {
  const api = loadSyncTransferApi();
  const migrationCode = "Ncv_3tMJ5V7DCS9gTgH0aWzqY_eZ4bKv1X2pL8rQ6s";
  const firstId = await api.deriveSyncProfileIdFromTransferCode(migrationCode);
  const secondId = await api.deriveSyncProfileIdFromTransferCode(migrationCode);
  const differentId = await api.deriveSyncProfileIdFromTransferCode(`${migrationCode}_other`);
  assert.equal(firstId, secondId);
  assert.notEqual(firstId, differentId);
  assert.match(firstId, /^profile_[A-Za-z0-9_-]{40,}$/);

  const payload = {
    schema: "shenke_config_v1",
    apiBase: "https://example.workers.dev/api",
    timerUrl: "https://example.github.io/timer/",
    token: "fixture_shenk_token",
    timerToken: "fixture_timer_token"
  };
  const encrypted = await api.encryptSyncProfilePayload(payload, migrationCode);
  assert.notEqual(encrypted.ciphertext, JSON.stringify(payload));
  const decrypted = await api.decryptSyncProfilePayload(encrypted, migrationCode);
  assert.deepEqual(JSON.parse(JSON.stringify(decrypted)), {
    apiBase: payload.apiBase,
    timerUrl: payload.timerUrl,
    token: payload.token,
    timerToken: payload.timerToken
  });
  await assert.rejects(
    () => api.decryptSyncProfilePayload(encrypted, `${migrationCode}_wrong`),
    /迁移码不正确/
  );
  console.log("sync-transfer tests passed");
}

run().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
