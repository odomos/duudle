const {onDocumentCreated} = require("firebase-functions/v2/firestore");
const {initializeApp} = require("firebase-admin/app");
const {getFirestore, FieldValue} = require("firebase-admin/firestore");
const {getMessaging} = require("firebase-admin/messaging");
const logger = require("firebase-functions/logger");
const {decideDoodlePush, displayName} = require("./doodlePush");
const {isDeadTokenError, readPushToken, forgetDeadToken} = require("./pushTokens");

// Must run before any getFirestore()/getMessaging() call (the handlers below use them).
initializeApp();

/** Forgets a dead token (see pushTokens.forgetDeadToken) and logs what was removed. */
async function cleanUpDeadToken(userRef, token, logContext) {
  try {
    const removed = await forgetDeadToken(userRef, token, FieldValue.delete());
    logger.info("Dead push token handled", {...logContext, removedPrivate: removed.private, removedLegacy: removed.legacy});
  } catch (error) {
    logger.error("Couldn't remove a dead push token", {...logContext, error: error.message});
  }
}

function serializeStrokes(strokes) {
  // Round coordinates to keep the payload small and fast to deliver
  const compact = (strokes || []).map((s) => ({
    c: s.color,
    w: s.width,
    p: (s.points || []).map((pt) => [
      Math.round(pt.x * 1000) / 1000,
      Math.round(pt.y * 1000) / 1000,
    ]),
  }));
  return JSON.stringify(compact);
}

exports.onDoodleCreated = onDocumentCreated("doodles/{doodleId}", async (event) => {
  const snapshot = event.data;
  if (!snapshot) {
    logger.warn("No data in event, exiting");
    return;
  }

  const doodle = snapshot.data();
  const doodleId = event.params.doodleId;
  const receiverId = typeof doodle.receiverId === "string" ? doodle.receiverId : "";
  const senderId = typeof doodle.senderId === "string" ? doodle.senderId : "";

  if (!receiverId || !senderId || receiverId === senderId) {
    logger.warn("Doodle has no valid sender/receiver, exiting", {doodleId});
    return;
  }

  const db = getFirestore();
  const receiverRef = db.collection("users").doc(receiverId);
  const friendships = db.collection("friendships");
  let userDoc; let muteDoc; let senderDoc; let asA; let asB; let blockedByReceiver; let blockedBySender;
  try {
    [userDoc, muteDoc, senderDoc, asA, asB, blockedByReceiver, blockedBySender] = await Promise.all([
      receiverRef.get(),
      receiverRef.collection("mutes").doc(senderId).get(),
      db.collection("users").doc(senderId).get(),
      friendships.where("userA", "==", senderId).where("userB", "==", receiverId).limit(1).get(),
      friendships.where("userA", "==", receiverId).where("userB", "==", senderId).limit(1).get(),
      receiverRef.collection("blocks").doc(senderId).get(),
      db.collection("users").doc(senderId).collection("blocks").doc(receiverId).get(),
    ]);
  } catch (error) {
    // No retries are configured, so log enough to find the lost push.
    logger.error("Doodle push lookups failed", {doodleId, receiverId, error: error.message});
    return;
  }

  // Muting only silences the push (and so the overlay). The doodle doc itself
  // is still written, so it stays in the receiver's Vault and unmuting loses
  // nothing. The sender is never told.
  let pushToken;
  try {
    pushToken = await readPushToken(receiverRef, userDoc);
  } catch (error) {
    logger.error("Doodle push token lookup failed", {doodleId, receiverId, error: error.message});
    return;
  }
  const fcmToken = pushToken.token;
  const decision = decideDoodlePush({
    senderId,
    receiverId,
    blocked: blockedByReceiver.exists || blockedBySender.exists,
    areFriends: !asA.empty || !asB.empty,
    muted: muteDoc.exists,
    receiverExists: userDoc.exists,
    fcmToken,
  });
  if (!decision.send) {
    logger.info("Skipping doodle push", {doodleId, receiverId, reason: decision.reason});
    return;
  }

  // The overlay names the sender from their own profile, not from the doodle doc,
  // which the sender wrote and could have labelled with anyone's name.
  const senderUsername = displayName(senderDoc.exists ? senderDoc.data().username : undefined);

  const strokesJson = serializeStrokes(doodle.strokes);
  // FCM data messages cap at 4KB total; leave ~500 bytes for the other fields.
  const MAX_STROKES_BYTES = 3600;
  const pointCount = (doodle.strokes || []).reduce((n, s) => n + ((s.points || []).length), 0);
  const strokesSize = Buffer.byteLength(strokesJson, "utf8");

  const data = {
    type: "new_doodle",
    doodleId: doodleId,
    // The app drops pushes whose receiverId isn't the signed-in account (defence in
    // depth: tokens were public before Phase 2 and may linger on old profiles).
    receiverId: receiverId,
    senderId: senderId,
    senderUsername: senderUsername,
  };
  if (typeof doodle.cardId === "string" && doodle.cardId) data.cardId = doodle.cardId.slice(0, 32);

  if (strokesSize <= MAX_STROKES_BYTES) {
    data.strokes = strokesJson;
  } else {
    logger.warn("Doodle too large to embed in push, receiver will need to fetch it", {doodleId, strokesSize, pointCount});
  }

  const message = {
    token: fcmToken,
    data: data,
    android: {
      priority: "high",
    },
  };

  try {
    const response = await getMessaging().send(message);
    logger.info("Push sent successfully", {doodleId, receiverId, tokenSource: pushToken.source, embedded: strokesSize <= MAX_STROKES_BYTES, strokesSize, pointCount, response});
  } catch (error) {
    logger.error("Failed to send push", {doodleId, receiverId, error: error.message, code: error.code});
    if (isDeadTokenError(error)) await cleanUpDeadToken(receiverRef, fcmToken, {doodleId, receiverId});
  }
});

exports.onChatMessageCreated = onDocumentCreated("chats/{chatId}/messages/{messageId}", async (event) => {
  const snapshot = event.data;
  if (!snapshot) {
    logger.warn("No data in chat message event, exiting");
    return;
  }

  const message = snapshot.data();
  const {chatId, messageId} = event.params;
  const senderId = message.senderId;

  // Theme changes show up in the chat itself; only text messages notify.
  if (message.kind !== "text") {
    logger.info("Not a text message, skipping push", {chatId, messageId, kind: message.kind});
    return;
  }
  if (!senderId) {
    logger.warn("Chat message has no senderId, exiting", {chatId, messageId});
    return;
  }

  const db = getFirestore();
  const chatDoc = await db.collection("chats").doc(chatId).get();
  if (!chatDoc.exists) {
    logger.warn("Chat document not found", {chatId, messageId});
    return;
  }
  const members = chatDoc.data().members || [];
  const recipientId = members.find((uid) => uid !== senderId);
  if (!members.includes(senderId) || !recipientId) {
    logger.warn("Sender isn't a member or chat has no recipient", {chatId, messageId});
    return;
  }

  // The rules can't check friendship (friendship docs have random ids), so check it here:
  // a stranger who creates a chat with someone gets no push out of it.
  const friendships = db.collection("friendships");
  const [asA, asB] = await Promise.all([
    friendships.where("userA", "==", senderId).where("userB", "==", recipientId).limit(1).get(),
    friendships.where("userA", "==", recipientId).where("userB", "==", senderId).limit(1).get(),
  ]);
  if (asA.empty && asB.empty) {
    logger.info("Not friends, skipping chat push", {chatId, messageId});
    return;
  }

  const recipientRef = db.collection("users").doc(recipientId);
  const [recipientDoc, muteDoc, senderDoc, blockedByRecipient, blockedBySender] = await Promise.all([
    recipientRef.get(),
    recipientRef.collection("mutes").doc(senderId).get(),
    db.collection("users").doc(senderId).get(),
    recipientRef.collection("blocks").doc(senderId).get(),
    db.collection("users").doc(senderId).collection("blocks").doc(recipientId).get(),
  ]);

  // The rules refuse messages between blocked people; this catches one sent just before.
  if (blockedByRecipient.exists || blockedBySender.exists) {
    logger.info("Blocked, skipping chat push", {chatId, messageId});
    return;
  }

  // Same mute semantics as doodles: the message is still stored and shows in the chat,
  // only the push is skipped. The sender is never told.
  if (muteDoc.exists) {
    logger.info("Recipient muted sender, skipping chat push", {chatId, messageId, recipientId});
    return;
  }

  if (!recipientDoc.exists) {
    logger.warn("Recipient user document not found", {recipientId});
    return;
  }

  let pushToken;
  try {
    pushToken = await readPushToken(recipientRef, recipientDoc);
  } catch (error) {
    logger.error("Chat push token lookup failed", {chatId, messageId, error: error.message});
    return;
  }
  const fcmToken = pushToken.token;
  if (!fcmToken) {
    logger.warn("Recipient has no fcmToken saved", {recipientId});
    return;
  }

  const senderUsername = String((senderDoc.exists && senderDoc.data().username) || "A friend").slice(0, 32);
  // First 200 characters, counted by code point so an emoji is never cut in half.
  const text = Array.from(String(message.text || "")).slice(0, 200).join("");

  const push = {
    token: fcmToken,
    data: {
      type: "chat_message",
      chatId: chatId,
      receiverId: recipientId,
      senderId: senderId,
      senderUsername: senderUsername,
      text: text,
    },
    android: {
      priority: "high",
    },
  };

  try {
    const response = await getMessaging().send(push);
    logger.info("Chat push sent successfully", {chatId, messageId, recipientId, response});
  } catch (error) {
    logger.error("Failed to send chat push", {chatId, messageId, recipientId, error: error.message, code: error.code});
    if (isDeadTokenError(error)) await cleanUpDeadToken(recipientRef, fcmToken, {chatId, messageId, recipientId});
  }
});
