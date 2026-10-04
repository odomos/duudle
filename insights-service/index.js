const express = require('express');
const { initializeApp, cert } = require('firebase-admin/app');
const { getFirestore } = require('firebase-admin/firestore');
const serviceAccount = require('./serviceAccountKey.json');

initializeApp({
  credential: cert(serviceAccount)
});

const db = getFirestore();
const app = express();
const PORT = process.env.PORT || 3000;

const doodleCountBySender = {};
let initialLoadComplete = false;

function incrementCount(uid) {
  if (!uid) return;
  doodleCountBySender[uid] = (doodleCountBySender[uid] || 0) + 1;
}

// Firestore's onSnapshot fires once immediately with every existing document
// marked as "added" — this one listener handles the initial load AND every
// new doodle afterward, so a separate one-time get() would only double-count.
db.collection('doodles').onSnapshot((snapshot) => {
  snapshot.docChanges().forEach((change) => {
    if (change.type === 'added') {
      const senderId = change.doc.data().senderId;
      incrementCount(senderId);
      if (initialLoadComplete) {
        console.log(`New doodle counted for sender ${senderId}`);
      }
    }
  });
  if (!initialLoadComplete) {
    initialLoadComplete = true;
    console.log(`Initial load complete: ${snapshot.size} doodles counted`);
  }
}, (error) => {
  console.error('Firestore listener error:', error.message);
});

app.get('/health', (req, res) => {
  res.json({ status: 'ok', service: 'duudle-insights-service' });
});

app.get('/stats/:uid', (req, res) => {
  const uid = req.params.uid;
  const count = doodleCountBySender[uid] || 0;
  res.json({ uid, doodlesSent: count });
});

app.listen(PORT, () => {
  console.log(`Duudle Insights Service listening on port ${PORT}`);
});