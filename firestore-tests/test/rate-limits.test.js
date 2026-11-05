// Phase 6 (abuse controls): every doodle or card, chat message, friend request and report
// must be paid for by one step of the sender's counter users/{uid}/limits/{kind}, and the
// counter stops at the limit until its window has passed (RateLimits.kt, firestore.rules).
const { assertFails, assertSucceeds } = require("@firebase/rules-unit-testing");
const {
  doc, getDoc, setDoc, deleteDoc, collection, addDoc, writeBatch, serverTimestamp, increment, Timestamp,
} = require("firebase/firestore");
const { createEnv, seed, seedUsers, doodleDoc, requestDoc, limitedAdd, ALICE, BOB, CAROL } = require("./helpers");

const HOUR = 60 * 60 * 1000;

describe("Phase 6: rate limits", function () {
  let env;
  const as = (uid) => env.authenticatedContext(uid).firestore();
  const counterPath = (uid, kind) => `users/${uid}/limits/${kind}`;
  const seedCounter = (uid, kind, count, ageMillis, lastId = "old") =>
    seed(env, (db) => setDoc(doc(db, counterPath(uid, kind)),
      { count, windowStart: Timestamp.fromMillis(Date.now() - ageMillis), lastId }));
  const message = (sender) => ({ senderId: sender, kind: "text", text: "hi", createdAt: serverTimestamp() });
  const chatDoc = (b, db, sender) => b.set(doc(db, "chats/alice_bob"),
    { members: [ALICE, BOB], lastMessage: "hi", lastSenderId: sender, lastAt: serverTimestamp() }, { merge: true });
  const report = () => ({
    reporterId: ALICE, reportedUid: BOB, reason: "spam", contentType: "user", createdAt: serverTimestamp(), status: "open",
  });
  const sendDoodle = () => limitedAdd(as(ALICE), ALICE, "doodles", "doodles", doodleDoc(ALICE, BOB));

  before(async () => { env = await createEnv(); });
  after(async () => { await env.cleanup(); });
  beforeEach(async () => {
    await env.clearFirestore();
    await seedUsers(env);
  });

  describe("every send needs a counter step", () => {
    it("the app's way works for all four kinds", async () => {
      await assertSucceeds(sendDoodle());
      await assertSucceeds(limitedAdd(as(ALICE), ALICE, "messages", "chats/alice_bob/messages", message(ALICE),
        (b, db) => chatDoc(b, db, ALICE)));
      await assertSucceeds(limitedAdd(as(ALICE), ALICE, "friend_requests", "friend_requests", requestDoc(ALICE, CAROL)));
      await assertSucceeds(limitedAdd(as(ALICE), ALICE, "reports", "reports", report()));
      const counter = await getDoc(doc(as(ALICE), counterPath(ALICE, "doodles")));
      if (counter.data().count !== 1) throw new Error(`count ${counter.data().count}`);
    });
    it("a send without a counter step is refused (older app versions included)", async () => {
      await assertFails(addDoc(collection(as(ALICE), "doodles"), doodleDoc(ALICE, BOB)));
      await assertFails(addDoc(collection(as(ALICE), "friend_requests"), requestDoc(ALICE, CAROL)));
      await assertFails(addDoc(collection(as(ALICE), "reports"), report()));
      const db = as(ALICE);
      const b = writeBatch(db);
      b.set(doc(collection(db, "chats/alice_bob/messages")), message(ALICE));
      chatDoc(b, db, ALICE);
      await assertFails(b.commit());
    });
    it("one counter step can't pay for two docs in a batch", async () => {
      const db = as(ALICE);
      const first = doc(collection(db, "doodles"));
      const b = writeBatch(db);
      b.set(first, doodleDoc(ALICE, BOB));
      b.set(doc(collection(db, "doodles")), doodleDoc(ALICE, BOB));
      b.set(doc(db, counterPath(ALICE, "doodles")), { count: 1, windowStart: serverTimestamp(), lastId: first.id });
      await assertFails(b.commit());
    });
    it("a step paid for one kind can't be spent on another", async () => {
      const db = as(ALICE);
      const ref = doc(collection(db, "friend_requests"));
      const b = writeBatch(db);
      b.set(ref, requestDoc(ALICE, CAROL));
      b.set(doc(db, counterPath(ALICE, "doodles")), { count: 1, windowStart: serverTimestamp(), lastId: ref.id });
      await assertFails(b.commit());
    });
    it("a deleted friend request can't be sent again without a new step", async () => {
      const db = as(ALICE);
      const ref = doc(db, "friend_requests/r1");
      const b = writeBatch(db);
      b.set(ref, requestDoc(ALICE, CAROL));
      b.set(doc(db, counterPath(ALICE, "friend_requests")), { count: 1, windowStart: serverTimestamp(), lastId: "r1" });
      await assertSucceeds(b.commit());
      await assertSucceeds(deleteDoc(ref));
      await assertFails(setDoc(ref, requestDoc(ALICE, CAROL)));
    });
  });

  describe("the limit", () => {
    it("the 60th doodle in an hour goes through, the 61st doesn't", async () => {
      await seedCounter(ALICE, "doodles", 59, 10 * 60 * 1000);
      await assertSucceeds(sendDoodle());
      await assertFails(sendDoodle());
    });
    it("other kinds stop at their own limits", async () => {
      await seedCounter(ALICE, "messages", 300, 10 * 60 * 1000);
      await assertFails(limitedAdd(as(ALICE), ALICE, "messages", "chats/alice_bob/messages", message(ALICE),
        (b, db) => chatDoc(b, db, ALICE)));
      // Friend requests and reports count per day, so two hours later they're still blocked.
      await seedCounter(ALICE, "friend_requests", 30, 2 * HOUR);
      await assertFails(limitedAdd(as(ALICE), ALICE, "friend_requests", "friend_requests", requestDoc(ALICE, CAROL)));
      await seedCounter(ALICE, "reports", 20, 2 * HOUR);
      await assertFails(limitedAdd(as(ALICE), ALICE, "reports", "reports", report()));
    });
    it("one person's limit doesn't block anyone else", async () => {
      await seedCounter(ALICE, "doodles", 60, 10 * 60 * 1000);
      await assertSucceeds(limitedAdd(as(BOB), BOB, "doodles", "doodles", doodleDoc(BOB, ALICE)));
    });
    it("once the window has passed, a new one starts at 1", async () => {
      await seedCounter(ALICE, "doodles", 60, 61 * 60 * 1000);
      const db = as(ALICE);
      const ref = doc(collection(db, "doodles"));
      // Adding to the old window is refused; starting a new one works.
      let b = writeBatch(db);
      b.set(ref, doodleDoc(ALICE, BOB));
      b.set(doc(db, counterPath(ALICE, "doodles")), { count: increment(1), lastId: ref.id }, { merge: true });
      await assertFails(b.commit());
      b = writeBatch(db);
      b.set(ref, doodleDoc(ALICE, BOB));
      b.set(doc(db, counterPath(ALICE, "doodles")), { count: 1, windowStart: serverTimestamp(), lastId: ref.id });
      await assertSucceeds(b.commit());
    });
  });

  describe("the counter itself", () => {
    const write = (uid, data, kind = "doodles") => setDoc(doc(as(uid), counterPath(uid, kind)), data, { merge: true });

    it("can't be restarted early, deleted, skipped ahead or lowered", async () => {
      await seedCounter(ALICE, "doodles", 60, 10 * 60 * 1000);
      await assertFails(write(ALICE, { count: 1, windowStart: serverTimestamp(), lastId: "x" }));
      await assertFails(deleteDoc(doc(as(ALICE), counterPath(ALICE, "doodles"))));
      await seedCounter(ALICE, "doodles", 10, 10 * 60 * 1000);
      await assertFails(write(ALICE, { count: 12, lastId: "x" }));
      await assertFails(write(ALICE, { count: 9, lastId: "x" }));
      await assertFails(write(ALICE, { count: increment(1), lastId: "x", windowStart: serverTimestamp() }));
      await assertFails(write(ALICE, { count: increment(1), lastId: "x", extra: true }));
    });
    it("can't start above 1, for an unknown kind, or with an old window", async () => {
      await assertFails(write(ALICE, { count: 5, windowStart: serverTimestamp(), lastId: "x" }));
      await assertFails(write(ALICE, { count: 1, windowStart: serverTimestamp(), lastId: "x" }, "favorites"));
      await assertFails(write(ALICE, { count: 1, windowStart: Timestamp.fromMillis(Date.now() - 2 * HOUR), lastId: "x" }));
    });
    it("is the owner's only", async () => {
      await seedCounter(ALICE, "doodles", 3, 1000);
      await assertSucceeds(getDoc(doc(as(ALICE), counterPath(ALICE, "doodles"))));
      await assertFails(getDoc(doc(as(BOB), counterPath(ALICE, "doodles"))));
      await assertFails(setDoc(doc(as(BOB), counterPath(ALICE, "doodles")), { count: 4, lastId: "x" }, { merge: true }));
    });
  });
});
