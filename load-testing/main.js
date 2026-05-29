// Entry point — profile selected via __ENV.PROFILE (load | stress | spike | soak | smoke). Default: load.
import { buildOptions } from './lib/options.js';

export const options = buildOptions(__ENV.PROFILE || 'load');

export { setup, browse, writes, teardown } from './lib/entry.js';
export { handleSummary } from './lib/summary.js';
