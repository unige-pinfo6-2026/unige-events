import { parseApiUtcDateTime } from '@/utils/dateTime'

const MINUTE = 60
const HOUR = 60 * MINUTE
const DAY = 24 * HOUR
const WEEK = 7 * DAY
const MONTH = 30 * DAY
const YEAR = 365 * DAY

export function formatRelativeTime(dateTime: string, now: Date = new Date()): string {
  const parsed = parseApiUtcDateTime(dateTime)
  if (Number.isNaN(parsed.getTime())) return ''

  const diffSeconds = Math.max(0, Math.floor((now.getTime() - parsed.getTime()) / 1000))

  if (diffSeconds < MINUTE) return "à l'instant"
  if (diffSeconds < HOUR) {
    const m = Math.floor(diffSeconds / MINUTE)
    return `il y a ${m} min`
  }
  if (diffSeconds < DAY) {
    const h = Math.floor(diffSeconds / HOUR)
    return `il y a ${h} h`
  }
  if (diffSeconds < 2 * DAY) return 'hier'
  if (diffSeconds < WEEK) {
    const d = Math.floor(diffSeconds / DAY)
    return `il y a ${d} jours`
  }
  if (diffSeconds < MONTH) {
    const w = Math.floor(diffSeconds / WEEK)
    return w === 1 ? 'il y a 1 semaine' : `il y a ${w} semaines`
  }
  if (diffSeconds < YEAR) {
    const months = Math.floor(diffSeconds / MONTH)
    return months === 1 ? 'il y a 1 mois' : `il y a ${months} mois`
  }
  const years = Math.floor(diffSeconds / YEAR)
  return years === 1 ? 'il y a 1 an' : `il y a ${years} ans`
}
