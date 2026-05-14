import { useEffect, useState } from 'react'
import { getAll } from '@/services/eventApi'
import type { Event } from '@/types/event'

export interface UseOrganizerEventsResult {
  events: Event[]
  loading: boolean
  error: string | null
}

/**
 * Lists published events created by a given user (SCRUM-141 — public
 * profile). Calls `GET /events?organizerId={id}&status=PUBLISHED` — the
 * backend forces `status=PUBLISHED` when `organizerId` is set, so only
 * publicly-visible events are returned regardless of caller role.
 *
 * `organizerId === undefined` keeps the hook in loading state without
 * firing a request.
 */
export function useOrganizerEvents(organizerId: string | undefined): UseOrganizerEventsResult {
  const [events, setEvents] = useState<Event[]>([])
  const [loading, setLoading] = useState<boolean>(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!organizerId) {
      setEvents([])
      setLoading(true)
      setError(null)
      return
    }

    let cancelled = false
    setLoading(true)
    setError(null)

    getAll({ organizerId, status: 'PUBLISHED' })
      .then(data => {
        if (cancelled) return
        setEvents(data)
        setLoading(false)
      })
      .catch(() => {
        if (cancelled) return
        setEvents([])
        setError('Impossible de charger les événements organisés.')
        setLoading(false)
      })

    return () => {
      cancelled = true
    }
  }, [organizerId])

  return { events, loading, error }
}
