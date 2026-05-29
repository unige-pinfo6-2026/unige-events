// Shared config + small helpers. All values come from load-testing/.env via __ENV.
import { sleep } from 'k6';

export const BASE_URL = (__ENV.BASE_URL || 'https://pinfo6.p-info.net/api').replace(/\/+$/, '');
export const POOL_SIZE = Number(__ENV.POOL_SIZE || 50);

export const AUTH0 = {
  domain: __ENV.AUTH0_DOMAIN,
  audience: __ENV.AUTH0_AUDIENCE,
  connection: __ENV.AUTH0_CONNECTION,
  clientId: __ENV.AUTH0_CLIENT_ID,
  clientSecret: __ENV.AUTH0_CLIENT_SECRET || '',
  emailPrefix: __ENV.AUTH0_TEST_EMAIL_PREFIX || 'loadtest',
  emailDomain: __ENV.AUTH0_TEST_EMAIL_DOMAIN || 'example.com',
  password: __ENV.AUTH0_TEST_PASSWORD,
};

// think-time between user steps (seconds)
export function think(min = 1, max = 4) {
  sleep(min + Math.random() * (max - min));
}

export function randItem(arr) {
  return arr && arr.length ? arr[Math.floor(Math.random() * arr.length)] : null;
}

// LocalDateTime strings (no timezone) — backend expects YYYY-MM-DDTHH:mm:ss, startDate @Future
export function futureIso(daysAhead, durationHours = 2) {
  const s = new Date(Date.now() + daysAhead * 86400000);
  const e = new Date(s.getTime() + durationHours * 3600000);
  const fmt = (d) => d.toISOString().slice(0, 19);
  return { start: fmt(s), end: fmt(e) };
}
