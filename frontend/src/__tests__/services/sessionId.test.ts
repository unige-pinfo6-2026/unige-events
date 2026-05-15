import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { getOrCreateSessionId } from '@/services/sessionId'

const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i
const KEY = 'unige_session_id'

beforeEach(() => {
  localStorage.clear()
})

afterEach(() => {
  localStorage.clear()
})

describe('sessionId', () => {
  it('creates a UUID on first call', () => {
    const id = getOrCreateSessionId()
    expect(UUID_RE.test(id)).toBe(true)
    expect(localStorage.getItem(KEY)).toBe(id)
  })

  it('returns the same UUID on subsequent calls (persistent)', () => {
    const a = getOrCreateSessionId()
    const b = getOrCreateSessionId()
    expect(a).toBe(b)
  })

  it('regenerates if the stored value is not a valid UUID', () => {
    localStorage.setItem(KEY, 'tampered')
    const fresh = getOrCreateSessionId()
    expect(UUID_RE.test(fresh)).toBe(true)
    expect(fresh).not.toBe('tampered')
    expect(localStorage.getItem(KEY)).toBe(fresh)
  })

  it('honors an existing valid UUID', () => {
    const preset = '11111111-2222-4333-8444-555555555555'
    localStorage.setItem(KEY, preset)
    expect(getOrCreateSessionId()).toBe(preset)
  })
})
