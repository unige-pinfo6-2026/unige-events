import { useEffect, useRef, useState } from 'react'
import { getEventStats } from '@/services/statsApi'
import type { EventStats } from '@/services/statsApi'

const REFRESH_INTERVAL_MS = 60_000

interface UseEventStatsResult {
  stats: EventStats | null
  loading: boolean
  error: string | null
}

export function useEventStats(eventId: number | null): UseEventStatsResult {
  const [stats, setStats] = useState<EventStats | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null)

  useEffect(() => {
    if (eventId === null) {
      setLoading(false)
      setStats(null)
      setError(null)
      return
    }

    let active = true

    async function fetchStats() {
      try {
        const data = await getEventStats(eventId!)
        if (active) {
          setStats(data)
          setError(null)
        }
      } catch {
        if (active) setError('Impossible de charger les statistiques.')
      } finally {
        if (active) setLoading(false)
      }
    }

    setLoading(true)
    fetchStats()
    intervalRef.current = setInterval(fetchStats, REFRESH_INTERVAL_MS)

    return () => {
      active = false
      if (intervalRef.current) {
        clearInterval(intervalRef.current)
        intervalRef.current = null
      }
    }
  }, [eventId])

  return { stats, loading, error }
}
