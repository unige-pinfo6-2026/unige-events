import { describe, expect, it } from 'vitest'
import { userDisplayLabel, userInitials } from '@/utils/displayName'

describe('userDisplayLabel', () => {
  it('returns the displayName when non-empty', () => {
    expect(userDisplayLabel('Alice')).toBe('Alice')
  })

  it('trims surrounding whitespace', () => {
    expect(userDisplayLabel('  Alice  ')).toBe('Alice')
  })

  // SCRUM-169 — @username is the preferred fallback over UUID prefix.

  it('falls back to @username when displayName is null', () => {
    expect(userDisplayLabel(null, 'alice.martin')).toBe('@alice.martin')
  })

  it('falls back to @username when displayName is blank', () => {
    expect(userDisplayLabel('   ', 'alice.martin')).toBe('@alice.martin')
  })

  it('falls back to UUID prefix when displayName and username are both absent', () => {
    expect(userDisplayLabel(null, null, 'abcdef12-3456-7890-abcd-ef1234567890')).toBe('abcdef12')
  })

  it('falls back to UUID prefix when username is undefined (soft transition)', () => {
    expect(userDisplayLabel(null, undefined, 'abcdef12-3456-7890-abcd-ef1234567890')).toBe('abcdef12')
  })

  it('returns "Utilisateur" when nothing identifies the user', () => {
    expect(userDisplayLabel(null)).toBe('Utilisateur')
    expect(userDisplayLabel(undefined, undefined, undefined)).toBe('Utilisateur')
  })

  it('prefers displayName over username over UUID', () => {
    // Stable priority order regardless of which extra args are provided.
    expect(userDisplayLabel('Alice', 'alice.martin', '19f3ab78-0fbf-4cfb-896e-5c0346fabed5'))
      .toBe('Alice')
    expect(userDisplayLabel(null, 'alice.martin', '19f3ab78-0fbf-4cfb-896e-5c0346fabed5'))
      .toBe('@alice.martin')
  })
})

describe('userInitials', () => {
  it('returns the first 2 chars uppercased', () => {
    expect(userInitials('alice')).toBe('AL')
  })

  it('handles short names', () => {
    expect(userInitials('A')).toBe('A')
  })

  it('returns "??" when displayName is null or blank', () => {
    expect(userInitials(null)).toBe('??')
    expect(userInitials('   ')).toBe('??')
  })
})
