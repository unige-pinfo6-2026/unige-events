import { describe, expect, it } from 'vitest'
import { formatRelativeTime } from '@/utils/formatRelativeTime'

const NOW = new Date('2026-04-13T12:00:00.000Z')

function minusSeconds(seconds: number): string {
  return new Date(NOW.getTime() - seconds * 1000).toISOString()
}

describe('formatRelativeTime', () => {
  it("returns 'à l'instant' for under a minute", () => {
    expect(formatRelativeTime(minusSeconds(30), NOW)).toBe("à l'instant")
  })

  it('returns minutes under an hour', () => {
    expect(formatRelativeTime(minusSeconds(120), NOW)).toBe('il y a 2 min')
  })

  it('returns hours under a day', () => {
    expect(formatRelativeTime(minusSeconds(2 * 60 * 60), NOW)).toBe('il y a 2 h')
  })

  it("returns 'hier' the day before", () => {
    expect(formatRelativeTime(minusSeconds(36 * 60 * 60), NOW)).toBe('hier')
  })

  it('returns days under a week', () => {
    expect(formatRelativeTime(minusSeconds(3 * 24 * 60 * 60), NOW)).toBe('il y a 3 jours')
  })

  it('returns weeks', () => {
    expect(formatRelativeTime(minusSeconds(14 * 24 * 60 * 60), NOW)).toBe('il y a 2 semaines')
  })

  it('returns the singular week form (w === 1)', () => {
    // 8 days = 1 week (floor), exercises the `w === 1` true branch @line 32.
    expect(formatRelativeTime(minusSeconds(8 * 24 * 60 * 60), NOW)).toBe('il y a 1 semaine')
  })

  it('returns months', () => {
    expect(formatRelativeTime(minusSeconds(60 * 24 * 60 * 60), NOW)).toBe('il y a 2 mois')
  })

  it('returns years', () => {
    expect(formatRelativeTime(minusSeconds(400 * 24 * 60 * 60), NOW)).toBe('il y a 1 an')
  })

  it('returns the plural year form (years > 1)', () => {
    // 800 days = 2 years (floor), exercises the `years === 1` false branch @line 39.
    expect(formatRelativeTime(minusSeconds(800 * 24 * 60 * 60), NOW)).toBe('il y a 2 ans')
  })

  it('returns empty string for invalid input', () => {
    expect(formatRelativeTime('not-a-date', NOW)).toBe('')
  })
})
