const STORAGE_KEY = 'unige_session_id'
const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i

/**
 * Returns a stable UUID v4 from `localStorage[unige_session_id]`. Creates and
 * persists one on first invocation. Used to de-duplicate anonymous event
 * views server-side (cf. backend `EventViewService` post-V11 schema).
 *
 * RGPD: no PII, no fingerprint — just a random UUID that rotates if the user
 * clears their localStorage. Authenticated callers ignore this and use their
 * user UUID.
 *
 * Returns the existing value if it's already a valid UUID v4; otherwise
 * regenerates and overwrites — defensive against tampered storage.
 */
export function getOrCreateSessionId(): string {
  try {
    const existing = localStorage.getItem(STORAGE_KEY)
    if (existing !== null && UUID_RE.test(existing)) {
      return existing
    }
    const fresh = generateUuidV4()
    localStorage.setItem(STORAGE_KEY, fresh)
    return fresh
  } catch {
    // localStorage can throw in private mode or with quota exceeded —
    // generate a one-off UUID per call as fallback.
    return generateUuidV4()
  }
}

function generateUuidV4(): string {
  // crypto.randomUUID() is supported in all modern browsers (Chrome 92+,
  // Firefox 95+, Safari 15.4+) and Node 14.17+. Our target = ES2022 (cf.
  // tsconfig.app.json), so this is always available — no Math.random
  // polyfill needed.
  return crypto.randomUUID()
}
