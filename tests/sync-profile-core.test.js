"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");
const { webcrypto } = require("node:crypto");

function loadCore() {
  const corePath = path.join(__dirname, "..", "src", "sync-profile-core.js");
  const source = fs.readFileSync(corePath, "utf8");
  const window = {
    crypto: webcrypto,
    btoa(value) { return Buffer.from(value, "binary").toString("base64"); },
    atob(value) { return Buffer.from(value, "base64").toString("binary"); }
  };
  const context = {
    window,
    TextEncoder,
    TextDecoder,
    Uint8Array,
    Date
  };
  context.globalThis = context;
  vm.createContext(context);
  vm.runInContext(source, context, { filename: corePath });
  return window.ShenkeSyncProfileCore.create({
    defaultApiBase: "https://example.test/api",
    defaultTimerUrl: "https://timer.example.test/",
    crypto: webcrypto,
    iterations: 1000
  });
}

async function run() {
  const core = loadCore();

  assert.equal(core.normalizeApiBase("https://example.test/"), "https://example.test/api");
  assert.equal(core.normalizeApiBase("https://example.test/api/"), "https://example.test/api");
  assert.equal(core.normalizeTimerUrl(""), "https://timer.example.test/");
  assert.throws(() => core.assertAccessKey("short"));

  const transferCode = core.generateAccessKey();
  const profileIdA = await core.deriveProfileId(transferCode);
  const profileIdB = await core.deriveProfileId(transferCode);
  assert.equal(profileIdA, profileIdB);
  assert.match(profileIdA, /^profile_[A-Za-z0-9_-]+$/);

  const payload = {
    apiBase: "https://example.test/api",
    timerUrl: "https://timer.example.test/",
    token: core.generateAccessKey(),
    timerToken: core.generateAccessKey()
  };
  const encrypted = await core.encrypt(payload, transferCode);
  assert.equal(encrypted.schema, "shenk_sync_profile/v1");
  assert.notEqual(encrypted.ciphertext, JSON.stringify(payload));
  assert.deepEqual(
    JSON.parse(JSON.stringify(await core.decrypt(encrypted, transferCode))),
    payload
  );
  await assert.rejects(() => core.decrypt(encrypted, "not-the-same-transfer-code"));

  console.log("sync-profile-core.test.js passed");
}

run().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
