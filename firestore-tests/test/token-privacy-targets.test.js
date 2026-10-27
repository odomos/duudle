// Phase 2 (audit finding F9): a device's push token is private. It lives in the
// owner-only users/{uid}/private/push document, never on the world-readable profile.
// Written as a Phase 1 target while it still failed; part of the must-pass suite now.
const { assertFails, assertSucceeds } = require("@firebase/rules-unit-testing");
const {
  doc, getDoc, setDoc, updateDoc, deleteDoc, getDocs, collection, collectionGroup, query, serverTimestamp,
} = require("firebase/firestore");
const { createEnv, seed, ALICE, CAROL } = require("./helpers");

describe("Phase 2: push token privacy", function () {
  let env;
  const as = (uid) => env.authenticatedContext(uid).firestore();
  before(async () => { env = await createEnv(); });
  after(async () => { await env.cleanup(); });
  beforeEach(async () => {
    await env.clearFirestore();
    await seed(env, async (db) => {
      await setDoc(doc(db, "users/alice"), { uid: ALICE, username: "alice", usernameLower: "alice" });
      await setDoc(doc(db, "users/alice/private/push"), { fcmToken: "alice-token", updatedAt: new Date() });
    });
  });

  it("the push token isn't stored on the public profile", async () => {
    await assertFails(setDoc(doc(as(ALICE), "users/alice"),
      { uid: ALICE, username: "Alice", usernameLower: "alice", fcmToken: "token-value" }));
  });
  it("an existing profile can't gain a public token", async () => {
    await assertFails(updateDoc(doc(as(ALICE), "users/alice"), { fcmToken: "token-value" }));
  });
  it("nobody else can read your private token", async () => {
    await assertFails(getDoc(doc(as(CAROL), "users/alice/private/push")));
    await assertFails(getDocs(collection(as(CAROL), "users/alice/private")));
  });
  it("nobody else can overwrite your private token", async () => {
    await assertFails(setDoc(doc(as(CAROL), "users/alice/private/push"),
      { fcmToken: "carol-token", updatedAt: serverTimestamp() }));
  });
  it("only the 'push' document, only known fields, only server time", async () => {
    await assertFails(setDoc(doc(as(ALICE), "users/alice/private/other"),
      { fcmToken: "t", updatedAt: serverTimestamp() }));
    await assertFails(setDoc(doc(as(ALICE), "users/alice/private/push"),
      { fcmToken: "t", updatedAt: serverTimestamp(), extra: true }));
    await assertFails(setDoc(doc(as(ALICE), "users/alice/private/push"),
      { fcmToken: "t", updatedAt: new Date(0) }));
    await assertFails(setDoc(doc(as(ALICE), "users/alice/private/push"),
      { fcmToken: 42, updatedAt: serverTimestamp() }));
  });
  it("nobody else can delete your private token", async () => {
    await assertFails(deleteDoc(doc(as(CAROL), "users/alice/private/push")));
  });
  it("a collection-group query across everyone's private documents is denied, even for an owner", async () => {
    await assertFails(getDocs(query(collectionGroup(as(CAROL), "private"))));
    await assertFails(getDocs(query(collectionGroup(as(ALICE), "private"))));
  });
  it("rejects a token over 4096 characters", async () => {
    await assertFails(setDoc(doc(as(ALICE), "users/alice/private/push"),
      { fcmToken: "x".repeat(4097), updatedAt: serverTimestamp() }));
    await assertSucceeds(setDoc(doc(as(ALICE), "users/alice/private/push"),
      { fcmToken: "x".repeat(4096), updatedAt: serverTimestamp() }));
  });
  it("the Phase 1 app's token refresh (public field old -> new) is denied", async () => {
    await seed(env, (db) => setDoc(doc(db, "users/alice"),
      { uid: ALICE, username: "alice", usernameLower: "alice", fcmToken: "old" }));
    await assertFails(updateDoc(doc(as(ALICE), "users/alice"), { fcmToken: "new" }));
  });
  // KNOWN GAP (security review M3): profiles written before Phase 2 keep their public
  // token until that phone runs the new app or a one-off cleanup removes it. This test
  // documents the gap; flip it to assertFails-style checks once the cleanup has run.
  it("known gap: a leftover public token on an old profile is still readable", async () => {
    await seed(env, (db) => setDoc(doc(db, "users/bob"),
      { uid: "bob", username: "bob", usernameLower: "bob", fcmToken: "bob-old-token" }));
    const snap = await getDoc(doc(as(CAROL), "users/bob"));
    if (snap.data().fcmToken !== "bob-old-token") throw new Error("gap closed: update this test");
  });
  it("the owner can read their own token", async () => {
    await assertSucceeds(getDoc(doc(as(ALICE), "users/alice/private/push")));
  });
});
