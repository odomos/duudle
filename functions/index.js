const {onDocumentCreated} = require("firebase-functions/v2/firestore");
const {initializeApp} = require("firebase-admin/app");
const {getFirestore} = require("firebase-admin/firestore");
const {getMessaging} = require("firebase-admin/messaging");
const logger = require("firebase-functions/logger");

initializeApp();

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
  const receiverId = doodle.receiverId;
  const senderUsername = doodle.senderUsername || "A friend";

  if (!receiverId) {
    logger.warn("Doodle has no receiverId, exiting", {doodleId});
    return;
  }

  const userDoc = await getFirestore().collection("users").doc(receiverId).get();

  if (!userDoc.exists) {
    logger.warn("Receiver user document not found", {receiverId});
    return;
  }

  const fcmToken = userDoc.data().fcmToken;
  if (!fcmToken) {
    logger.warn("Receiver has no fcmToken saved", {receiverId});
    return;
  }

  const strokesJson = serializeStrokes(doodle.strokes);
  const MAX_STROKES_BYTES = 3000;
  const strokesSize = Buffer.byteLength(strokesJson, "utf8");

  const data = {
    type: "new_doodle",
    doodleId: doodleId,
    senderId: doodle.senderId || "",
    senderUsername: senderUsername,
  };

  if (strokesSize <= MAX_STROKES_BYTES) {
    data.strokes = strokesJson;
  } else {
    logger.warn("Doodle too large to embed in push, receiver will need to fetch it", {doodleId, strokesSize});
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
    logger.info("Push sent successfully", {doodleId, receiverId, embedded: strokesSize <= MAX_STROKES_BYTES, response});
  } catch (error) {
    logger.error("Failed to send push", {doodleId, receiverId, error: error.message});
  }
});