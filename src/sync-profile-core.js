(function (global) {
  "use strict";

  const PROFILE_SCHEMA = "shenk_sync_profile/v1";
  const PROFILE_CONFIG_SCHEMA = "shenke_config_v1";

  function create(options = {}) {
    const cryptoApi = options.crypto || global.crypto;
    const defaultApiBase = String(options.defaultApiBase || "");
    const defaultTimerUrl = String(options.defaultTimerUrl || "");
    const iterations = Number(options.iterations || 210000);

    function normalizeApiBase(value) {
      const next = String(value || "").trim().replace(/\/+$/, "");
      if (!next) return "";
      return next.endsWith("/api") ? next : `${next}/api`;
    }

    function normalizeTimerUrl(value) {
      return String(value || "").trim() || defaultTimerUrl;
    }

    function normalizeProfileId(value) {
      return String(value || "").trim();
    }

    function normalizeAccessKey(value) {
      return String(value || "").trim();
    }

    function assertAccessKey(value) {
      const key = normalizeAccessKey(value);
      if (!/^[A-Za-z0-9_-]{20,200}$/.test(key)) {
        throw new Error("请粘贴有效迁移码，或在旧浏览器重新生成迁移码。");
      }
      return key;
    }

    function ensureWebCrypto() {
      if (!cryptoApi?.subtle || !cryptoApi?.getRandomValues) {
        throw new Error("当前浏览器不支持本地加密配置档案，请使用 HTTPS 页面或现代浏览器。");
      }
    }

    function bytesToBase64Url(bytes) {
      let binary = "";
      bytes.forEach((byte) => {
        binary += String.fromCharCode(byte);
      });
      return global.btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
    }

    function base64UrlToBytes(value) {
      const base64 = String(value || "").replace(/-/g, "+").replace(/_/g, "/");
      const padded = base64.padEnd(base64.length + ((4 - base64.length % 4) % 4), "=");
      const binary = global.atob(padded);
      return Uint8Array.from(binary, (char) => char.charCodeAt(0));
    }

    function generateAccessKey() {
      ensureWebCrypto();
      const bytes = cryptoApi.getRandomValues(new Uint8Array(32));
      return bytesToBase64Url(bytes);
    }

    async function deriveKey(migrationCode, salt) {
      ensureWebCrypto();
      const material = await cryptoApi.subtle.importKey(
        "raw",
        new TextEncoder().encode(migrationCode),
        "PBKDF2",
        false,
        ["deriveKey"]
      );
      return cryptoApi.subtle.deriveKey(
        { name: "PBKDF2", salt, iterations, hash: "SHA-256" },
        material,
        { name: "AES-GCM", length: 256 },
        false,
        ["encrypt", "decrypt"]
      );
    }

    async function encrypt(payload, migrationCode) {
      ensureWebCrypto();
      const salt = cryptoApi.getRandomValues(new Uint8Array(16));
      const iv = cryptoApi.getRandomValues(new Uint8Array(12));
      const key = await deriveKey(migrationCode, salt);
      const encoded = new TextEncoder().encode(JSON.stringify(payload));
      const ciphertext = new Uint8Array(await cryptoApi.subtle.encrypt({ name: "AES-GCM", iv }, key, encoded));
      return {
        schema: PROFILE_SCHEMA,
        cipher: "AES-GCM",
        kdf: "PBKDF2-SHA256",
        iterations,
        salt: bytesToBase64Url(salt),
        iv: bytesToBase64Url(iv),
        ciphertext: bytesToBase64Url(ciphertext),
        updatedAt: new Date().toISOString()
      };
    }

    function parseConfigPayload(payload) {
      const config = {
        apiBase: normalizeApiBase(payload?.apiBase || payload?.cloudApiBase || ""),
        timerUrl: normalizeTimerUrl(payload?.timerUrl || defaultTimerUrl),
        token: String(payload?.token || payload?.shenkToken || ""),
        timerToken: String(payload?.timerToken || "")
      };
      if (!config.apiBase || !config.token || !config.timerToken) {
        throw new Error("配置包缺少 API、身刻密钥或计时器密钥。");
      }
      return config;
    }

    async function decrypt(profile, migrationCode) {
      ensureWebCrypto();
      if (!profile || profile.schema !== PROFILE_SCHEMA) throw new Error("配置档案格式不正确。");
      const salt = base64UrlToBytes(profile.salt);
      const iv = base64UrlToBytes(profile.iv);
      const ciphertext = base64UrlToBytes(profile.ciphertext);
      const key = await deriveKey(migrationCode, salt);
      let plaintext = null;
      try {
        plaintext = await cryptoApi.subtle.decrypt({ name: "AES-GCM", iv }, key, ciphertext);
      } catch (error) {
        throw new Error("迁移码不正确，或加密配置档案已损坏。");
      }
      return parseConfigPayload(JSON.parse(new TextDecoder().decode(new Uint8Array(plaintext))));
    }

    async function deriveProfileId(migrationCode) {
      ensureWebCrypto();
      const digest = new Uint8Array(await cryptoApi.subtle.digest(
        "SHA-256",
        new TextEncoder().encode(migrationCode)
      ));
      return `profile_${bytesToBase64Url(digest).slice(0, 48)}`;
    }

    return {
      PROFILE_SCHEMA,
      PROFILE_CONFIG_SCHEMA,
      normalizeApiBase,
      normalizeTimerUrl,
      normalizeProfileId,
      normalizeAccessKey,
      assertAccessKey,
      generateAccessKey,
      encrypt,
      decrypt,
      parseConfigPayload,
      deriveProfileId
    };
  }

  global.ShenkeSyncProfileCore = { create };
})(typeof window !== "undefined" ? window : globalThis);
