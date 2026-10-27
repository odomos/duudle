// Phase 2 target (audit finding F9): the device push token must not live on the public,
// world-readable profile. EXPECTED TO FAIL until the token moves to a private document;
// Phase 1 keeps allowing it so existing phones can still save their token.
const { assertFails } = require("@firebase/rules-unit-testing");
const { doc, setDoc } = require("firebase/firestore");
const { createEnv, ALICE } = require("./helpers");

describe("Phase 2 target: push token privacy (expected to FAIL until Phase 2)", function () {
  let env;
  before(async () => { env = await createEnv(); });
  after(async () => { await env.cleanup(); });
  beforeEach(async () => { await env.clearFirestore(); });

  it("the push token isn't stored on the public profile", async () => {
    await assertFails(setDoc(doc(env.authenticatedContext(ALICE).firestore(), "users/alice"),
      { uid: ALICE, username: "Alice", usernameLower: "alice", fcmToken: "token-value" }));
  });
});
