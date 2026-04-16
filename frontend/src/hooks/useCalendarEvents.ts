import { useCallback, useEffect, useState } from 'react'
import { getAll } from '../services/eventApi'
import type { Event } from '@/types/event'

export interface CalendarEvent {
  title: string
  start: Date
  end: Date
  resource: Event
}

interface UseCalendarEventsResult {
  events: CalendarEvent[]
  loading: boolean
  error: string | null
}

export function useCalendarEvents(date: Date): UseCalendarEventsResult {
  const [events, setEvents] = useState<CalendarEvent[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const fetchEvents = useCallback(async (d: Date) => {
    setLoading(true)
    setError(null)
    try {
      const year = d.getFullYear()
      const month = d.getMonth()
      const firstDay = new Date(year, month, 1)
      const lastDay = new Date(year, month + 1, 0, 23, 59, 59)
      const endDateFrom = firstDay.toISOString().slice(0, 19)
      const data = await getAll({ page: 0, size: 100, status: 'PUBLISHED', endDateFrom })
      const filtered = data.filter((e) => new Date(e.startDate) <= lastDay)
      setEvents(
        filtered.map((e) => ({
          title: e.title,
          start: new Date(e.startDate),
          end: new Date(e.endDate),
          resource: e,
        })),
      )
    } catch {
      setError('Impossible de charger les événements.')
    } finally {
      setLoading(false)
    }
  }, [])

  const year = date.getFullYear()
  const month = date.getMonth()
  useEffect(() => {
    fetchEvents(new Date(year, month, 1))
  }, [fetchEvents, year, month])

  return { events, loading, error }
}
