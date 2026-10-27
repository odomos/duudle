// Regression suite: behaviour that is correct TODAY and must stay correct after the
// Phase 1 lockdown. Includes the real app flows (shaped like the Kotlin code writes
// them) so a stricter rule can't silently break the app. Must always pass.
const { assertFails, assertSucceeds } = require("@firebase/rules-unit-testing");
const {
  doc, getDoc, setDoc, updateDoc, deleteDoc, collection, addDoc, getDocs,
  query, where, orderBy, limit, writeBatch, serverTimestamp, getCountFromServer, deleteField,
} = require("firebase/firestore");
const { createEnv, seed, seedUsers, doodleDoc, requestDoc, ALICE, BOB, CAROL } = require("./helpers");

describe("Current rules: must keep passing", function () {
  let env;
  const as = (uid) => env.authenticatedContext(uid).firestore();
  const anon = () => env.unauthenticatedContext().firestore();

  before(async () => { env = await createEnv(); });
  after(async () => { await env.cleanup(); });
  beforeEach(async () => { await env.clearFirestore(); });

  describe("signed-out requests", () => {
    it("can't read users, doodles or chats", async () => {
      await seed(env, async (db) => {
        await setDoc(doc(db, "users/alice"), { uid: ALICE, username: "alice", usernameLower: "alice" });
        await setDoc(doc(db, "doodles/d1"), doodleDoc(ALICE, BOB));
      });
      await assertFails(getDoc(doc(anon(), "users/alice")));
      await assertFails(getDoc(doc(anon(), "doodles/d1")));
      await assertFails(getDoc(doc(anon(), "chats/alice_bob")));
    });
    it("can't write anything", async () => {
      await assertFails(setDoc(doc(anon(), "users/alice"), { username: "x" }));
      await assertFails(addDoc(collection(anon(), "doodles"), doodleDoc(ALICE, BOB)));
    });
  });

  describe("users/{uid}", () => {
    it("owner can create their profile the way sign-up does", async () => {
      await assertSucceeds(setDoc(doc(as(ALICE), "users/alice"),
        { username: "Alice", usernameLower: "alice", uid: ALICE }));
    });
    it("owner can save their push token in the private document (server time)", async () => {
      await assertSucceeds(setDoc(doc(as(ALICE), "users/alice/private/push"),
        { fcmToken: "new-token", updatedAt: serverTimestamp() }));
      await assertSucceeds(getDoc(doc(as(ALICE), "users/alice/private/push")));
    });
    it("a pre-Phase-2 profile can remove its old public token, even with an extra field", async () => {
      await seed(env, (db) => setDoc(doc(db, "users/alice"),
        { uid: ALICE, username: "Al", usernameLower: "al", fcmToken: "old-token", legacyField: 1 }));
      await assertSucceeds(updateDoc(doc(as(ALICE), "users/alice"), { fcmToken: deleteField() }));
    });
    it("an older profile can add its search key even if its name predates the rule", async () => {
      await seed(env, (db) => setDoc(doc(db, "users/alice"), { uid: ALICE, username: "Old Name!" }));
      await assertSucceeds(updateDoc(doc(as(ALICE), "users/alice"), { usernameLower: "old name!" }));
    });
    it("nobody can write someone else's profile", async () => {
      await assertFails(setDoc(doc(as(CAROL), "users/alice"), { username: "hacked" }));
    });
    it("signed-in users can search usernames (prefix range, limit 20)", async () => {
      await seed(env, (db) => setDoc(doc(db, "users/bob"), { uid: BOB, username: "Bob", usernameLower: "bob" }));
      const q = query(collection(as(ALICE), "users"),
        where("usernameLower", ">=", "bo"), where("usernameLower", "<=", "bo"), limit(20));
      await assertSucceeds(getDocs(q));
    });
  });

  describe("private subcollections: mutes and favorites", () => {
    it("owner can read and write their own", async () => {
      await assertSucceeds(setDoc(doc(as(ALICE), "users/alice/mutes/bob"), { mutedAt: 1 }));
      await assertSucceeds(setDoc(doc(as(ALICE), "users/alice/favorites/d1"), { savedAt: 1 }));
      await assertSucceeds(getDoc(doc(as(ALICE), "users/alice/mutes/bob")));
    });
    it("a muted friend can't see or change the mute", async () => {
      await seed(env, (db) => setDoc(doc(db, "users/alice/mutes/bob"), { mutedAt: 1 }));
      await assertFails(getDoc(doc(as(BOB), "users/alice/mutes/bob")));
      await assertFails(deleteDoc(doc(as(BOB), "users/alice/mutes/bob")));
    });
    it("nobody else can read your favorites", async () => {
      await seed(env, (db) => setDoc(doc(db, "users/alice/favorites/d1"), { savedAt: 1 }));
      await assertFails(getDoc(doc(as(CAROL), "users/alice/favorites/d1")));
    });
  });

  describe("doodles: real app flows that must survive the lockdown", () => {
    beforeEach(async () => { await seedUsers(env); });
    it("sender can create a doodle as themselves", async () => {
      await assertSucceeds(addDoc(collection(as(ALICE), "doodles"), doodleDoc(ALICE, BOB)));
    });
    it("sender can create a flash card", async () => {
      await assertSucceeds(addDoc(collection(as(ALICE), "doodles"),
        doodleDoc(ALICE, BOB, { strokes: [], cardId: "card03" })));
    });
    it("receiver can read it, query their unseen ones and mark it removed", async () => {
      await seed(env, (db) => setDoc(doc(db, "doodles/d1"), doodleDoc(ALICE, BOB)));
      await assertSucceeds(getDoc(doc(as(BOB), "doodles/d1")));
      await assertSucceeds(getDocs(query(collection(as(BOB), "doodles"),
        where("receiverId", "==", BOB), where("status", "==", "unseen"))));
      await assertSucceeds(updateDoc(doc(as(BOB), "doodles/d1"), { status: "removed" }));
    });
    it("sender can list their sent doodles (Vault)", async () => {
      await assertSucceeds(getDocs(query(collection(as(ALICE), "doodles"), where("senderId", "==", ALICE))));
    });
  });

  describe("friends: real app flows that must survive the lockdown", () => {
    beforeEach(async () => { await seedUsers(env); });
    it("a user can send a request as themselves and see their incoming requests", async () => {
      await assertSucceeds(addDoc(collection(as(ALICE), "friend_requests"), requestDoc(ALICE, BOB)));
      await assertSucceeds(getDocs(query(collection(as(BOB), "friend_requests"),
        where("toUid", "==", BOB), where("status", "==", "pending"))));
    });
    it("the recipient can decline (delete) a request addressed to them", async () => {
      await seed(env, (db) => setDoc(doc(db, "friend_requests/r1"), requestDoc(ALICE, BOB)));
      await assertSucceeds(deleteDoc(doc(as(BOB), "friend_requests/r1")));
    });
    it("the recipient can accept: one batch creates the friendship and deletes the request", async () => {
      await seed(env, (db) => setDoc(doc(db, "friend_requests/r1"), requestDoc(ALICE, BOB)));
      const db = as(BOB);
      const b = writeBatch(db);
      b.set(doc(collection(db, "friendships")), { userA: BOB, userB: ALICE, createdAt: Date.now(), requestId: "r1" });
      b.delete(doc(db, "friend_requests/r1"));
      await assertSucceeds(b.commit());
    });
    it("a member can list their own friendships", async () => {
      await assertSucceeds(getDocs(query(collection(as(ALICE), "friendships"), where("userA", "==", ALICE))));
      await assertSucceeds(getDocs(query(collection(as(ALICE), "friendships"), where("userB", "==", ALICE))));
    });
  });

  describe("other queries the app makes", () => {
    beforeEach(async () => { await seedUsers(env); });
    it("Profile stats: count sent and received doodles (ProfileScreen)", async () => {
      await assertSucceeds(getCountFromServer(query(collection(as(ALICE), "doodles"), where("senderId", "==", ALICE))));
      await assertSucceeds(getCountFromServer(query(collection(as(ALICE), "doodles"), where("receiverId", "==", ALICE))));
      await assertSucceeds(getCountFromServer(collection(as(ALICE), "users/alice/favorites")));
    });
    it("'already friends?' check, both directions (FriendsScreen)", async () => {
      await assertSucceeds(getDocs(query(collection(as(ALICE), "friendships"),
        where("userA", "==", ALICE), where("userB", "==", BOB))));
      await assertSucceeds(getDocs(query(collection(as(ALICE), "friendships"),
        where("userA", "==", BOB), where("userB", "==", ALICE))));
    });
    it("'request already sent?' check (FriendsScreen)", async () => {
      await assertSucceeds(getDocs(query(collection(as(ALICE), "friend_requests"),
        where("fromUid", "==", ALICE), where("toUid", "==", BOB), where("status", "==", "pending"))));
    });
    it("receiver can open a doodle by id (push fallback fetch)", async () => {
      await seed(env, (db) => setDoc(doc(db, "doodles/d9"), doodleDoc(ALICE, BOB)));
      await assertSucceeds(getDoc(doc(as(BOB), "doodles/d9")));
    });
  });

  describe("chats (already locked down)", () => {
    const firstMessageBatch = (db, sender, chatId, members, text = "hi") => {
      const b = writeBatch(db);
      b.set(doc(collection(db, `chats/${chatId}/messages`)),
        { senderId: sender, kind: "text", text, createdAt: serverTimestamp() });
      b.set(doc(db, `chats/${chatId}`),
        { members, lastMessage: text.slice(0, 120), lastSenderId: sender, lastAt: serverTimestamp() }, { merge: true });
      return b.commit();
    };

    it("a member can start a chat with the first message (batch)", async () => {
      await assertSucceeds(firstMessageBatch(as(ALICE), ALICE, "alice_bob", [ALICE, BOB]));
    });
    it("a non-member can't read the chat or its messages", async () => {
      await seed(env, async (db) => {
        await setDoc(doc(db, "chats/alice_bob"), { members: [ALICE, BOB] });
        await setDoc(doc(db, "chats/alice_bob/messages/m1"), { senderId: ALICE, kind: "text", text: "secret", createdAt: 1 });
      });
      await assertFails(getDoc(doc(as(CAROL), "chats/alice_bob")));
      await assertFails(getDoc(doc(as(CAROL), "chats/alice_bob/messages/m1")));
      await assertFails(getDocs(collection(as(CAROL), "chats/alice_bob/messages")));
    });
    it("a member can read messages, newest first", async () => {
      await seed(env, (db) => setDoc(doc(db, "chats/alice_bob"), { members: [ALICE, BOB] }));
      await assertSucceeds(getDocs(query(collection(as(BOB), "chats/alice_bob/messages"),
        orderBy("createdAt", "desc"), limit(50))));
    });
    it("nobody can post as the other member", async () => {
      await seed(env, (db) => setDoc(doc(db, "chats/alice_bob"), { members: [ALICE, BOB] }));
      await assertFails(addDoc(collection(as(BOB), "chats/alice_bob/messages"),
        { senderId: ALICE, kind: "text", text: "fake", createdAt: serverTimestamp() }));
    });
    it("messages can't be edited or deleted", async () => {
      await seed(env, async (db) => {
        await setDoc(doc(db, "chats/alice_bob"), { members: [ALICE, BOB] });
        await setDoc(doc(db, "chats/alice_bob/messages/m1"), { senderId: ALICE, kind: "text", text: "hi", createdAt: 1 });
      });
      await assertFails(updateDoc(doc(as(ALICE), "chats/alice_bob/messages/m1"), { text: "edited" }));
      await assertFails(deleteDoc(doc(as(ALICE), "chats/alice_bob/messages/m1")));
    });
    it("a stranger can't create a chat between two other people", async () => {
      await assertFails(firstMessageBatch(as(CAROL), CAROL, "alice_bob", [ALICE, BOB]));
    });
    // The preview is trimmed to 120 like ChatRepository does, so only the message length can deny this.
    it("rejects a message over 1000 characters", async () => {
      await assertFails(firstMessageBatch(as(ALICE), ALICE, "alice_bob", [ALICE, BOB], "x".repeat(1001)));
    });
    it("accepts a message of exactly 1000 characters", async () => {
      await assertSucceeds(firstMessageBatch(as(ALICE), ALICE, "alice_bob", [ALICE, BOB], "x".repeat(1000)));
    });
    it("a member can update only their own read receipt", async () => {
      await seed(env, (db) => setDoc(doc(db, "chats/alice_bob"), { members: [ALICE, BOB] }));
      await assertSucceeds(setDoc(doc(as(ALICE), "chats/alice_bob"),
        { lastRead: { [ALICE]: serverTimestamp() } }, { merge: true }));
      await assertFails(setDoc(doc(as(ALICE), "chats/alice_bob"),
        { lastRead: { [BOB]: serverTimestamp() } }, { merge: true }));
    });
    it("members can't be changed", async () => {
      await seed(env, (db) => setDoc(doc(db, "chats/alice_bob"), { members: [ALICE, BOB] }));
      await assertFails(updateDoc(doc(as(ALICE), "chats/alice_bob"), { members: [ALICE, CAROL] }));
    });
  });
});
