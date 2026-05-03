import { useCallback, useEffect, useRef, useState } from 'react'
import { getEventStats } from '@/services/statsApi'
import type { EventStats } from '@/services/statsApi'

const REFRESH_INTERVAL_MS = 60_000

interface UseEventStatsResult {
  stats: EventStats | null
  /** True only on the very first fetch, when no stats have been loaded yet. */
  loading: boolean
  /** True during a manual `refetch()` or auto-refresh tick once stats are loaded. */
  isRefetching: boolean
  error: string | null
  /** Triggers an immediate refetch. Resolves once the response (or error) lands. */
  refetch: () => Promise<void>
}

export function useEventStats(eventId: number | null): UseEventStatsResult {
  const [stats, setStats] = useState<EventStats | null>(null)
  const [loading, setLoading] = useState(true)
  const [isRefetching, setIsRefetching] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null)
  // Bumped on eventId change / unmount so an in-flight response from a previous
  // generation does not overwrite fresh state.
  const requestIdRef = useRef(0)
  const hasLoadedOnceRef = useRef(false)

  const fetchStats = useCallback(async (id: number): Promise<void> => {
    requestIdRef.current += 1
    const myRequestId = requestIdRef.current
    const isCurrent = () => requestIdRef.current === myRequestId
    if (hasLoadedOnceRef.current) {
      setIsRefetching(true)
    } else {
      setLoading(true)
    }
    try {
      const data = await getEventStats(id)
      if (!isCurrent()) return
      setStats(data)
      setError(null)
      hasLoadedOnceRef.current = true
    } catch {
      if (!isCurrent()) return
      setError('Impossible de charger les statistiques.')
    } finally {
      if (isCurrent()) {
        setLoading(false)
        setIsRefetching(false)
      }
    }
  }, [])

  useEffect(() => {
    if (eventId === null) {
      setLoading(false)
      setStats(null)
      setError(null)
      hasLoadedOnceRef.current = false
      return
    }

    void fetchStats(eventId)
    intervalRef.current = setInterval(() => { void fetchStats(eventId) }, REFRESH_INTERVAL_MS)

    return () => {
      requestIdRef.current += 1
      if (intervalRef.current) {
        clearInterval(intervalRef.current)
        intervalRef.current = null
      }
    }
  }, [eventId, fetchStats])

  const refetch = useCallback(async (): Promise<void> => {
    if (eventId === null) return
    await fetchStats(eventId)
  }, [eventId, fetchStats])

  return { stats, loading, isRefetching, error, refetch }
}
