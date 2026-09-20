// A transactional in-memory test double, including asynchronous request lifetimes.
function createIndexedDb() {
  const stores = new Map();
  let failNextWrite = false;
  const stats = { recordScans: 0 };
  const database = {
    objectStoreNames: { contains: name => stores.has(name) },
    createObjectStore(name) { stores.set(name, new Map()); },
    transaction(names, mode) {
      names = Array.isArray(names) ? names : [names];
      const working = new Map(names.map(name => [name, new Map(stores.get(name))]));
      let pending = 0, done = false, completion;
      const tx = {
        error: null,
        abort() {
          if (done) return;
          done = true;
          clearTimeout(completion);
          setTimeout(() => tx.onabort?.(), 0);
        },
        objectStore(name) {
          const rows = working.get(name);
          const request = action => {
            pending++;
            clearTimeout(completion);
            const req = {};
            setTimeout(() => {
              if (done) return;
              try {
                req.result = action();
                req.onsuccess?.();
              } catch (error) {
                tx.error = error;
                tx.abort();
              }
              pending--;
              finish();
            }, 0);
            return req;
          };
          return {
            getAll: () => request(() => { if (name === "records") stats.recordScans++; return [...rows.values()]; }),
            get: key => request(() => rows.get(key)),
            put: value => request(() => rows.set(value.key, structuredClone(value))),
            delete: key => request(() => rows.delete(key))
          };
        }
      };
      const finish = () => {
        if (pending || done) return;
        completion = setTimeout(() => {
          if (done || pending) return;
          if (mode === "readwrite" && failNextWrite) {
            failNextWrite = false;
            tx.error = new Error("synthetic commit failure");
            tx.abort();
            return;
          }
          done = true;
          if (mode === "readwrite") working.forEach((rows, name) => stores.set(name, rows));
          tx.oncomplete?.();
        }, 0);
      };
      finish();
      return tx;
    }
  };
  return {
    stats,
    failNextWrite() { failNextWrite = true; },
    open() {
      const request = { result: database };
      setTimeout(() => { request.onupgradeneeded?.(); request.onsuccess?.(); }, 0);
      return request;
    }
  };
}
module.exports = { createIndexedDb };
