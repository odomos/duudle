// Where a user's push token lives (Phase 2), kept free of Firebase so it can be unit
// tested. New app versions write users/{uid}/private/push; profiles from before Phase 2
// may still carry a public users/{uid}.fcmToken until that phone's app updates.

/** The token to send to: the private one if present, else the legacy public field. */
function pickToken(privateData, profileData) {
  const fresh = privateData && typeof privateData.fcmToken === "string" ? privateData.fcmToken : "";
  if (fresh) return {token: fresh, source: "private"};
  const legacy = profileData && typeof profileData.fcmToken === "string" ? profileData.fcmToken : "";
  if (legacy) return {token: legacy, source: "legacy"};
  return {token: "", source: "none"};
}

// FCM error codes meaning the token will never work again (app uninstalled, data
// cleared, token replaced). Anything else may be temporary, so the token is kept.
const DEAD_TOKEN_CODES = new Set([
  "messaging/registration-token-not-registered",
  "messaging/invalid-registration-token",
]);

function isDeadTokenError(error) {
  return !!(error && DEAD_TOKEN_CODES.has(error.code));
}

/**
 * Reads the recipient's token: users/{uid}/private/push first, then the legacy public
 * field. `userRef` is a Firestore DocumentReference; `profileDoc` its snapshot.
 */
async function readPushToken(userRef, profileDoc) {
  const privateDoc = await userRef.collection("private").doc("push").get();
  return pickToken(privateDoc.exists ? privateDoc.data() : undefined, profileDoc.exists ? profileDoc.data() : undefined);
}

// gRPC FAILED_PRECONDITION: the document changed since we read it.
const FAILED_PRECONDITION = 9;

/**
 * After FCM says `token` is permanently dead, forget it, but only where it's still the
 * stored value AND the document hasn't changed since we read it. A phone retiring a token
 * writes its replacement at about the same moment; the lastUpdateTime precondition makes
 * sure that fresh token is never deleted by mistake. Returns what was removed.
 */
async function forgetDeadToken(userRef, token, deleteSentinel) {
  const privateRef = userRef.collection("private").doc("push");
  const [privateDoc, profileDoc] = await Promise.all([privateRef.get(), userRef.get()]);
  const removed = {private: false, legacy: false};
  const keepIfChanged = (error) => {
    if (error && error.code === FAILED_PRECONDITION) return; // a new token arrived: keep it
    throw error;
  };
  if (privateDoc.exists && privateDoc.data().fcmToken === token) {
    await privateRef.delete({lastUpdateTime: privateDoc.updateTime})
        .then(() => { removed.private = true; }, keepIfChanged);
  }
  if (profileDoc.exists && profileDoc.data().fcmToken === token) {
    await userRef.update({fcmToken: deleteSentinel}, {lastUpdateTime: profileDoc.updateTime})
        .then(() => { removed.legacy = true; }, keepIfChanged);
  }
  return removed;
}

module.exports = {pickToken, isDeadTokenError, readPushToken, forgetDeadToken, FAILED_PRECONDITION};
