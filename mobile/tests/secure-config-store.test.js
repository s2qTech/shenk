import test from "node:test";
import assert from "node:assert/strict";
import { createSecureConfigStore } from "../src/platform/secure-config-store.js";

function memoryStorage() { const values = new Map(); return { getItem: (key) => values.get(key) || null, setItem: (key, value) => values.set(key, value), raw: values }; }

test("公开配置与密钥分开保存", async () => {
  const storage = memoryStorage();
  const native = { value: null, async get() { return { value: this.value }; }, async set({ value }) { this.value = value; }, async remove() { this.value = null; } };
  const store = createSecureConfigStore({ secureStore: native, storage });
  await store.save({ apiBase: "https://api.example", timerUrl: "https://timer.example", token: "secret", timerToken: "timer-secret" });
  const publicValue = [...storage.raw.values()].join("");
  assert.doesNotMatch(publicValue, /secret/);
  assert.match(native.value, /secret/);
  assert.equal((await store.load()).token, "secret");
});

test("没有原生安全存储时拒绝保存密钥", async () => {
  const store = createSecureConfigStore({ storage: memoryStorage() });
  await assert.rejects(() => store.save({ token: "secret" }), /安全存储/);
});
