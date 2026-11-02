// Smoke test for the real functions entry point: loading index.js must initialise the
// Admin app and export every function. (Phase 2 shipped to staging with initializeApp()
// accidentally removed: every helper test passed, but every push crashed at runtime.)
const test = require("node:test");
const assert = require("node:assert/strict");

test("index.js initialises Firebase Admin and exports every function", () => {
  const fns = require("../index");
  const {getApps} = require("firebase-admin/app");
  assert.equal(getApps().length, 1, "initializeApp() must be called when index.js loads");
  assert.equal(typeof fns.onDoodleCreated, "function");
  assert.equal(typeof fns.onChatMessageCreated, "function");
  assert.equal(typeof fns.deleteAccount, "function");
});
