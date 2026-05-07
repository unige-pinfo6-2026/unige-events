import { useEffect, useState } from 'react'
import { getFeatured } from '@/services/eventApi'
import type { Event } from '@/types/event'

const FEATURED_LIMIT = 6

interface UseFeaturedEventsResult {
  events: Event[]
  loading: boolean
  error: string | null
}

export function useFeaturedEvents(): UseFeaturedEventsResult {
  const [events, setEvents] = useState<Event[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    setError(null)
    getFeatured(FEATURED_LIMIT)
      .then((data) => {
        if (!cancelled) setEvents(data)
      })
      .catch(() => {
        if (!cancelled) setError('Impossible de charger les événements à la une.')
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [])

  return { events, loading, error }
}
