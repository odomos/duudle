// Phase 4 (user-content safety): Block, Report and the Terms record.
// Block: once either person has blocked the other, nothing new can pass between them
// (doodles, friend requests, accepting a request, chat messages). Blocks are owner-only.
// Report: write-only, and a report can only point at content that really involves you.
const { assertFails, assertSucceeds } = require("@firebase/rules-unit-testing");
const {
  doc, getDoc, getDocs, setDoc, deleteDoc, addDoc, collection, collectionGroup, query, where, writeBatch, serverTimestamp,
} = require("firebase/firestore");
const { createEnv, seed, seedUsers, doodleDoc, requestDoc, ALICE, BOB, CAROL } = require("./helpers");

describe("Phase 4: block, report, terms", function () {
  let env;
  const as = (uid) => env.authenticatedContext(uid).firestore();
  const block = (db, owner, other) =>
    setDoc(doc(db, `users/${owner}/blocks/${other}`), { blockedAt: serverTimestamp(), username: other });
  const seedBlock = (owner, other) =>
    seed(env, (db) => setDoc(doc(db, `users/${owner}/blocks/${other}`), { blockedAt: new Date(), username: other }));
  const sendMessage = (db, sender, chatId, members, text = "hi") => {
    const b = writeBatch(db);
    b.set(doc(collection(db, `chats/${chatId}/messages`)),
      { senderId: sender, kind: "text", text, createdAt: serverTimestamp() });
    b.set(doc(db, `chats/${chatId}`),
      { members, lastMessage: text, lastSenderId: sender, lastAt: serverTimestamp() }, { merge: true });
    return b.commit();
  };
  const acceptBatch = (db, requestId, me, friend) => {
    const b = writeBatch(db);
    b.set(doc(collection(db, "friendships")), { userA: me, userB: friend, createdAt: 1, requestId });
    b.delete(doc(db, `friend_requests/${requestId}`));
    return b.commit();
  };
  const report = (extra) => ({
    reporterId: ALICE, reportedUid: BOB, reason: "harassment", contentType: "user",
    createdAt: serverTimestamp(), status: "open", ...extra,
  });

  before(async () => { env = await createEnv(); });
  after(async () => { await env.cleanup(); });
  beforeEach(async () => {
    await env.clearFirestore();
    await seedUsers(env);
    await seed(env, async (db) => {
      await setDoc(doc(db, "friendships/f1"), { userA: BOB, userB: ALICE, createdAt: 1 });
      await setDoc(doc(db, "doodles/d1"), doodleDoc(BOB, ALICE));
      await setDoc(doc(db, "doodles/d2"), doodleDoc(CAROL, ALICE));
      await setDoc(doc(db, "chats/alice_bob"), { members: [ALICE, BOB] });
    });
  });

  describe("blocks are private to their owner", () => {
    it("you can block someone and see your own list", async () => {
      await assertSucceeds(block(as(ALICE), ALICE, BOB));
      await assertSucceeds(getDocs(collection(as(ALICE), "users/alice/blocks")));
    });
    it("the blocked person can't see that they're blocked", async () => {
      await seedBlock(ALICE, BOB);
      await assertFails(getDoc(doc(as(BOB), "users/alice/blocks/bob")));
      await assertFails(getDocs(collection(as(BOB), "users/alice/blocks")));
      await assertFails(getDocs(query(collectionGroup(as(BOB), "blocks"))));
    });
    it("nobody can block or unblock on someone else's behalf", async () => {
      await assertFails(block(as(CAROL), ALICE, BOB));
      await seedBlock(ALICE, BOB);
      await assertFails(deleteDoc(doc(as(BOB), "users/alice/blocks/bob")));
    });
    it("you can unblock", async () => {
      await seedBlock(ALICE, BOB);
      await assertSucceeds(deleteDoc(doc(as(ALICE), "users/alice/blocks/bob")));
    });
    it("only known fields, server time, and not yourself", async () => {
      await assertFails(setDoc(doc(as(ALICE), "users/alice/blocks/bob"), { blockedAt: new Date(0), username: "bob" }));
      await assertFails(setDoc(doc(as(ALICE), "users/alice/blocks/bob"),
        { blockedAt: serverTimestamp(), username: "bob", extra: 1 }));
      await assertFails(block(as(ALICE), ALICE, ALICE));
    });
    it("the app's block lookups (Safety.blockUser) are allowed", async () => {
      await seed(env, async (db) => {
        await setDoc(doc(db, "friend_requests/r1"), requestDoc(BOB, ALICE));
        await setDoc(doc(db, "friend_requests/r2"), requestDoc(ALICE, BOB));
      });
      const db = as(ALICE);
      const q = (col, a, av, b, bv) => getDocs(query(collection(db, col), where(a, "==", av), where(b, "==", bv)));
      await assertSucceeds(q("friendships", "userA", ALICE, "userB", BOB));
      await assertSucceeds(q("friendships", "userA", BOB, "userB", ALICE));
      await assertSucceeds(q("friend_requests", "fromUid", ALICE, "toUid", BOB));
      await assertSucceeds(q("friend_requests", "fromUid", BOB, "toUid", ALICE));
      const b = writeBatch(db);
      b.set(doc(db, "users/alice/blocks/bob"), { blockedAt: serverTimestamp(), username: "bob" });
      b.delete(doc(db, "friendships/f1"));
      b.delete(doc(db, "friend_requests/r1"));
      b.delete(doc(db, "friend_requests/r2"));
      await assertSucceeds(b.commit());
    });
    it("blocking in one batch also unfriends (the app's block action)", async () => {
      const db = as(ALICE); const b = writeBatch(db);
      b.set(doc(db, "users/alice/blocks/bob"), { blockedAt: serverTimestamp(), username: "bob" });
      b.delete(doc(db, "friendships/f1"));
      await assertSucceeds(b.commit());
    });
  });

  describe("a block stops new contact, both ways", () => {
    for (const [label, blocker, blocked] of [["blocked person", ALICE, BOB], ["blocker", BOB, ALICE]]) {
      it(`a doodle from the ${label} is denied`, async () => {
        await seedBlock(blocker, blocked);
        await assertFails(addDoc(collection(as(BOB), "doodles"), doodleDoc(BOB, ALICE)));
      });
      it(`a flash card from the ${label} is denied`, async () => {
        await seedBlock(blocker, blocked);
        await assertFails(addDoc(collection(as(BOB), "doodles"), doodleDoc(BOB, ALICE, { strokes: [], cardId: "card01" })));
      });
      it(`a friend request from the ${label} is denied`, async () => {
        await seedBlock(blocker, blocked);
        await assertFails(addDoc(collection(as(BOB), "friend_requests"), requestDoc(BOB, ALICE)));
      });
      it(`a chat message from the ${label} is denied`, async () => {
        await seedBlock(blocker, blocked);
        await assertFails(sendMessage(as(BOB), BOB, "alice_bob", [ALICE, BOB]));
      });
    }
    it("a request sent before the block can't be accepted after it", async () => {
      await seed(env, (db) => setDoc(doc(db, "friend_requests/r1"), requestDoc(CAROL, ALICE)));
      await seedBlock(ALICE, CAROL);
      await assertFails(acceptBatch(as(ALICE), "r1", ALICE, CAROL));
    });
    it("other people are unaffected by someone's block", async () => {
      await seedBlock(ALICE, BOB);
      await assertSucceeds(addDoc(collection(as(CAROL), "doodles"), doodleDoc(CAROL, ALICE)));
      await assertSucceeds(addDoc(collection(as(CAROL), "friend_requests"), requestDoc(CAROL, BOB)));
    });
    it("after unblocking, contact works again", async () => {
      await seedBlock(ALICE, BOB);
      await seed(env, (db) => deleteDoc(doc(db, "users/alice/blocks/bob")));
      await assertSucceeds(addDoc(collection(as(BOB), "doodles"), doodleDoc(BOB, ALICE)));
      await assertSucceeds(sendMessage(as(BOB), BOB, "alice_bob", [ALICE, BOB]));
    });
    it("the blocker can still read old doodles and mark them removed", async () => {
      await seedBlock(ALICE, BOB);
      await assertSucceeds(getDoc(doc(as(ALICE), "doodles/d1")));
    });
  });

  describe("reports", () => {
    it("you can report a user", async () => {
      await assertSucceeds(addDoc(collection(as(ALICE), "reports"), report()));
      await assertSucceeds(addDoc(collection(as(ALICE), "reports"), report({ reason: "other", details: "x".repeat(1000) })));
    });
    it("you can report a doodle the reported person sent you", async () => {
      await assertSucceeds(addDoc(collection(as(ALICE), "reports"), report({ contentType: "doodle", contentId: "d1" })));
    });
    it("you can report your chat with them", async () => {
      await assertSucceeds(addDoc(collection(as(ALICE), "reports"), report({ contentType: "chat", contentId: "alice_bob" })));
    });
    it("you can't pin a doodle on someone who didn't send it, or report a doodle you didn't get", async () => {
      await assertFails(addDoc(collection(as(ALICE), "reports"), report({ contentType: "doodle", contentId: "d2" })));
      await assertFails(addDoc(collection(as(CAROL), "reports"),
        report({ reporterId: CAROL, contentType: "doodle", contentId: "d1" })));
      await assertFails(addDoc(collection(as(ALICE), "reports"), report({ contentType: "doodle", contentId: "nope" })));
    });
    it("a chat report must be the chat between the two of you", async () => {
      await assertFails(addDoc(collection(as(ALICE), "reports"), report({ contentType: "chat", contentId: "bob_carol" })));
      await assertFails(addDoc(collection(as(CAROL), "reports"),
        report({ reporterId: CAROL, contentType: "chat", contentId: "alice_bob" })));
    });
    it("nobody can read, edit or delete reports, including the reporter", async () => {
      await seed(env, (db) => setDoc(doc(db, "reports/rep1"), { ...report(), createdAt: new Date() }));
      await assertFails(getDoc(doc(as(ALICE), "reports/rep1")));
      await assertFails(getDoc(doc(as(BOB), "reports/rep1")));
      await assertFails(getDocs(collection(as(ALICE), "reports")));
      await assertFails(setDoc(doc(as(ALICE), "reports/rep1"), { ...report(), status: "closed" }));
      await assertFails(deleteDoc(doc(as(ALICE), "reports/rep1")));
    });
    it("a report is sent as yourself, about someone else, with a known reason, open, at server time", async () => {
      await assertFails(addDoc(collection(as(CAROL), "reports"), report()));
      await assertFails(addDoc(collection(as(ALICE), "reports"), report({ reportedUid: ALICE })));
      await assertFails(addDoc(collection(as(ALICE), "reports"), report({ reason: "dislike" })));
      await assertFails(addDoc(collection(as(ALICE), "reports"), report({ status: "closed" })));
      await assertFails(addDoc(collection(as(ALICE), "reports"), report({ createdAt: new Date(0) })));
      await assertFails(addDoc(collection(as(ALICE), "reports"), report({ details: "x".repeat(1001) })));
      await assertFails(addDoc(collection(as(ALICE), "reports"), report({ priority: "urgent" })));
      await assertFails(addDoc(collection(as(ALICE), "reports"), report({ contentType: "user", contentId: "d1" })));
    });
  });

  describe("terms record", () => {
    it("you can record which Terms version you accepted, privately", async () => {
      await assertSucceeds(setDoc(doc(as(ALICE), "users/alice/private/terms"), { version: 1, acceptedAt: serverTimestamp() }));
      await assertFails(getDoc(doc(as(BOB), "users/alice/private/terms")));
    });
    it("nobody else can write it, and it takes only a positive whole version at server time", async () => {
      await assertFails(setDoc(doc(as(BOB), "users/alice/private/terms"), { version: 1, acceptedAt: serverTimestamp() }));
      await assertFails(setDoc(doc(as(ALICE), "users/alice/private/terms"), { version: 0, acceptedAt: serverTimestamp() }));
      await assertFails(setDoc(doc(as(ALICE), "users/alice/private/terms"), { version: "1", acceptedAt: serverTimestamp() }));
      await assertFails(setDoc(doc(as(ALICE), "users/alice/private/terms"), { version: 1, acceptedAt: new Date(0) }));
      await assertFails(setDoc(doc(as(ALICE), "users/alice/private/terms"),
        { version: 1, acceptedAt: serverTimestamp(), fcmToken: "t" }));
    });
  });
});
