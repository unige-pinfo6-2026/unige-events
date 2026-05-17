import { useEffect, useState } from 'react'
import { getAll } from '@/services/eventApi'
import type { Event } from '@/types/event'

export interface UseOrganizerEventsResult {
  events: Event[]
  loading: boolean
  error: string | null
  /**
   * `true` when the API returned exactly {@link ORGANIZER_EVENTS_FETCH_SIZE}
   * rows — there may be more events the hook didn't fetch. Pagination is not
   * implemented (this surface is a profile preview, not a full archive) ;
   * the consumer can use this flag to render a "Voir plus" CTA pointing to
   * a dedicated archive page later.
   */
  hasMore: boolean
}

/**
 * Hard cap on the number of events fetched for the profile preview.
 * The backend `/events` endpoint paginates with `size` (max 100) — we ask
 * for the max so the public-profile section displays a meaningful preview
 * without scrolling through the whole archive. Pagination beyond this is
 * out of scope for SCRUM-141 — surface via `hasMore` for a future archive
 * page CTA.
 */
export const ORGANIZER_EVENTS_FETCH_SIZE = 100

/**
 * Lists published events created by a given user (SCRUM-141 — public
 * profile). Calls `GET /events?organizerId={id}&status=PUBLISHED&size=…` —
 * the backend forces `status=PUBLISHED` when `organizerId` is set, so only
 * publicly-visible events are returned regardless of caller role.
 *
 * `organizerId === undefined` keeps the hook in loading state without
 * firing a request.
 */
export function useOrganizerEvents(organizerId: string | undefined): UseOrganizerEventsResult {
  const [events, setEvents] = useState<Event[]>([])
  const [loading, setLoading] = useState<boolean>(true)
  const [error, setError] = useState<string | null>(null)
  const [hasMore, setHasMore] = useState<boolean>(false)

  useEffect(() => {
    if (!organizerId) {
      setEvents([])
      setHasMore(false)
      setLoading(true)
      setError(null)
      return
    }

    let cancelled = false
    setLoading(true)
    setError(null)

    getAll({ organizerId, status: 'PUBLISHED', size: ORGANIZER_EVENTS_FETCH_SIZE })
      .then(data => {
        if (cancelled) return
        setEvents(data)
        setHasMore(data.length === ORGANIZER_EVENTS_FETCH_SIZE)
        setLoading(false)
      })
      .catch(() => {
        if (cancelled) return
        setEvents([])
        setHasMore(false)
        setError('Impossible de charger les événements organisés.')
        setLoading(false)
      })

    return () => {
      cancelled = true
    }
  }, [organizerId])

  return { events, loading, error, hasMore }
}
