// Smoke: 1 VU, a few iterations of browse + writes. Validates auth, correlation, every request type.
// Uses k6's default end-of-test summary (no handleSummary) for maximum robustness.
import { buildOptions } from '../lib/options.js';

export const options = buildOptions('smoke');

export { setup, browse, writes, teardown } from '../lib/entry.js';
