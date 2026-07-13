/* Keyxif Web — db.js
 * Persistence layer: IndexedDB `keyxif` v1 (stores: sources, exported) + localStorage JSON helpers.
 * Contract: data/contracts.md § window.KeyxifDB. Dependency-free, plain script (no modules).
 */
(function () {
  'use strict';

  var DB_NAME = 'keyxif';
  var DB_VERSION = 1;
  var STORE_SOURCES = 'sources';
  var STORE_EXPORTED = 'exported';

  var dbInstance = null;
  var openPromise = null;

  // ---- open / init -------------------------------------------------------

  function init() {
    if (openPromise) return openPromise;
    openPromise = new Promise(function (resolve, reject) {
      var req;
      try {
        req = window.indexedDB.open(DB_NAME, DB_VERSION);
      } catch (e) {
        openPromise = null;
        reject(e);
        return;
      }
      req.onupgradeneeded = function () {
        var db = req.result;
        if (!db.objectStoreNames.contains(STORE_SOURCES)) {
          db.createObjectStore(STORE_SOURCES, { keyPath: 'id' });
        }
        if (!db.objectStoreNames.contains(STORE_EXPORTED)) {
          db.createObjectStore(STORE_EXPORTED, { keyPath: 'id' });
        }
      };
      req.onsuccess = function () {
        dbInstance = req.result;
        resolve();
      };
      req.onerror = function () {
        openPromise = null; // allow a later init() retry
        reject(req.error || new Error('KeyxifDB: failed to open IndexedDB "' + DB_NAME + '"'));
      };
    });
    return openPromise;
  }

  function withDB() {
    if (!openPromise) {
      return Promise.reject(new Error('KeyxifDB: init() must be called (and awaited) before using IndexedDB methods.'));
    }
    return openPromise.then(function () { return dbInstance; });
  }

  // Run one request inside a transaction; resolve with the request result
  // once the transaction completes (safe for both reads and writes).
  function run(storeName, mode, fn) {
    return withDB().then(function (db) {
      return new Promise(function (resolve, reject) {
        var tx, req, result;
        try {
          tx = db.transaction(storeName, mode);
          req = fn(tx.objectStore(storeName));
        } catch (e) {
          reject(e);
          return;
        }
        req.onsuccess = function () { result = req.result; };
        req.onerror = function () { reject(req.error || new Error('KeyxifDB: request failed on "' + storeName + '"')); };
        tx.oncomplete = function () { resolve(result); };
        tx.onabort = function () { reject(tx.error || new Error('KeyxifDB: transaction aborted on "' + storeName + '"')); };
      });
    });
  }

  // ---- sources -----------------------------------------------------------

  function putSource(record) {
    return run(STORE_SOURCES, 'readwrite', function (s) { return s.put(record); }).then(function () {});
  }

  function getSource(id) {
    return run(STORE_SOURCES, 'readonly', function (s) { return s.get(id); }).then(function (r) {
      return r === undefined ? null : r;
    });
  }

  function deleteSource(id) {
    return run(STORE_SOURCES, 'readwrite', function (s) { return s['delete'](id); }).then(function () {});
  }

  function listSourceIds() {
    return run(STORE_SOURCES, 'readonly', function (s) { return s.getAllKeys(); }).then(function (keys) {
      return keys || [];
    });
  }

  function hasSource(id) {
    return run(STORE_SOURCES, 'readonly', function (s) { return s.getKey(id); }).then(function (key) {
      return key !== undefined;
    });
  }

  // ---- exported ----------------------------------------------------------

  function putExported(record) {
    return run(STORE_EXPORTED, 'readwrite', function (s) { return s.put(record); }).then(function () {});
  }

  function getExported(id) {
    return run(STORE_EXPORTED, 'readonly', function (s) { return s.get(id); }).then(function (r) {
      return r === undefined ? null : r;
    });
  }

  function listExported() {
    return run(STORE_EXPORTED, 'readonly', function (s) { return s.getAll(); }).then(function (list) {
      list = list || [];
      list.sort(function (a, b) { return (b.createdAt || 0) - (a.createdAt || 0); }); // createdAt desc
      return list;
    });
  }

  function deleteExported(id) {
    return run(STORE_EXPORTED, 'readwrite', function (s) { return s['delete'](id); }).then(function () {});
  }

  function clearExported() {
    return run(STORE_EXPORTED, 'readwrite', function (s) { return s.clear(); }).then(function () {});
  }

  function hasExportedBlob(id) {
    return getExported(id).then(function (record) {
      return !!(record &&
        typeof Blob !== 'undefined' &&
        record.blob instanceof Blob &&
        record.blob.size > 0);
    });
  }

  // ---- localStorage JSON helpers ------------------------------------------

  function loadJSON(key) {
    try {
      var raw = window.localStorage.getItem(key);
      if (raw === null) return null;
      return JSON.parse(raw);
    } catch (e) {
      return null;
    }
  }

  function saveJSON(key, value) {
    try {
      window.localStorage.setItem(key, JSON.stringify(value));
      return true;
    } catch (e) {
      return false; // quota exceeded, private mode, serialization failure, etc.
    }
  }

  function removeKey(key) {
    try {
      window.localStorage.removeItem(key);
    } catch (e) { /* ignore */ }
  }

  // ---- export ------------------------------------------------------------

  window.KeyxifDB = {
    init: init,
    putSource: putSource,
    getSource: getSource,
    deleteSource: deleteSource,
    listSourceIds: listSourceIds,
    hasSource: hasSource,
    putExported: putExported,
    getExported: getExported,
    listExported: listExported,
    deleteExported: deleteExported,
    clearExported: clearExported,
    hasExportedBlob: hasExportedBlob,
    loadJSON: loadJSON,
    saveJSON: saveJSON,
    removeKey: removeKey
  };
})();
