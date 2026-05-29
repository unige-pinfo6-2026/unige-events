// Stress: ramp 50 -> 200 VUs to find the knee. Run on a coordinated window (prod target).
import { buildOptions } from '../lib/options.js';

export const options = buildOptions('stress');

export { setup, browse, writes, teardown } from '../lib/entry.js';
export { handleSummary } from '../lib/summary.js';
