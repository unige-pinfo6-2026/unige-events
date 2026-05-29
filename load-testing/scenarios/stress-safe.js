// Prod-safe stress: ramp 50 -> 200 VUs against UN-throttled GET endpoints only
// (events/featured, events/search, events/{id}, events/{id}/comments). Anonymous, read-only,
// non-polluting. Finds the real tunnel/Kong/service knee without the /events 10/min IP cap.
// Run with: -e SKIP_AUTH=1 (no token pool needed).
import { buildOptions } from '../lib/options.js';

export const options = buildOptions('stress_nolist');

export { setup, browseReadOnly, teardown } from '../lib/entry.js';
export { handleSummary } from '../lib/summary.js';
