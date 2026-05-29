// v2 — Cross-VU notification_lag probe (Grafana Cloud). User A follows user B; polls B's unread count
// for the Kafka -> notification visibility delay. Low arrival rate (respects the follow 30/min cap).
// Self-actions don't notify the actor, so this measures the real cross-user fan-out (RESULTS.md #6).
// Run with: k6 cloud run scenarios/notify.js
import { buildOptions } from '../lib/options.js';

export const options = buildOptions('cloud_notify');

export { setup, notifyProbe, teardown } from '../lib/entry.js';
export { handleSummary } from '../lib/summary.js';
