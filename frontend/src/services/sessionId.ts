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
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }
  // Polyfill fallback (browsers without crypto.randomUUID — rare).
  const bytes = new Uint8Array(16)
  if (typeof crypto !== 'undefined') crypto.getRandomValues(bytes)
  else for (let i = 0; i < bytes.length; i++) bytes[i] = Math.floor(Math.random() * 256)
  bytes[6] = (bytes[6] & 0x0f) | 0x40
  bytes[8] = (bytes[8] & 0x3f) | 0x80
  const hex = Array.from(bytes, (b) => b.toString(16).padStart(2, '0'))
  return `${hex.slice(0, 4).join('')}-${hex.slice(4, 6).join('')}-${hex.slice(6, 8).join('')}-${hex.slice(8, 10).join('')}-${hex.slice(10, 16).join('')}`
}
