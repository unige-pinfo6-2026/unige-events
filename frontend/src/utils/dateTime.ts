const TIMEZONE_SUFFIX_PATTERN = /(Z|[+-]\d{2}:\d{2})$/i

function pad(value: number): string {
  return String(value).padStart(2, '0')
}

export function parseApiUtcDateTime(dateTime: string): Date {
  const normalizedDateTime = TIMEZONE_SUFFIX_PATTERN.test(dateTime) ? dateTime : `${dateTime}Z`
  return new Date(normalizedDateTime)
}

export function toLocalDateTimeInputValue(dateTime: string): string {
  const date = parseApiUtcDateTime(dateTime)
  if (Number.isNaN(date.getTime())) {
    return ''
  }

  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`
}

export function formatEventDateTime(dateTime: string): string {
  return parseApiUtcDateTime(dateTime).toLocaleDateString('fr-CH', {
    weekday: 'long',
    day: 'numeric',
    month: 'long',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

export function formatEventDateTimeCompact(dateTime: string): string {
  return parseApiUtcDateTime(dateTime).toLocaleDateString('fr-CH', {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}
