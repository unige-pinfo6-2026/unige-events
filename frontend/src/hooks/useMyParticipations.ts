import { useCallback, useEffect, useState } from 'react'
import { getMyParticipations } from '@/services/attendanceApi'
import type { Event } from '@/types/event'

interface UseMyParticipationsResult {
  attending: Event[]
  waitlisted: Event[]
  pastAttending: Event[]
  loading: boolean
  error: string | null
  refresh: () => void
}

export function useMyParticipations(): UseMyParticipationsResult {
  const [attending, setAttending] = useState<Event[]>([])
  const [waitlisted, setWaitlisted] = useState<Event[]>([])
  const [pastAttending, setPastAttending] = useState<Event[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const fetch = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const [attendingData, waitlistedData, pastAttendingData] = await Promise.all([
        getMyParticipations('ATTENDING', 'upcoming'),
        getMyParticipations('WAITLISTED', 'upcoming'),
        getMyParticipations('ATTENDING', 'past'),
      ])
      setAttending(attendingData)
      setWaitlisted(waitlistedData)
      setPastAttending(pastAttendingData)
    } catch {
      setError('Impossible de charger vos participations.')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    fetch()
  }, [fetch])

  return { attending, waitlisted, pastAttending, loading, error, refresh: fetch }
}
