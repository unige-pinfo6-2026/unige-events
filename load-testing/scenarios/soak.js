// Soak (optional): 50 VUs for 1h. Surfaces leaks/connection exhaustion. Mind Auth0 token TTL.
import { buildOptions } from '../lib/options.js';

export const options = buildOptions('soak');

export { setup, browse, writes, teardown } from '../lib/entry.js';
export { handleSummary } from '../lib/summary.js';
