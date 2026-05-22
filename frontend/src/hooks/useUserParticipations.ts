import { useEffect, useState } from 'react'
import { getUserParticipations } from '@/services/attendanceApi'
import type { Event } from '@/types/event'

export interface UseUserParticipationsResult {
  events: Event[]
  loading: boolean
  error: string | null
}

/**
 * Lists the PUBLISHED events a given user is registered to (ATTENDING) — the
 * public "Participations publiques" profile section (SCRUM-141 follow-up).
 * Calls `GET /users/{id}/participations`; the backend applies the privacy gate
 * (self / public target → list, private non-owner target → empty list) and
 * only exposes ATTENDING inscriptions to PUBLISHED events.
 *
 * `userId === undefined` keeps the hook in its loading state without firing a
 * request (mirrors {@link useOrganizerEvents}).
 */
export function useUserParticipations(userId: string | undefined): UseUserParticipationsResult {
  const [events, setEvents] = useState<Event[]>([])
  const [loading, setLoading] = useState<boolean>(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!userId) {
      setEvents([])
      setLoading(true)
      setError(null)
      return
    }

    let cancelled = false
    setLoading(true)
    setError(null)

    getUserParticipations(userId)
      .then(data => {
        if (cancelled) return
        setEvents(data)
        setLoading(false)
      })
      .catch(() => {
        if (cancelled) return
        setEvents([])
        setError('Impossible de charger les participations.')
        setLoading(false)
      })

    return () => {
      cancelled = true
    }
  }, [userId])

  return { events, loading, error }
}
