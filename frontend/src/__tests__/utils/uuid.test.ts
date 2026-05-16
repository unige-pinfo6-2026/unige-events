import { describe, expect, it } from 'vitest'
import { isUuid } from '@/utils/uuid'

describe('isUuid', () => {
  it.each([
    'a4ab9d0a-3e1c-4b6e-9a8d-0c1e2f3a4b5c',
    'A4AB9D0A-3E1C-4B6E-9A8D-0C1E2F3A4B5C',
    '00000000-0000-0000-0000-000000000000',
  ])('accepts canonical UUID %s', (value) => {
    expect(isUuid(value)).toBe(true)
  })

  it.each([
    'me',
    'auth0|abc123',
    'a4ab9d0a-3e1c-4b6e-9a8d-0c1e2f3a4b5',
    'a4ab9d0a-3e1c-4b6e-9a8d-0c1e2f3a4b5cZ',
    'not-a-uuid',
    '',
  ])('rejects non-UUID %s', (value) => {
    expect(isUuid(value)).toBe(false)
  })

  it('rejects undefined and null', () => {
    expect(isUuid(undefined)).toBe(false)
    expect(isUuid(null)).toBe(false)
  })

  it('narrows the type to string on success (compile-time assertion)', () => {
    const value: string | undefined = 'a4ab9d0a-3e1c-4b6e-9a8d-0c1e2f3a4b5c'
    if (isUuid(value)) {
      // If `isUuid` were not a type guard this `.length` would be a TS error.
      expect(value.length).toBe(36)
    }
  })
})
