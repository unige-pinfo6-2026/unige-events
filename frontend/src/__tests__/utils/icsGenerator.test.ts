import { describe, it, expect } from 'vitest'
import { generateIcs, buildGoogleCalendarUrl } from '@/utils/icsGenerator'
import type { Event } from '@/types/event'

const mockEvent: Event = {
  id: 42,
  title: 'Test Event',
  description: 'A test description',
  location: 'Room 101',
  startDate: '2025-06-15T10:00:00',
  endDate: '2025-06-15T12:00:00',
  category: 'ACADEMIC',
  creatorId: 'auth0|123',
  status: 'PUBLISHED',
  createdAt: '2025-01-01T00:00:00',
}

describe('generateIcs', () => {
  it('formats dates as UTC YYYYMMDDTHHmmssZ', () => {
    const result = generateIcs(mockEvent)
    expect(result).toContain('DTSTART:20250615T100000Z')
    expect(result).toContain('DTEND:20250615T120000Z')
  })

  it('includes correct envelope, PRODID, VERSION and UID', () => {
    const result = generateIcs(mockEvent)
    expect(result).toContain('BEGIN:VCALENDAR')
    expect(result).toContain('VERSION:2.0')
    expect(result).toContain('PRODID:-//UNIGE Events//EN')
    expect(result).toContain('BEGIN:VEVENT')
    expect(result).toContain(`UID:42@unige-events`)
    expect(result).toContain('END:VEVENT')
    expect(result).toContain('END:VCALENDAR')
  })

  it('includes SUMMARY and LOCATION', () => {
    const result = generateIcs(mockEvent)
    expect(result).toContain('SUMMARY:Test Event')
    expect(result).toContain('LOCATION:Room 101')
  })

  it('escapes commas in title', () => {
    const event: Event = { ...mockEvent, title: 'Event, with comma' }
    expect(generateIcs(event)).toContain('SUMMARY:Event\\, with comma')
  })

  it('escapes semicolons in title', () => {
    const event: Event = { ...mockEvent, title: 'Event; with semicolon' }
    expect(generateIcs(event)).toContain('SUMMARY:Event\\; with semicolon')
  })

  it('escapes backslashes in title', () => {
    const event: Event = { ...mockEvent, title: 'Event\\backslash' }
    expect(generateIcs(event)).toContain('SUMMARY:Event\\\\backslash')
  })

  it('escapes newlines in description', () => {
    const event: Event = { ...mockEvent, description: 'Line 1\nLine 2' }
    expect(generateIcs(event)).toContain('DESCRIPTION:Line 1\\nLine 2')
  })

  it('includes DESCRIPTION when present', () => {
    const result = generateIcs(mockEvent)
    expect(result).toContain('DESCRIPTION:A test description')
  })

  it('omits DESCRIPTION when description is absent', () => {
    const event: Event = { ...mockEvent, description: undefined }
    expect(generateIcs(event)).not.toContain('DESCRIPTION')
  })

  it('omits DESCRIPTION when description is empty string', () => {
    const event: Event = { ...mockEvent, description: '' }
    expect(generateIcs(event)).not.toContain('DESCRIPTION')
  })

  it('folds lines longer than 75 octets with CRLF + space', () => {
    const event: Event = { ...mockEvent, title: 'A'.repeat(80) }
    const result = generateIcs(event)
    const lines = result.split('\r\n')
    for (const line of lines) {
      expect(new TextEncoder().encode(line).length).toBeLessThanOrEqual(75)
    }
    // Continuation lines must start with a space
    const summaryLineIndex = lines.findIndex((l) => l.startsWith('SUMMARY:'))
    expect(lines[summaryLineIndex + 1]).toMatch(/^ /)
  })

  it('uses CRLF line endings throughout', () => {
    const result = generateIcs(mockEvent)
    // No bare LF without preceding CR
    const bareLfPattern = /(?<!\r)\n/
    expect(bareLfPattern.test(result)).toBe(false)
  })

  it('ends with CRLF', () => {
    const result = generateIcs(mockEvent)
    expect(result.endsWith('\r\n')).toBe(true)
  })
})

describe('buildGoogleCalendarUrl', () => {
  it('starts with Google Calendar render URL and has action=TEMPLATE', () => {
    const url = buildGoogleCalendarUrl(mockEvent)
    expect(url).toContain('calendar.google.com/calendar/render')
    expect(url).toContain('action=TEMPLATE')
  })

  it('includes event title', () => {
    const url = buildGoogleCalendarUrl(mockEvent)
    expect(url).toContain('text=')
    expect(url).toContain('Test+Event')
  })

  it('includes both start and end dates in the dates param', () => {
    const url = buildGoogleCalendarUrl(mockEvent)
    expect(url).toContain('dates=')
    expect(url).toContain('20250615T100000Z')
    expect(url).toContain('20250615T120000Z')
  })

  it('includes location', () => {
    const url = buildGoogleCalendarUrl(mockEvent)
    expect(url).toContain('location=')
    expect(url).toContain('Room+101')
  })

  it('includes details when description is present', () => {
    const url = buildGoogleCalendarUrl(mockEvent)
    expect(url).toContain('details=')
  })

  it('omits details when description is absent', () => {
    const event: Event = { ...mockEvent, description: undefined }
    expect(buildGoogleCalendarUrl(event)).not.toContain('details=')
  })

  it('omits details when description is empty', () => {
    const event: Event = { ...mockEvent, description: '' }
    expect(buildGoogleCalendarUrl(event)).not.toContain('details=')
  })
})
