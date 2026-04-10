import type { Event } from '@/types/event'
import { parseApiUtcDateTime } from '@/utils/dateTime'

function toIcsDate(dateStr: string): string {
  const d = parseApiUtcDateTime(dateStr)
  const pad = (n: number) => String(n).padStart(2, '0')
  return (
    `${d.getUTCFullYear()}${pad(d.getUTCMonth() + 1)}${pad(d.getUTCDate())}` +
    `T${pad(d.getUTCHours())}${pad(d.getUTCMinutes())}${pad(d.getUTCSeconds())}Z`
  )
}

function escapeIcsText(text: string): string {
  return text
    .replace(/\\/g, '\\\\')
    .replace(/;/g, '\\;')
    .replace(/,/g, '\\,')
    .replace(/\r\n|\r|\n/g, '\\n')
}

function foldLine(line: string): string {
  const encoder = new TextEncoder()
  const segments: string[] = []
  let current = ''
  let byteCount = 0

  for (const char of line) {
    const charByteLength = encoder.encode(char).length
    if (byteCount + charByteLength > 75) {
      segments.push(current)
      current = ' ' + char
      byteCount = 1 + charByteLength
    } else {
      current += char
      byteCount += charByteLength
    }
  }

  if (current.length > 0) {
    segments.push(current)
  }

  return segments.join('\r\n')
}

export function generateIcs(event: Event): string {
  const lines: string[] = [
    'BEGIN:VCALENDAR',
    'VERSION:2.0',
    'PRODID:-//UNIGE Events//EN',
    'BEGIN:VEVENT',
    `UID:${event.id}@unige-events`,
    `DTSTART:${toIcsDate(event.startDate)}`,
    `DTEND:${toIcsDate(event.endDate)}`,
    `SUMMARY:${escapeIcsText(event.title)}`,
    `LOCATION:${escapeIcsText(event.location)}`,
  ]

  if (event.description) {
    lines.push(`DESCRIPTION:${escapeIcsText(event.description)}`)
  }

  lines.push('END:VEVENT', 'END:VCALENDAR')

  return lines.map(foldLine).join('\r\n') + '\r\n'
}

export function buildGoogleCalendarUrl(event: Event): string {
  const start = toIcsDate(event.startDate)
  const end = toIcsDate(event.endDate)
  const params = new URLSearchParams({
    action: 'TEMPLATE',
    text: event.title,
    dates: `${start}/${end}`,
    location: event.location,
  })
  if (event.description) {
    params.set('details', event.description)
  }
  return `https://calendar.google.com/calendar/render?${params.toString()}`
}
