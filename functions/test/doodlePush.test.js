// Unit tests for the doodle push decision (finding F3). Run: npm --prefix functions test
const test = require("node:test");
const assert = require("node:assert/strict");
const {decideDoodlePush, displayName, MAX_NAME_LENGTH} = require("../doodlePush");

const ok = {senderId: "alice", receiverId: "bob", blocked: false, areFriends: true, muted: false, receiverExists: true, fcmToken: "t"};

test("friends with a token get the push", () => {
  assert.deepEqual(decideDoodlePush(ok), {send: true, reason: "ok"});
});

test("a stranger's doodle never pops up", () => {
  assert.deepEqual(decideDoodlePush({...ok, areFriends: false}), {send: false, reason: "not-friends"});
});

test("a doodle between blocked people never pops up, even if they're still friends", () => {
  assert.deepEqual(decideDoodlePush({...ok, blocked: true}), {send: false, reason: "blocked"});
  assert.equal(decideDoodlePush({...ok, blocked: true, areFriends: false, muted: true}).reason, "blocked");
});

test("a muted friend's doodle doesn't pop up", () => {
  assert.equal(decideDoodlePush({...ok, muted: true}).reason, "muted");
});

test("missing sender, receiver, self-sends, receiver doc or token are skipped", () => {
  assert.equal(decideDoodlePush({...ok, receiverId: ""}).reason, "no-receiver");
  assert.equal(decideDoodlePush({...ok, senderId: undefined}).reason, "no-sender");
  assert.equal(decideDoodlePush({...ok, receiverId: "alice"}).reason, "self");
  assert.equal(decideDoodlePush({...ok, receiverExists: false}).reason, "no-receiver-doc");
  assert.equal(decideDoodlePush({...ok, fcmToken: ""}).reason, "no-token");
});

test("friendship is checked before mute and token (a stranger learns nothing)", () => {
  assert.equal(decideDoodlePush({...ok, areFriends: false, muted: true, fcmToken: ""}).reason, "not-friends");
});

test("display name comes from the stored username, trimmed and capped", () => {
  assert.equal(displayName("  Alice "), "Alice");
  assert.equal(displayName(undefined), "A friend");
  assert.equal(displayName(""), "A friend");
  assert.equal(displayName(42), "A friend");
  assert.equal(displayName("x".repeat(500)).length, MAX_NAME_LENGTH);
});
