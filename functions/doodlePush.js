// Pure decision logic for onDoodleCreated, kept free of Firebase so it can be unit
// tested with `node --test` (functions/test). index.js gathers the facts; this decides.

const MAX_NAME_LENGTH = 32;

/** The name shown on the overlay: the sender's stored username, never the doodle's copy. */
function displayName(storedUsername) {
  const name = typeof storedUsername === "string" ? storedUsername.trim() : "";
  return (name || "A friend").slice(0, MAX_NAME_LENGTH);
}

/**
 * Should this doodle pop up on the receiver's phone?
 * @param {object} facts
 * @param {string|undefined} facts.senderId
 * @param {string|undefined} facts.receiverId
 * @param {boolean} facts.blocked      either of the two has blocked the other
 * @param {boolean} facts.areFriends   a friendship doc links the two, either direction
 * @param {boolean} facts.muted        receiver muted the sender
 * @param {boolean} facts.receiverExists
 * @param {string|undefined} facts.fcmToken
 * @returns {{send: boolean, reason: string}}
 */
function decideDoodlePush(facts) {
  if (!facts.receiverId) return {send: false, reason: "no-receiver"};
  if (!facts.senderId) return {send: false, reason: "no-sender"};
  if (facts.senderId === facts.receiverId) return {send: false, reason: "self"};
  // The rules already refuse doodles between blocked people; this also catches one
  // written just before the block landed.
  if (facts.blocked) return {send: false, reason: "blocked"};
  // Rules can't check friendship (random friendship ids), so this is where a stranger's
  // doodle is stopped from popping up. It still lands in the receiver's Vault list.
  if (!facts.areFriends) return {send: false, reason: "not-friends"};
  if (facts.muted) return {send: false, reason: "muted"};
  if (!facts.receiverExists) return {send: false, reason: "no-receiver-doc"};
  if (!facts.fcmToken) return {send: false, reason: "no-token"};
  return {send: true, reason: "ok"};
}

const DEFAULT_WIDTH = 8;
const round3 = (n) => Math.round(n * 1000) / 1000;

/**
 * The doodle's strokes in the compact push format [{c, w, p: [[x, y], ...]}], keeping what
 * the app's own parser (parseDoodleDoc) would keep: a stroke needs a points list and a
 * numeric colour, a point needs finite x and y, a missing width becomes 8. Anything else
 * is dropped instead of crashing the function or embedding nulls the app can't parse.
 * @returns {{strokes: Array, pointCount: number}}
 */
function compactStrokes(raw) {
  const strokes = [];
  let pointCount = 0;
  for (const s of Array.isArray(raw) ? raw : []) {
    if (!s || typeof s !== "object" || !Array.isArray(s.points) || !Number.isFinite(s.color)) continue;
    const p = [];
    for (const pt of s.points) {
      if (pt && Number.isFinite(pt.x) && Number.isFinite(pt.y)) p.push([round3(pt.x), round3(pt.y)]);
    }
    if (p.length === 0) continue;
    const w = Number.isFinite(s.width) && s.width > 0 ? s.width : DEFAULT_WIDTH;
    strokes.push({c: s.color | 0, w, p}); // | 0: a 32-bit ARGB int, like the app writes
    pointCount += p.length;
  }
  return {strokes, pointCount};
}

module.exports = {displayName, decideDoodlePush, compactStrokes, MAX_NAME_LENGTH};
