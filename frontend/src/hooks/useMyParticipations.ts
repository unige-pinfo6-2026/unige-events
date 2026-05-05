import { useCallback, useEffect, useState } from 'react'
import { getMyParticipations } from '@/services/attendanceApi'
import type { Event } from '@/types/event'

interface UseMyParticipationsResult {
  attending: Event[]
  waitlisted: Event[]
  loading: boolean
  error: string | null
  refresh: () => void
}

export function useMyParticipations(): UseMyParticipationsResult {
  const [attending, setAttending] = useState<Event[]>([])
  const [waitlisted, setWaitlisted] = useState<Event[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const fetch = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const [attendingData, waitlistedData] = await Promise.all([
        getMyParticipations('ATTENDING'),
        getMyParticipations('WAITLISTED'),
      ])
      setAttending(attendingData)
      setWaitlisted(waitlistedData)
    } catch {
      setError('Impossible de charger vos participations.')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    fetch()
  }, [fetch])

  return { attending, waitlisted, loading, error, refresh: fetch }
}
