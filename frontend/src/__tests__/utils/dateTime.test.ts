import { describe, expect, it } from 'vitest'
import {
  formatEventDateTime,
  formatEventDateTimeCompact,
  parseApiUtcDateTime,
  toLocalDateTimeInputValue,
} from '../../utils/dateTime'

describe('dateTime utils', () => {
  it('parses timezone-less API values as UTC', () => {
    const parsed = parseApiUtcDateTime('2026-04-10T18:45:00')
    expect(parsed.toISOString()).toBe('2026-04-10T18:45:00.000Z')
  })

  it('keeps explicit timezone values unchanged', () => {
    const parsed = parseApiUtcDateTime('2026-04-10T18:45:00+02:00')
    expect(parsed.toISOString()).toBe('2026-04-10T16:45:00.000Z')
  })

  it('returns a local datetime-local-compatible value', () => {
    expect(toLocalDateTimeInputValue('2026-04-10T18:45:30Z')).toMatch(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/)
  })

  it('formats long and compact event dates', () => {
    const full = formatEventDateTime('2026-04-10T18:45:00Z')
    const compact = formatEventDateTimeCompact('2026-04-10T18:45:00Z')

    expect(full.length).toBeGreaterThan(0)
    expect(compact.length).toBeGreaterThan(0)
  })

  it('omits time when allDay is true (long)', () => {
    const withTime = formatEventDateTime('2026-04-10T14:00:00Z', false)
    const allDay = formatEventDateTime('2026-04-10T14:00:00Z', true)

    expect(withTime).toMatch(/\d{2}:\d{2}/)
    expect(allDay).not.toMatch(/\d{2}:\d{2}/)
    expect(allDay).toMatch(/avril/)
  })

  it('omits time when allDay is true (compact)', () => {
    const withTime = formatEventDateTimeCompact('2026-04-10T14:00:00Z', false)
    const allDay = formatEventDateTimeCompact('2026-04-10T14:00:00Z', true)

    expect(withTime).toMatch(/\d{2}:\d{2}/)
    expect(allDay).not.toMatch(/\d{2}:\d{2}/)
  })
})
