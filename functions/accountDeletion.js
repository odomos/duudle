// Account deletion (Phase 5a). Deletes everything stored for one user, except doodles and
// chats that an open report points at: those stay as evidence until the report is closed
// (MODERATION.md). Safe to run again if it stopped part-way.

/** Doodle and chat ids that open reports point at. */
function evidenceToKeep(reports) {
  const doodles = new Set();
  const chats = new Set();
  for (const report of reports) {
    if (!report || report.status !== "open" || typeof report.contentId !== "string") continue;
    if (report.contentType === "doodle") doodles.add(report.contentId);
    else if (report.contentType === "chat") chats.add(report.contentId);
  }
  return {doodles, chats};
}

/** The chat id two users share: both uids sorted, joined with "_" (as in the app). */
function chatIdFor(a, b) {
  return a < b ? `${a}_${b}` : `${b}_${a}`;
}

/**
 * Deletes the user's profile (with mutes, favorites, blocks and private docs), friend
 * requests, friendships, doodles sent and received, and chats. Returns counts for the log.
 * The Auth account is deleted separately, last, so a failure here can be retried.
 */
async function deleteAccountData(db, uid) {
  const where = (collection, field, op, value) => db.collection(collection).where(field, op, value).get();

  const reportSnaps = await Promise.all([
    where("reports", "reportedUid", "==", uid),
    where("reports", "reporterId", "==", uid),
  ]);
  const keep = evidenceToKeep(reportSnaps.flatMap((snap) => snap.docs.map((doc) => doc.data())));

  const [sent, received, requestsFrom, requestsTo, friendsA, friendsB, chats] = await Promise.all([
    where("doodles", "senderId", "==", uid),
    where("doodles", "receiverId", "==", uid),
    where("friend_requests", "fromUid", "==", uid),
    where("friend_requests", "toUid", "==", uid),
    where("friendships", "userA", "==", uid),
    where("friendships", "userB", "==", uid),
    where("chats", "members", "array-contains", uid),
  ]);

  const doodles = [...sent.docs, ...received.docs];
  const friendships = [...friendsA.docs, ...friendsB.docs];
  const writer = db.bulkWriter();
  let doodlesKept = 0;
  for (const doc of doodles) {
    if (keep.doodles.has(doc.id)) doodlesKept++;
    else writer.delete(doc.ref);
  }
  for (const doc of [...requestsFrom.docs, ...requestsTo.docs, ...friendships]) writer.delete(doc.ref);
  await writer.close();

  // A chat's messages can outlive a missing chat doc, so also try each friend's chat id.
  const chatIds = new Set(chats.docs.map((doc) => doc.id));
  for (const doc of friendships) {
    const {userA, userB} = doc.data();
    if (userA && userB) chatIds.add(chatIdFor(userA, userB));
  }
  let chatsKept = 0;
  for (const chatId of chatIds) {
    if (keep.chats.has(chatId)) chatsKept++;
    else await db.recursiveDelete(db.collection("chats").doc(chatId));
  }

  await db.recursiveDelete(db.collection("users").doc(uid));

  return {
    doodlesDeleted: doodles.length - doodlesKept,
    doodlesKept,
    requestsDeleted: requestsFrom.size + requestsTo.size,
    friendshipsDeleted: friendships.length,
    chatsDeleted: chatIds.size - chatsKept,
    chatsKept,
  };
}

/** Deletes the Auth account; one that's already gone counts as done. */
async function deleteAuthUser(auth, uid) {
  try {
    await auth.deleteUser(uid);
  } catch (error) {
    if (error.code !== "auth/user-not-found") throw error;
  }
}

/** The token from an "Authorization: Bearer <Firebase ID token>" header, or null. */
function bearerToken(header) {
  const match = /^Bearer\s+(\S+)$/.exec(header || "");
  return match ? match[1] : null;
}

module.exports = {evidenceToKeep, chatIdFor, deleteAccountData, deleteAuthUser, bearerToken};
