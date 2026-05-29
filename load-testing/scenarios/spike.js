// Spike: sudden burst to 200 VUs, hold briefly, drop. Tests tunnel + Kong burst resilience.
import { buildOptions } from '../lib/options.js';

export const options = buildOptions('spike');

export { setup, browse, writes, teardown } from '../lib/entry.js';
export { handleSummary } from '../lib/summary.js';
