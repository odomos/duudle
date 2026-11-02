// End-to-end test of the deleteAccount function on the local emulators (no real project).
// Run: firebase emulators:exec --only auth,firestore,functions --project demo-duudle "npm --prefix functions run test:emulator"
const test = require("node:test");
const assert = require("node:assert/strict");
const {initializeApp} = require("firebase-admin/app");
const {getFirestore} = require("firebase-admin/firestore");

const PROJECT = "demo-duudle";
const AUTH = `http://${process.env.FIREBASE_AUTH_EMULATOR_HOST}`;
const FUNCTION_URL = `http://127.0.0.1:5001/${PROJECT}/us-central1/deleteAccount`;

initializeApp({projectId: PROJECT});
const db = getFirestore();

async function signUp() {
  const r = await fetch(`${AUTH}/identitytoolkit.googleapis.com/v1/accounts:signUp?key=fake`, {
    method: "POST", headers: {"Content-Type": "application/json"}, body: JSON.stringify({returnSecureToken: true}),
  });
  const j = await r.json();
  return {uid: j.localId, idToken: j.idToken};
}

async function userExists(uid) {
  const r = await fetch(`${AUTH}/identitytoolkit.googleapis.com/v1/accounts:lookup?key=fake`, {
    method: "POST", headers: {"Content-Type": "application/json", "Authorization": "Bearer owner"}, body: JSON.stringify({localId: [uid]}),
  });
  return ((await r.json()).users || []).length > 0;
}

const callDelete = (headers) => fetch(FUNCTION_URL, {method: "POST", headers});
const exists = async (path) => (await db.doc(path).get()).exists;

test("deletes the caller's data and account, keeps reported evidence and other people's data", async () => {
  const me = await signUp();
  const friend = await signUp();
  const other = await signUp();
  const [a, b] = [me.uid, friend.uid].sort();
  const chat = `${a}_${b}`;
  const reportedChat = [me.uid, other.uid].sort().join("_");

  await Promise.all([
    db.doc(`users/${me.uid}`).set({uid: me.uid, username: "me"}),
    db.doc(`users/${me.uid}/mutes/${friend.uid}`).set({mutedAt: 1}),
    db.doc(`users/${me.uid}/private/push`).set({fcmToken: "t"}),
    db.doc(`users/${friend.uid}`).set({uid: friend.uid, username: "friend"}),
    db.doc("friendships/f1").set({userA: friend.uid, userB: me.uid, createdAt: 1, requestId: "r0"}),
    db.doc("friendships/f2").set({userA: friend.uid, userB: other.uid, createdAt: 1, requestId: "r9"}),
    db.doc("friend_requests/r1").set({fromUid: me.uid, toUid: other.uid}),
    db.doc("friend_requests/r2").set({fromUid: other.uid, toUid: me.uid}),
    db.doc("doodles/sent").set({senderId: me.uid, receiverId: friend.uid}),
    db.doc("doodles/got").set({senderId: friend.uid, receiverId: me.uid}),
    db.doc("doodles/evidence").set({senderId: me.uid, receiverId: other.uid}),
    db.doc("doodles/unrelated").set({senderId: friend.uid, receiverId: other.uid}),
    db.doc(`chats/${chat}`).set({members: [a, b]}),
    db.doc(`chats/${chat}/messages/m1`).set({senderId: me.uid, text: "hi"}),
    db.doc(`chats/${reportedChat}`).set({members: [me.uid, other.uid].sort()}),
    db.doc(`chats/${reportedChat}/messages/m1`).set({senderId: me.uid, text: "bad"}),
    db.doc("reports/x1").set({reporterId: other.uid, reportedUid: me.uid, contentType: "doodle", contentId: "evidence", status: "open"}),
    db.doc("reports/x2").set({reporterId: other.uid, reportedUid: me.uid, contentType: "chat", contentId: reportedChat, status: "open"}),
  ]);

  assert.equal((await callDelete({})).status, 401, "no token");
  assert.equal((await callDelete({Authorization: "Bearer not-a-token"})).status, 401, "bad token");
  assert.ok(await exists(`users/${me.uid}`), "nothing deleted without a valid token");

  const res = await callDelete({Authorization: `Bearer ${me.idToken}`});
  assert.equal(res.status, 200);

  for (const path of [`users/${me.uid}`, `users/${me.uid}/mutes/${friend.uid}`, `users/${me.uid}/private/push`,
    "friendships/f1", "friend_requests/r1", "friend_requests/r2", "doodles/sent", "doodles/got",
    `chats/${chat}`, `chats/${chat}/messages/m1`]) {
    assert.equal(await exists(path), false, `${path} should be deleted`);
  }
  for (const path of ["doodles/evidence", `chats/${reportedChat}/messages/m1`, "reports/x1", "reports/x2",
    `users/${friend.uid}`, "friendships/f2", "doodles/unrelated"]) {
    assert.equal(await exists(path), true, `${path} should be kept`);
  }
  assert.equal(await userExists(me.uid), false, "auth account deleted");
  assert.equal(await userExists(friend.uid), true, "other accounts untouched");

  // Running again (the app retried after a timeout) is harmless: the token of a deleted
  // account is refused (401), and the app then checks whether its account still exists.
  const retry = await callDelete({Authorization: `Bearer ${me.idToken}`});
  assert.ok([200, 401].includes(retry.status), `retry status ${retry.status}`);
  assert.equal(await exists(`users/${me.uid}`), false);
});
