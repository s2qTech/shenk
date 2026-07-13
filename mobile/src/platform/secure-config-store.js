const PUBLIC_CONFIG_KEY = "shenk-mobile-public-config-v1";
const SECRET_CONFIG_KEY = "shenk-mobile-secret-config-v1";

function pickPublic(config = {}) {
  return {
    apiBase: String(config.apiBase || ""),
    timerUrl: String(config.timerUrl || ""),
    profileId: String(config.profileId || "")
  };
}

function pickSecrets(config = {}) {
  return {
    token: String(config.token || ""),
    timerToken: String(config.timerToken || "")
  };
}

export function createSecureConfigStore({ secureStore = null, storage = globalThis.localStorage } = {}) {
  async function readNative() {
    if (!secureStore?.get) return {};
    const result = await secureStore.get({ key: SECRET_CONFIG_KEY });
    return result?.value ? JSON.parse(result.value) : {};
  }

  return {
    async load() {
      const publicConfig = JSON.parse(storage?.getItem(PUBLIC_CONFIG_KEY) || "{}");
      try {
        return { ...publicConfig, ...await readNative(), secureAvailable: Boolean(secureStore?.get) };
      } catch (_error) {
        return { ...publicConfig, secureAvailable: false };
      }
    },
    async save(config) {
      storage?.setItem(PUBLIC_CONFIG_KEY, JSON.stringify(pickPublic(config)));
      if (!secureStore?.set) {
        if (config.token || config.timerToken) throw new Error("当前环境没有原生安全存储，不能保存访问密钥。");
        return { secureAvailable: false };
      }
      await secureStore.set({ key: SECRET_CONFIG_KEY, value: JSON.stringify(pickSecrets(config)) });
      return { secureAvailable: true };
    },
    async clearSecrets() {
      if (secureStore?.remove) await secureStore.remove({ key: SECRET_CONFIG_KEY });
    }
  };
}
