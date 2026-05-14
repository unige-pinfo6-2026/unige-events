import { describe, expect, it } from 'vitest'
import { userDisplayLabel, userInitials } from '@/utils/displayName'

describe('userDisplayLabel', () => {
  it('returns the displayName when non-empty', () => {
    expect(userDisplayLabel('Alice')).toBe('Alice')
  })

  it('trims surrounding whitespace', () => {
    expect(userDisplayLabel('  Alice  ')).toBe('Alice')
  })

  it('falls back to the UUID prefix when displayName is null', () => {
    expect(userDisplayLabel(null, 'abcdef12-3456-7890-abcd-ef1234567890')).toBe('abcdef12')
  })

  it('falls back to the UUID prefix when displayName is blank', () => {
    expect(userDisplayLabel('   ', 'abcdef12-3456-7890-abcd-ef1234567890')).toBe('abcdef12')
  })

  it('returns "Utilisateur" when neither displayName nor userId is available', () => {
    expect(userDisplayLabel(null)).toBe('Utilisateur')
    expect(userDisplayLabel(undefined, undefined)).toBe('Utilisateur')
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
