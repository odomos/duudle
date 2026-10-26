// Target suite: what the rules SHOULD deny after the Phase 1 lockdown (audit findings
// F1, F2, F9, F11, F24). Against today's rules these are EXPECTED TO FAIL: each
// failure is evidence of a live access-control hole. Once Phase 1 lands, this file
// must pass and gets merged into the regression suite.
const { assertFails } = require("@firebase/rules-unit-testing");
const {
  doc, getDoc, setDoc, updateDoc, deleteDoc, collection, addDoc, getDocs, query, where,
} = require("firebase/firestore");
const { createEnv, seed, doodleDoc, requestDoc, ALICE, BOB, CAROL } = require("./helpers");

describe("Lockdown targets (expected to FAIL until Phase 1)", function () {
  let env;
  const as = (uid) => env.authenticatedContext(uid).firestore();

  before(async () => { env = await createEnv(); });
  after(async () => { await env.cleanup(); });
  beforeEach(async () => {
    await env.clearFirestore();
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
    it("a stranger can't delete someone else's friendship", async () => {
      await assertFails(deleteDoc(doc(as(CAROL), "friendships/f1")));
    });
    it("a stranger can't download the whole friendship graph", async () => {
      await assertFails(getDocs(collection(as(CAROL), "friendships")));
    });
  });

  describe("F9 and F11: user profiles", () => {
    it("the push token isn't stored on the public profile", async () => {
      await assertFails(setDoc(doc(as(ALICE), "users/alice"),
        { uid: ALICE, username: "Alice", usernameLower: "alice", fcmToken: "token-value" }));
    });
    it("rejects an absurdly long username", async () => {
      await assertFails(setDoc(doc(as(ALICE), "users/alice"),
        { uid: ALICE, username: "a".repeat(500), usernameLower: "a".repeat(500) }));
    });
    it("usernameLower must match the username (no impersonation by search key)", async () => {
      await assertFails(setDoc(doc(as(ALICE), "users/alice"),
        { uid: ALICE, username: "Alice", usernameLower: "bob" }));
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
