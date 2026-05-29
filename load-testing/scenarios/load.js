// Nominal load: ramp to ~50 VUs, hold, ramp down. Steady-state SLO measurement.
import { buildOptions } from '../lib/options.js';

export const options = buildOptions('load');

export { setup, browse, writes, teardown } from '../lib/entry.js';
export { handleSummary } from '../lib/summary.js';
