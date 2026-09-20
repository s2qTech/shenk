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
    const legacyCheckpointKey = String(options.legacyCheckpointKey || "legacy-snapshot-checkpoint-v2");
    let db = null;
    let recordBaseline = null;
    let persistTail = Promise.resolve();

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

    async function updateOutbox(keys, update) {
      const database = await open();
      if (!database) return false;
      return new Promise((resolve, reject) => {
        const tx = database.transaction(outboxStoreName, "readwrite");
        const store = tx.objectStore(outboxStoreName);
        [...new Set(keys || [])].forEach(key => {
          const request = store.get(key);
          request.onsuccess = () => { if (request.result) store.put(update(request.result)); };
        });
        tx.oncomplete = () => resolve(true);
        tx.onabort = tx.onerror = () => reject(tx.error || new Error("IndexedDB outbox update failed"));
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

    async function getMetaValue(key) {
      const row = await getOne(metaStoreName, key);
      return row?.value ?? null;
    }

    async function setMetaValue(key, value) {
      return putOne(metaStoreName, { key, value: clone(value) });
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
      if (rows) recordBaseline = new Map(rows.map(row => [row.key, JSON.stringify(row)]));
      return rows === null ? null : rows.map((row) => clone(row.envelope)).filter(Boolean);
    }

    async function loadOutbox() {
      const rows = await getAll(outboxStoreName);
      return rows === null ? null : rows.map(clone);
    }

    function persist(records, outboxEntries, metadata = {}) {
      const rows = toRecordRows(records);
      const entries = clone(outboxEntries || []);
      const task = persistTail.then(() => persistRows(rows, entries, metadata));
      persistTail = task.catch(() => {});
      return task;
    }

    async function persistRows(rows, entries, metadata) {
      const database = await open();
      if (!database) return { available: false, outbox: null };
      if (!recordBaseline) await loadRecords();
      const changed = rows.filter(row => recordBaseline.get(row.key) !== JSON.stringify(row));
      return new Promise((resolve, reject) => {
        const tx = database.transaction([recordStoreName, outboxStoreName, metaStoreName], "readwrite");
        const recordStore = tx.objectStore(recordStoreName);
        let failure = null;
        let nextOutbox = [];
        let outboxWritten = 0;
        let outboxRemoved = 0;
        changed.forEach(row => {
          const request = recordStore.get(row.key);
          request.onsuccess = () => {
            const current = request.result;
            // Another tab may have committed since this page loaded. Never
            // replace that value from a stale in-memory snapshot.
            if (current && JSON.stringify(current) !== recordBaseline.get(row.key) && JSON.stringify(current) !== JSON.stringify(row)) {
              failure = new Error("entity_store_changed_in_another_tab");
              tx.abort();
              return;
            }
            recordStore.put(row);
          };
        });
        const outboxStore = tx.objectStore(outboxStoreName);
        const outboxRequest = outboxStore.getAll();
        outboxRequest.onsuccess = () => {
          const current = outboxRequest.result || [];
          // Change only operations belonging to records changed by this save.
          const changedKeys = new Set(changed.map(row => row.key));
          const byKey = new Map(current.map(row => [row.key, row]));
          const entryKeys = new Set(entries.map(row => row.key));
          toOutboxRows(entries.filter(row => changedKeys.has(row.key) || !byKey.has(row.key)), current).forEach(row => {
            outboxStore.put(row);
            byKey.set(row.key, row);
            outboxWritten++;
          });
          changedKeys.forEach(key => {
            if (!entryKeys.has(key) && byKey.has(key)) {
              outboxStore.delete(key);
              byKey.delete(key);
              outboxRemoved++;
            }
          });
          nextOutbox = [...byKey.values()];
        };
        Object.entries(metadata).forEach(([key, value]) => tx.objectStore(metaStoreName).put({ key, value: clone(value) }));
        tx.oncomplete = () => {
          changed.forEach(row => recordBaseline.set(row.key, JSON.stringify(row)));
          resolve({ available: true, recordResult: { available: true, written: changed.length, removed: 0 }, outboxResult: { available: true, written: outboxWritten, removed: outboxRemoved }, outbox: nextOutbox });
        };
        tx.onabort = tx.onerror = () => reject(failure || tx.error || new Error("IndexedDB transaction failed"));
      });
    }

    async function initializeFromSnapshot(snapshotRecords, outboxEntries, backupPayload) {
      const database = await open();
      if (!database) return { available: false, migrated: false, records: null, outbox: null };
      const marker = await getOne(metaStoreName, migrationKey);
      if (!marker) {
        const backup = {
          createdAt: new Date().toISOString(),
          snapshot: clone(backupPayload || { records: snapshotRecords || [] })
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
        outbox: await loadOutbox(),
        legacyCheckpoint: await getMetaValue(legacyCheckpointKey)
      };
    }

    async function recordFailure(keys, message, nextAttemptAt) {
      return updateOutbox(keys, row => ({
          ...row,
          attempts: Number(row.attempts || 0) + 1,
          lastError: String(message || "sync_failed"),
          lastAttemptAt: new Date().toISOString(),
          nextAttemptAt: nextAttemptAt || "",
          updatedAt: new Date().toISOString()
      }));
    }

    async function scheduleRetry(keys, nextAttemptAt) {
      return updateOutbox(keys, row => ({
          ...row,
          nextAttemptAt: nextAttemptAt || "",
          updatedAt: new Date().toISOString()
      }));
    }

    return {
      initializeFromSnapshot,
      loadRecords,
      loadOutbox,
      persist,
      recordFailure,
      scheduleRetry,
      getMetaValue,
      setMetaValue
    };
  }

  global.ShenkeEntityStore = { create };
})(typeof window !== "undefined" ? window : globalThis);
