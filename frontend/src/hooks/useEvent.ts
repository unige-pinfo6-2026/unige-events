import { useCallback, useEffect, useRef, useState } from 'react'
import { getById } from '@/services/eventApi'
import type { Event } from '@/types/event'

interface UseEventResult {
  event: Event | null
  isInitialLoad: boolean
  isRefetching: boolean
  /** Alias for `isInitialLoad || isRefetching`. Prefer the named flags for new code. */
  loading: boolean
  error: string | null
  refetch: () => Promise<void>
}

export function useEvent(id: number | null, checkCoOrgOf?: string | null): UseEventResult {
  const [event, setEvent] = useState<Event | null>(null)
  // True only on the very first fetch (when no event has been loaded yet).
  // Flips to false permanently as soon as the first response arrives so
  // subsequent refetches don't trigger the full-page skeleton.
  const [isInitialLoad, setIsInitialLoad] = useState(true)
  const [isRefetching, setIsRefetching] = useState(false)
  const [error, setError] = useState<string | null>(null)

  // Monotonically increases on every fetch start, eventId change, and unmount;
  // in-flight responses whose captured id != current id are discarded.
  const requestIdRef = useRef(0)
  // Tracks whether any successful response has been received yet. Refetches
  // after the first response use `isRefetching` instead of `isInitialLoad`.
  const hasLoadedOnceRef = useRef(false)

  const fetchEvent = useCallback((targetId: number, coOrgCheck: string | null | undefined): Promise<void> => {
    requestIdRef.current += 1
    const myRequestId = requestIdRef.current
    const isCurrent = () => requestIdRef.current === myRequestId
    if (hasLoadedOnceRef.current) {
      setIsRefetching(true)
    } else {
      setIsInitialLoad(true)
    }
    setError(null)
    return getById(targetId, coOrgCheck ?? null)
      .then((data) => {
        if (!isCurrent()) return
        setEvent(data)
        hasLoadedOnceRef.current = true
      })
      .catch(() => {
        if (!isCurrent()) return
        setError('Impossible de charger cet événement.')
      })
      .finally(() => {
        if (!isCurrent()) return
        setIsInitialLoad(false)
        setIsRefetching(false)
      })
  }, [])

  const refetch = useCallback((): Promise<void> => {
    if (id === null) return Promise.resolve()
    return fetchEvent(id, checkCoOrgOf)
  }, [id, checkCoOrgOf, fetchEvent])

  useEffect(() => {
    if (id === null) return
    void fetchEvent(id, checkCoOrgOf)
    return () => {
      requestIdRef.current += 1
    }
  }, [id, checkCoOrgOf, fetchEvent])

  return {
    event,
    isInitialLoad,
    isRefetching,
    loading: isInitialLoad || isRefetching,
    error,
    refetch,
  }
}
