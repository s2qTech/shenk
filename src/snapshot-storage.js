(function (global) {
  "use strict";

  function create(options = {}) {
    const browser = options.window || global;
    const dbName = String(options.dbName || "shenke");
    const dbVersion = Number(options.dbVersion || 1);
    const storeName = String(options.storeName || "kv");
    const snapshotKey = String(options.snapshotKey || "snapshot");
    const fallbackKey = String(options.fallbackKey || "shenke:snapshot");
    const extraStores = Array.isArray(options.extraStores) ? options.extraStores.map(String).filter(Boolean) : [];
    let db = null;

    function openDatabase() {
      return new Promise((resolve, reject) => {
        if (!("indexedDB" in browser)) {
          reject(new Error("IndexedDB unavailable"));
          return;
        }
        const request = browser.indexedDB.open(dbName, dbVersion);
        request.onupgradeneeded = () => {
          const nextDb = request.result;
          if (!nextDb.objectStoreNames.contains(storeName)) nextDb.createObjectStore(storeName);
          extraStores.forEach((name) => {
            if (!nextDb.objectStoreNames.contains(name)) nextDb.createObjectStore(name, { keyPath: "key" });
          });
        };
        request.onsuccess = () => resolve(request.result);
        request.onerror = () => reject(request.error || new Error("IndexedDB open failed"));
      });
    }

    function get(key) {
      return new Promise((resolve, reject) => {
        const tx = db.transaction(storeName, "readonly");
        const request = tx.objectStore(storeName).get(key);
        request.onsuccess = () => resolve(request.result || null);
        request.onerror = () => reject(request.error || new Error("IndexedDB read failed"));
      });
    }

    function set(key, value) {
      return new Promise((resolve, reject) => {
        const tx = db.transaction(storeName, "readwrite");
        tx.objectStore(storeName).put(value, key);
        tx.oncomplete = () => resolve();
        tx.onerror = () => reject(tx.error || new Error("IndexedDB write failed"));
      });
    }

    async function load() {
      let mode = "IndexedDB";
      try {
        db = await openDatabase();
        const snapshot = await get(snapshotKey);
        if (snapshot) return { snapshot, mode };
      } catch (error) {
        db = null;
        mode = "localStorage";
      }

      try {
        const raw = browser.localStorage.getItem(fallbackKey);
        return { snapshot: raw ? JSON.parse(raw) : null, mode };
      } catch (error) {
        return { snapshot: null, mode, fallbackReadError: error };
      }
    }

    async function save(snapshot) {
      try {
        if (db) {
          await set(snapshotKey, snapshot);
          return { mode: "IndexedDB" };
        }
        browser.localStorage.setItem(fallbackKey, JSON.stringify(snapshot));
        return { mode: "localStorage" };
      } catch (error) {
        browser.localStorage.setItem(fallbackKey, JSON.stringify(snapshot));
        return { mode: "localStorage", fallbackWriteError: error };
      }
    }

    return { load, save };
  }

  global.ShenkeSnapshotStorage = { create };
})(typeof window !== "undefined" ? window : globalThis);
