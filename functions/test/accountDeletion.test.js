// Unit tests for account deletion helpers (Phase 5a). Run: npm --prefix functions test
const test = require("node:test");
const assert = require("node:assert/strict");
const {evidenceToKeep, chatIdFor, deleteAuthUser, bearerToken} = require("../accountDeletion");

test("open doodle and chat reports keep their content; closed and user reports don't", () => {
  const keep = evidenceToKeep([
    {status: "open", contentType: "doodle", contentId: "d1"},
    {status: "open", contentType: "chat", contentId: "a_b"},
    {status: "dismissed", contentType: "doodle", contentId: "d2"},
    {status: "actioned", contentType: "chat", contentId: "a_c"},
    {status: "open", contentType: "user"},
    {status: "open", contentType: "doodle"},
    null,
  ]);
  assert.deepEqual([...keep.doodles], ["d1"]);
  assert.deepEqual([...keep.chats], ["a_b"]);
});

test("chat ids match the app: sorted uids joined with _", () => {
  assert.equal(chatIdFor("bob", "alice"), "alice_bob");
  assert.equal(chatIdFor("alice", "bob"), "alice_bob");
  assert.equal(chatIdFor("Zed", "abe"), "Zed_abe"); // uppercase sorts first, like Kotlin
});

test("only a well-formed Bearer header yields a token", () => {
  assert.equal(bearerToken("Bearer abc.def"), "abc.def");
  assert.equal(bearerToken("bearer abc"), null);
  assert.equal(bearerToken("Bearer "), null);
  assert.equal(bearerToken("Basic abc"), null);
  assert.equal(bearerToken(undefined), null);
});

test("an Auth account that's already gone counts as deleted; other errors don't", async () => {
  await deleteAuthUser({deleteUser: async () => { throw Object.assign(new Error("x"), {code: "auth/user-not-found"}); }}, "u");
  await assert.rejects(
    deleteAuthUser({deleteUser: async () => { throw Object.assign(new Error("x"), {code: "auth/internal-error"}); }}, "u"),
  );
});
