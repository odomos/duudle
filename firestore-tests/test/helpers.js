// Shared setup for the Firestore rules tests. Everything runs against the local
// emulator under the offline-only project id "demo-duudle": these tests can never
// reach the live duudle-296a9 project.
const fs = require("fs");
const path = require("path");
const { initializeTestEnvironment } = require("@firebase/rules-unit-testing");
const { setLogLevel } = require("firebase/firestore");

// Denied writes are the point of most tests; don't print every PERMISSION_DENIED.
setLogLevel("silent");

const PROJECT_ID = "demo-duudle";
const RULES_PATH = path.join(__dirname, "..", "..", "firestore.rules");

// Fixed uids. alice < bob < carol, so chat ids are "alice_bob" etc.
const ALICE = "alice";
const BOB = "bob";
const CAROL = "carol"; // a stranger to alice and bob

async function createEnv() {
  return initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules: fs.readFileSync(RULES_PATH, "utf8"),
      host: "127.0.0.1",
      port: 8080,
    },
  });
}

/** A doodle doc shaped exactly like DrawScreen.sendDoodle writes it. */
function doodleDoc(sender, receiver, extra = {}) {
  return {
    senderId: sender,
    senderUsername: sender,
    receiverId: receiver,
    receiverUsername: receiver,
    strokes: [{ points: [{ x: 0.1, y: 0.2 }, { x: 0.3, y: 0.4 }], color: -16777216, width: 8 }],
    createdAt: Date.now(),
    status: "unseen",
    ...extra,
  };
}

/** A friend request shaped like FriendsScreen.sendFriendRequest writes it. */
function requestDoc(from, to, extra = {}) {
  return {
    fromUid: from,
    fromUsername: from,
    toUid: to,
    toUsername: to,
    status: "pending",
    timestamp: Date.now(),
    ...extra,
  };
}

/** Seeds data with rules bypassed (like an admin would). */
async function seed(env, fn) {
  await env.withSecurityRulesDisabled(async (ctx) => fn(ctx.firestore()));
}

module.exports = { createEnv, seed, doodleDoc, requestDoc, ALICE, BOB, CAROL };
