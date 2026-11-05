// Unit tests for the doodle push decision (finding F3). Run: npm --prefix functions test
const test = require("node:test");
const assert = require("node:assert/strict");
const {decideDoodlePush, displayName, compactStrokes, MAX_NAME_LENGTH} = require("../doodlePush");

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

test("strokes compact to the push format, rounded to 3 decimals", () => {
  const raw = [{color: -16777216, width: 8, points: [{x: 0.12345, y: 0.5}, {x: 1, y: 0.0004}]}];
  assert.deepEqual(compactStrokes(raw), {strokes: [{c: -16777216, w: 8, p: [[0.123, 0.5], [1, 0]]}], pointCount: 2});
});

test("malformed strokes and points are dropped, never thrown on", () => {
  const raw = [
    null, "x", {color: 1, points: "nope"}, {points: [{x: 0, y: 0}]}, {color: "red", points: [{x: 0, y: 0}]},
    {color: 2, points: [null, {x: "0", y: 0}, {x: NaN, y: 0}, {x: 0, y: Infinity}]},
    {color: 3, width: -1, points: [7, {x: 0.2, y: 0.3}]},
  ];
  // Only the last stroke has a usable point; its bad width becomes the default 8.
  assert.deepEqual(compactStrokes(raw), {strokes: [{c: 3, w: 8, p: [[0.2, 0.3]]}], pointCount: 1});
  assert.deepEqual(compactStrokes(undefined), {strokes: [], pointCount: 0});
  assert.deepEqual(compactStrokes({0: "not a list"}), {strokes: [], pointCount: 0});
});

test("the colour is always a 32-bit int the app can read with getInt", () => {
  assert.equal(compactStrokes([{color: 4294967295, points: [{x: 0, y: 0}]}]).strokes[0].c, -1);
  assert.equal(compactStrokes([{color: 5.7, points: [{x: 0, y: 0}]}]).strokes[0].c, 5);
});
