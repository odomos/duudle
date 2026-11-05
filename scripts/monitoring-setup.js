// Phase 7b: Duudle's alert policies, as code. Creates (or updates, matched by display name)
// the email channel and the alert policies on one project, using the Firebase CLI's login.
// Run from the laptop, staging first, live only with the owner's OK (see MONITORING.md):
//   node scripts/monitoring-setup.js "$(npm root -g)/firebase-tools" duudle-staging doodlebyarnav425@gmail.com
const path = require("path");
const [ft, project, email] = process.argv.slice(2);
const RUNBOOK = "Runbook: MONITORING.md in the Duudle repo.";

const logAlert = (name, filter, rateSeconds, doc) => ({
  displayName: name,
  documentation: { content: `${doc}\n\n${RUNBOOK}`, mimeType: "text/markdown" },
  combiner: "OR",
  conditions: [{ displayName: name, conditionMatchedLog: { filter } }],
  alertStrategy: { notificationRateLimit: { period: `${rateSeconds}s` }, autoClose: "1800s" },
});

// Firestore reports each value under module "" and "__unknown__"; group by module so the
// count isn't doubled (a spike may then open two incidents, but never zero).
const firestoreBurst = (name, filter, threshold, doc) => ({
  displayName: name,
  documentation: { content: `${doc}\n\n${RUNBOOK}`, mimeType: "text/markdown" },
  combiner: "OR",
  conditions: [{
    displayName: name,
    conditionThreshold: {
      filter: `resource.type = "firestore_instance" AND ${filter}`,
      comparison: "COMPARISON_GT",
      thresholdValue: threshold,
      duration: "0s",
      aggregations: [{
        alignmentPeriod: "600s", perSeriesAligner: "ALIGN_SUM",
        crossSeriesReducer: "REDUCE_SUM", groupByFields: ["metric.label.module"],
      }],
      trigger: { count: 1 },
    },
  }],
  alertStrategy: { autoClose: "3600s" },
});

const POLICIES = [
  logAlert("Duudle: Cloud Function error",
    '(resource.type="cloud_run_revision" OR resource.type="cloud_function") AND severity>=ERROR',
    1800, "A Cloud Function logged an error (push send, chat push, account deletion or report logging). At most one email per 30 minutes."),
  logAlert("Duudle: new report",
    '(resource.type="cloud_run_revision" OR resource.type="cloud_function") AND jsonPayload.event="report_created"',
    300, "Someone filed a report. Review it in the Firestore console (reports collection) as MODERATION.md describes. Several reports within 5 minutes send one email."),
  firestoreBurst("Duudle: burst of refused requests",
    'metric.type = "firestore.googleapis.com/rules/evaluation_count" AND metric.label.result = "DENY"',
    100, "More than 100 Firestore requests were refused by the rules in 10 minutes: a rate-limited or scripted client, or an app/rules mismatch after a deploy."),
  firestoreBurst("Duudle: burst of writes",
    'metric.type = "firestore.googleapis.com/document/write_count"',
    2000, "More than 2000 Firestore document writes in 10 minutes, far above normal use. Check for spam or a client stuck in a loop."),
];

(async () => {
  const auth = require(path.join(ft, "lib/auth.js"));
  const token = (await auth.getAccessToken(auth.getGlobalDefaultAccount().tokens.refresh_token, [])).access_token;
  const call = async (method, url, body) => {
    const r = await fetch(url, {
      method, body: body && JSON.stringify(body),
      headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json", "x-goog-user-project": project },
    });
    const j = await r.json();
    if (!r.ok) throw new Error(`${method} ${url.split("/v3/")[1]}: ${JSON.stringify(j.error || j).slice(0, 400)}`);
    return j;
  };
  const base = `https://monitoring.googleapis.com/v3/projects/${project}`;

  const channelName = `Duudle alerts (${email})`;
  const channels = (await call("GET", `${base}/notificationChannels`)).notificationChannels || [];
  let channel = channels.find((c) => c.type === "email" && c.labels && c.labels.email_address === email);
  if (!channel) {
    channel = await call("POST", `${base}/notificationChannels`, { type: "email", displayName: channelName, labels: { email_address: email } });
    console.log("channel created:", channel.name);
  } else console.log("channel exists:", channel.name);

  const existing = (await call("GET", `${base}/alertPolicies`)).alertPolicies || [];
  for (const p of POLICIES) {
    const body = { ...p, enabled: true, notificationChannels: [channel.name] };
    const found = existing.find((e) => e.displayName === p.displayName);
    if (found) {
      await call("PATCH", `https://monitoring.googleapis.com/v3/${found.name}`, { ...body, name: found.name });
      console.log("policy updated:", p.displayName);
    } else {
      const made = await call("POST", `${base}/alertPolicies`, body);
      console.log("policy created:", p.displayName, made.name.split("/").pop());
    }
  }
})().catch((e) => { console.log("failed:", e.message); process.exitCode = 1; });
