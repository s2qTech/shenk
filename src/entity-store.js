(function (global) {
  "use strict";

  function create(options = {}) {
    const browser = options.window || global;
    const dbName = String(options.dbName || "shenke");
    const dbVersion = Number(options.dbVersion || 2);
    const recordStoreName = String(options.recordStoreName || "records");
    const outboxStoreName = String(options.outboxStoreName || "outbox");
    const metaStoreName = String(options.metaStoreName || "meta");
    const backupKeyPrefix = String(options.backupKeyPrefix || "shenke:migration-backup:");
    const migrationKey = String(options.migrationKey || "entity-store-v2");
    let db = null;

    function clone(value) {
      return JSON.parse(JSON.stringify(value));
    }

    function open() {
      if (db) return Promise.resolve(db);
      return new Promise((resolve, reject) => {
        if (!("indexedDB" in browser)) {
          resolve(null);
          return;
        }
        const request = browser.indexedDB.open(dbName, dbVersion);
        request.onupgradeneeded = () => {
          const nextDb = request.result;
          [recordStoreName, outboxStoreName, metaStoreName].forEach((name) => {
            if (!nextDb.objectStoreNames.contains(name)) nextDb.createObjectStore(name, { keyPath: "key" });
          });
        };
        request.onsuccess = () => {
          db = request.result;
          resolve(db);
        };
        request.onerror = () => reject(request.error || new Error("IndexedDB open failed"));
      });
    }

    async function getAll(storeName) {
      const database = await open();
      if (!database) return null;
      return new Promise((resolve, reject) => {
        const request = database.transaction(storeName, "readonly").objectStore(storeName).getAll();
        request.onsuccess = () => resolve(Array.isArray(request.result) ? request.result : []);
        request.onerror = () => reject(request.error || new Error(`IndexedDB read failed: ${storeName}`));
      });
    }

    async function getOne(storeName, key) {
      const database = await open();
      if (!database) return null;
      return new Promise((resolve, reject) => {
        const request = database.transaction(storeName, "readonly").objectStore(storeName).get(key);
        request.onsuccess = () => resolve(request.result || null);
        request.onerror = () => reject(request.error || new Error(`IndexedDB read failed: ${storeName}`));
      });
    }

    async function replaceRows(storeName, rows) {
      const database = await open();
      if (!database) return { available: false, written: 0, removed: 0 };
      const current = await getAll(storeName);
      const currentByKey = new Map((current || []).map((row) => [row.key, row]));
      const nextByKey = new Map((rows || []).map((row) => [row.key, row]));
      const writes = [];
      const deletes = [];

      nextByKey.forEach((row, key) => {
        if (JSON.stringify(currentByKey.get(key)) !== JSON.stringify(row)) writes.push(row);
      });
      currentByKey.forEach((_row, key) => {
        if (!nextByKey.has(key)) deletes.push(key);
      });
      if (!writes.length && !deletes.length) return { available: true, written: 0, removed: 0 };

      return new Promise((resolve, reject) => {
        const tx = database.transaction(storeName, "readwrite");
        const store = tx.objectStore(storeName);
        writes.forEach((row) => store.put(row));
        deletes.forEach((key) => store.delete(key));
        tx.oncomplete = () => resolve({ available: true, written: writes.length, removed: deletes.length });
        tx.onerror = () => reject(tx.error || new Error(`IndexedDB write failed: ${storeName}`));
      });
    }

    async function putOne(storeName, row) {
      const database = await open();
      if (!database) return false;
      return new Promise((resolve, reject) => {
        const tx = database.transaction(storeName, "readwrite");
        tx.objectStore(storeName).put(row);
        tx.oncomplete = () => resolve(true);
        tx.onerror = () => reject(tx.error || new Error(`IndexedDB write failed: ${storeName}`));
      });
    }

    function toRecordRows(records) {
      return (records || []).filter((record) => record?.entity && record?.id).map((record) => ({
        key: `${record.entity}:${record.id}`,
        entity: record.entity,
        id: record.id,
        envelope: clone(record)
      }));
    }

    function toOutboxRows(entries, existingRows = []) {
      const existing = new Map((existingRows || []).map((row) => [row.key, row]));
      return (entries || []).filter((entry) => entry?.key).map((entry) => {
        const previous = existing.get(entry.key);
        const next = {
          ...previous,
          ...clone(entry),
          attempts: Number(entry.attempts ?? previous?.attempts ?? 0),
          createdAt: entry.createdAt || previous?.createdAt || new Date().toISOString()
        };
        const comparable = (row) => {
          const value = { ...(row || {}) };
          delete value.updatedAt;
          return JSON.stringify(value);
        };
        return {
          ...next,
          updatedAt: previous && comparable(previous) === comparable(next)
            ? previous.updatedAt
            : new Date().toISOString()
        };
      });
    }

    async function loadRecords() {
      const rows = await getAll(recordStoreName);
      return rows === null ? null : rows.map((row) => clone(row.envelope)).filter(Boolean);
    }

    async function loadOutbox() {
      const rows = await getAll(outboxStoreName);
      return rows === null ? null : rows.map(clone);
    }

    async function persist(records, outboxEntries) {
      const outboxRows = await getAll(outboxStoreName);
      const recordResult = await replaceRows(recordStoreName, toRecordRows(records));
      const outboxResult = await replaceRows(outboxStoreName, toOutboxRows(outboxEntries, outboxRows || []));
      return { available: recordResult.available, recordResult, outboxResult };
    }

    async function initializeFromSnapshot(snapshotRecords, outboxEntries) {
      const database = await open();
      if (!database) return { available: false, migrated: false, records: null, outbox: null };
      const marker = await getOne(metaStoreName, migrationKey);
      if (!marker) {
        const backup = {
          createdAt: new Date().toISOString(),
          records: clone(snapshotRecords || [])
        };
        try {
          browser.localStorage?.setItem(`${backupKeyPrefix}${backup.createdAt}`, JSON.stringify(backup));
        } catch (error) {
          // The old snapshot remains untouched in its original store.
        }
        await putOne(metaStoreName, { key: `${migrationKey}:backup`, value: backup });
        await persist(snapshotRecords || [], outboxEntries || []);
        await putOne(metaStoreName, { key: migrationKey, value: { migratedAt: backup.createdAt, version: 2 } });
      }
      return {
        available: true,
        migrated: !marker,
        records: await loadRecords(),
        outbox: await loadOutbox()
      };
    }

    async function recordFailure(keys, message, nextAttemptAt) {
      const rows = await getAll(outboxStoreName);
      if (rows === null) return false;
      const target = new Set(keys || []);
      const next = rows.map((row) => {
        if (!target.has(row.key)) return row;
        return {
          ...row,
          attempts: Number(row.attempts || 0) + 1,
          lastError: String(message || "sync_failed"),
          lastAttemptAt: new Date().toISOString(),
          nextAttemptAt: nextAttemptAt || "",
          updatedAt: new Date().toISOString()
        };
      });
      await replaceRows(outboxStoreName, next);
      return true;
    }

    return { initializeFromSnapshot, loadRecords, loadOutbox, persist, recordFailure };
  }

  global.ShenkeEntityStore = { create };
})(typeof window !== "undefined" ? window : globalThis);
