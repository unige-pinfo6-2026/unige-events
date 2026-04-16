import { useCallback, useEffect, useState } from 'react'
import { getMyParticipations } from '@/services/attendanceApi'
import type { Event } from '@/types/event'

interface UseMyParticipationsResult {
  events: Event[]
  loading: boolean
  error: string | null
  refresh: () => void
}

export function useMyParticipations(): UseMyParticipationsResult {
  const [events, setEvents] = useState<Event[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const fetch = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const data = await getMyParticipations()
      setEvents(data)
    } catch {
      setError('Impossible de charger vos participations.')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    fetch()
  }, [fetch])

  return { events, loading, error, refresh: fetch }
}
