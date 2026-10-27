// Phase 1 lockdown suite (audit findings F1, F2, F11, F24): what the rules must deny.
// Written in Phase 0 while every case still failed against the old rules. Against today's rules these are EXPECTED TO FAIL: each
// failure is evidence of a live access-control hole. Once Phase 1 lands, this file
// must pass and gets merged into the regression suite.
const { assertFails } = require("@firebase/rules-unit-testing");
const {
  doc, getDoc, setDoc, updateDoc, deleteDoc, collection, addDoc, getDocs, query, where, writeBatch,
} = require("firebase/firestore");
const { createEnv, seed, seedUsers, doodleDoc, requestDoc, ALICE, BOB, CAROL } = require("./helpers");

describe("Phase 1 lockdown: must deny", function () {
  let env;
  const as = (uid) => env.authenticatedContext(uid).firestore();

  before(async () => { env = await createEnv(); });
  after(async () => { await env.cleanup(); });
  beforeEach(async () => {
    await env.clearFirestore();
    await seedUsers(env);
    await seed(env, async (db) => {
      await setDoc(doc(db, "doodles/d1"), doodleDoc(ALICE, BOB));
      await setDoc(doc(db, "friend_requests/r1"), requestDoc(ALICE, BOB));
      await setDoc(doc(db, "friendships/f1"), { userA: BOB, userB: ALICE, createdAt: 1 });
    });
  });

  describe("F1: doodles", () => {
    it("a stranger can't read someone else's doodle", async () => {
      await assertFails(getDoc(doc(as(CAROL), "doodles/d1")));
    });
    it("a stranger can't list every doodle", async () => {
      await assertFails(getDocs(collection(as(CAROL), "doodles")));
    });
    it("a stranger can't query another user's received doodles", async () => {
      await assertFails(getDocs(query(collection(as(CAROL), "doodles"), where("receiverId", "==", BOB))));
    });
    it("nobody can send a doodle as someone else", async () => {
      await assertFails(addDoc(collection(as(CAROL), "doodles"), doodleDoc(ALICE, BOB)));
    });
    it("a stranger can't change a doodle's status", async () => {
      await assertFails(updateDoc(doc(as(CAROL), "doodles/d1"), { status: "removed" }));
    });
    it("a stranger can't delete a doodle", async () => {
      await assertFails(deleteDoc(doc(as(CAROL), "doodles/d1")));
    });
    it("the sender can't rewrite the drawing after sending", async () => {
      await assertFails(updateDoc(doc(as(ALICE), "doodles/d1"), { strokes: [] }));
    });
    it("the receiver can't change anything except status", async () => {
      await assertFails(updateDoc(doc(as(BOB), "doodles/d1"), { senderUsername: "someone else" }));
    });
    it("a doodle can't be created already 'removed' or with extra fields", async () => {
      await assertFails(addDoc(collection(as(ALICE), "doodles"), doodleDoc(ALICE, BOB, { status: "removed" })));
      await assertFails(addDoc(collection(as(ALICE), "doodles"), doodleDoc(ALICE, BOB, { isAdmin: true })));
    });
    it("rejects an unknown flash card id", async () => {
      await assertFails(addDoc(collection(as(ALICE), "doodles"),
        doodleDoc(ALICE, BOB, { strokes: [], cardId: "../../etc" })));
    });
    it("rejects an oversized drawing (more than 500 strokes)", async () => {
      const strokes = Array.from({ length: 501 }, () => ({ points: [{ x: 0, y: 0 }], color: 0, width: 4 }));
      await assertFails(addDoc(collection(as(ALICE), "doodles"), doodleDoc(ALICE, BOB, { strokes })));
    });
    it("can't send a doodle under someone else's name", async () => {
      await assertFails(addDoc(collection(as(CAROL), "doodles"), doodleDoc(CAROL, BOB, { senderUsername: "alice" })));
    });
    it("the receiver can only set status to 'removed'", async () => {
      await assertFails(updateDoc(doc(as(BOB), "doodles/d1"), { status: "seen" }));
    });
    it("the sender can't mark their own doodle removed", async () => {
      await assertFails(updateDoc(doc(as(ALICE), "doodles/d1"), { status: "removed" }));
    });
    it("can't send a doodle to yourself", async () => {
      await assertFails(addDoc(collection(as(ALICE), "doodles"), doodleDoc(ALICE, ALICE)));
    });
  });

  describe("F2: friend requests and friendships", () => {
    it("a stranger can't read a request between two other people", async () => {
      await assertFails(getDoc(doc(as(CAROL), "friend_requests/r1")));
    });
    it("nobody can send a request as someone else", async () => {
      await assertFails(addDoc(collection(as(CAROL), "friend_requests"), requestDoc(ALICE, BOB)));
    });
    it("a stranger can't delete someone else's request", async () => {
      await assertFails(deleteDoc(doc(as(CAROL), "friend_requests/r1")));
    });
    it("can't send a friend request to yourself", async () => {
      await assertFails(addDoc(collection(as(ALICE), "friend_requests"), requestDoc(ALICE, ALICE)));
    });
    it("a stranger can't forge a friendship between two other people", async () => {
      await assertFails(addDoc(collection(as(CAROL), "friendships"), { userA: ALICE, userB: BOB, createdAt: 1 }));
    });
    it("nobody can make themselves someone's friend without an accepted request", async () => {
      await assertFails(addDoc(collection(as(CAROL), "friendships"), { userA: CAROL, userB: ALICE, createdAt: 1 }));
    });
    it("a request can't be used by anyone but its recipient", async () => {
      await assertFails(addDoc(collection(as(CAROL), "friendships"),
        { userA: CAROL, userB: ALICE, createdAt: 1, requestId: "r1" }));
    });
    it("a used (deleted) request can't be replayed into a second friendship", async () => {
      await seed(env, (db) => deleteDoc(doc(db, "friend_requests/r1")));
      await assertFails(addDoc(collection(as(BOB), "friendships"),
        { userA: BOB, userB: ALICE, createdAt: 1, requestId: "r1" }));
    });
    it("can't send a friend request under someone else's name", async () => {
      await assertFails(addDoc(collection(as(CAROL), "friend_requests"), requestDoc(CAROL, BOB, { fromUsername: "alice" })));
    });
    it("a request can't carry extra fields or start as anything but pending", async () => {
      await assertFails(addDoc(collection(as(CAROL), "friend_requests"), requestDoc(CAROL, BOB, { status: "accepted" })));
      await assertFails(addDoc(collection(as(CAROL), "friend_requests"), requestDoc(CAROL, BOB, { isAdmin: true })));
    });
    it("the sender of a request can't accept it themselves", async () => {
      const db = as(ALICE); const b = writeBatch(db);
      b.set(doc(collection(db, "friendships")), { userA: ALICE, userB: BOB, createdAt: 1, requestId: "r1" });
      b.delete(doc(db, "friend_requests/r1"));
      await assertFails(b.commit());
    });
    it("one request can't make a second friendship (it must be used up in the same batch)", async () => {
      await assertFails(addDoc(collection(as(BOB), "friendships"),
        { userA: BOB, userB: ALICE, createdAt: 1, requestId: "r1" }));
    });
    it("the old app's accept (no requestId) is denied, so the new app must ship first", async () => {
      await assertFails(addDoc(collection(as(BOB), "friendships"), { userA: BOB, userB: ALICE, createdAt: 1 }));
    });
    it("a stranger can't delete someone else's friendship", async () => {
      await assertFails(deleteDoc(doc(as(CAROL), "friendships/f1")));
    });
    it("a stranger can't download the whole friendship graph", async () => {
      await assertFails(getDocs(collection(as(CAROL), "friendships")));
    });
  });

  describe("F11: user profiles", () => {
    it("rejects an absurdly long username", async () => {
      await assertFails(setDoc(doc(as(ALICE), "users/alice"),
        { uid: ALICE, username: "a".repeat(500), usernameLower: "a".repeat(500) }));
    });
    it("rejects usernames with spaces, symbols or non-ASCII letters", async () => {
      for (const name of ["my name", "bob!", "héllo"]) {
        await assertFails(setDoc(doc(as(ALICE), "users/alice"),
          { uid: ALICE, username: name, usernameLower: name.toLowerCase() }));
      }
    });
    it("usernameLower must match the username (no impersonation by search key)", async () => {
      await assertFails(setDoc(doc(as(ALICE), "users/alice"),
        { uid: ALICE, username: "Alice", usernameLower: "bob" }));
    });
    it("the push token must be a string", async () => {
      await assertFails(updateDoc(doc(as(ALICE), "users/alice"), { fcmToken: { not: "a string" } }));
    });
    it("the uid field must be the owner's uid", async () => {
      await assertFails(setDoc(doc(as(ALICE), "users/alice"),
        { uid: BOB, username: "Alice", usernameLower: "alice" }));
    });
  });

  describe("F24: leftovers", () => {
    it("the old connection_test collection is closed", async () => {
      await assertFails(setDoc(doc(as(CAROL), "connection_test/x"), { anything: true }));
    });
  });
});
