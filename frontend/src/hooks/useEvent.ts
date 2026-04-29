import { useCallback, useEffect, useRef, useState } from 'react'
import { getById } from '@/services/eventApi'
import type { Event } from '@/types/event'

interface UseEventResult {
  event: Event | null
  loading: boolean
  error: string | null
  refetch: () => void
}

export function useEvent(id: number | null): UseEventResult {
  const [event, setEvent] = useState<Event | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  // Monotonically increases on every fetch start, eventId change, and unmount;
  // in-flight responses whose captured id != current id are discarded.
  const requestIdRef = useRef(0)

  const fetchEvent = useCallback((targetId: number) => {
    requestIdRef.current += 1
    const myRequestId = requestIdRef.current
    const isCurrent = () => requestIdRef.current === myRequestId
    setLoading(true)
    setError(null)
    getById(targetId)
      .then((data) => {
        if (!isCurrent()) return
        setEvent(data)
      })
      .catch(() => {
        if (!isCurrent()) return
        setError('Impossible de charger cet événement.')
      })
      .finally(() => {
        if (!isCurrent()) return
        setLoading(false)
      })
  }, [])

  const refetch = useCallback(() => {
    if (id === null) return
    fetchEvent(id)
  }, [id, fetchEvent])

  useEffect(() => {
    if (id === null) return
    fetchEvent(id)
    return () => {
      requestIdRef.current += 1
    }
  }, [id, fetchEvent])

  return { event, loading, error, refetch }
}
