// Unit tests for push-token selection and dead-token detection (finding F9).
const test = require("node:test");
const assert = require("node:assert/strict");
const {pickToken, isDeadTokenError} = require("../pushTokens");

test("the private token wins over the legacy public one", () => {
  assert.deepEqual(pickToken({fcmToken: "new"}, {fcmToken: "old"}), {token: "new", source: "private"});
});

test("falls back to the legacy public token until the phone updates", () => {
  assert.deepEqual(pickToken(undefined, {fcmToken: "old"}), {token: "old", source: "legacy"});
  assert.deepEqual(pickToken({}, {fcmToken: "old"}), {token: "old", source: "legacy"});
});

test("no token anywhere, or a non-string, means none", () => {
  assert.deepEqual(pickToken(undefined, undefined), {token: "", source: "none"});
  assert.deepEqual(pickToken({fcmToken: 42}, {fcmToken: {}}), {token: "", source: "none"});
});

test("only permanent FCM errors count as a dead token", () => {
  assert.equal(isDeadTokenError({code: "messaging/registration-token-not-registered"}), true);
  assert.equal(isDeadTokenError({code: "messaging/invalid-registration-token"}), true);
  assert.equal(isDeadTokenError({code: "messaging/internal-error"}), false);
  assert.equal(isDeadTokenError({code: "messaging/quota-exceeded"}), false);
  assert.equal(isDeadTokenError(undefined), false);
});

// ---- readPushToken / forgetDeadToken against a small in-memory fake of Firestore ----
const {readPushToken, forgetDeadToken, FAILED_PRECONDITION} = require("../pushTokens");
const DELETE = Symbol("delete");

/** A document with an updateTime that honours lastUpdateTime preconditions. */
function fakeDoc(initial) {
  const state = {data: initial, version: initial ? 1 : 0};
  // Like real Firestore, a snapshot freezes the data and version at the time of the read.
  const snapshot = () => {
    const data = state.data === undefined ? undefined : {...state.data};
    return {exists: data !== undefined, data: () => data, updateTime: state.version};
  };
  const checkPrecondition = (opts) => {
    if (opts && opts.lastUpdateTime !== undefined && opts.lastUpdateTime !== state.version) {
      const e = new Error("FAILED_PRECONDITION"); e.code = FAILED_PRECONDITION; throw e;
    }
  };
  return {
    state,
    get: async () => snapshot(),
    delete: async (opts) => { checkPrecondition(opts); state.data = undefined; state.version++; },
    update: async (fields, opts) => {
      checkPrecondition(opts);
      const next = {...state.data};
      for (const [k, v] of Object.entries(fields)) { if (v === DELETE) delete next[k]; else next[k] = v; }
      state.data = next; state.version++;
    },
    // A phone writing a new value (bumps the version, like a real write)
    write: (data) => { state.data = data; state.version++; },
  };
}
function fakeUser(profile, privatePush) {
  const profileDoc = fakeDoc(profile);
  const pushDoc = fakeDoc(privatePush);
  const userRef = {...profileDoc, collection: () => ({doc: () => pushDoc})};
  return {userRef, profileDoc, pushDoc};
}

test("readPushToken prefers the private document, then the legacy field", async () => {
  const a = fakeUser({fcmToken: "old"}, {fcmToken: "new"});
  assert.equal((await readPushToken(a.userRef, await a.userRef.get())).token, "new");
  const b = fakeUser({fcmToken: "old"}, undefined);
  assert.deepEqual(await readPushToken(b.userRef, await b.userRef.get()), {token: "old", source: "legacy"});
});

test("forgetDeadToken removes the dead token from both places", async () => {
  const u = fakeUser({username: "a", fcmToken: "dead"}, {fcmToken: "dead"});
  assert.deepEqual(await forgetDeadToken(u.userRef, "dead", DELETE), {private: true, legacy: true});
  assert.equal(u.pushDoc.state.data, undefined);
  assert.deepEqual(u.profileDoc.state.data, {username: "a"});
});

test("forgetDeadToken leaves a different (newer) token alone", async () => {
  const u = fakeUser({username: "a"}, {fcmToken: "fresh"});
  assert.deepEqual(await forgetDeadToken(u.userRef, "dead", DELETE), {private: false, legacy: false});
  assert.equal(u.pushDoc.state.data.fcmToken, "fresh");
});

test("race: a fresh token written between the read and the delete is kept", async () => {
  const u = fakeUser({username: "a"}, {fcmToken: "dead"});
  const realGet = u.pushDoc.get;
  // The phone saves its new token right after the function reads the old one.
  u.pushDoc.get = async () => { const snap = await realGet(); u.pushDoc.write({fcmToken: "fresh"}); return snap; };
  assert.deepEqual(await forgetDeadToken(u.userRef, "dead", DELETE), {private: false, legacy: false});
  assert.equal(u.pushDoc.state.data.fcmToken, "fresh");
});

test("other errors from the delete are not swallowed", async () => {
  const u = fakeUser({username: "a"}, {fcmToken: "dead"});
  u.pushDoc.delete = async () => { const e = new Error("unavailable"); e.code = 14; throw e; };
  await assert.rejects(forgetDeadToken(u.userRef, "dead", DELETE), /unavailable/);
});
