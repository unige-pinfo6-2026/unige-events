import { useCallback, useEffect, useRef, useState } from 'react'
import { getEventAttendees } from '@/services/attendeesApi'
import type { Attendance } from '@/types/attendance'

export interface UseAttendeesOptions {
  enabled?: boolean
  pageSize?: number
}

export interface UseAttendeesResult {
  attendees: Attendance[]
  isLoading: boolean
  error: Error | null
  hasMore: boolean
  loadMore: () => void
  refetch: () => void
}

const DEFAULT_PAGE_SIZE = 20

/**
 * Paginated participants for an event. The backend applies the SCRUM-S7
 * privacy filter at the DTO layer, so the hook does NOT do per-row
 * `/users/{id}` lookups any more — anonymized rows arrive with
 * `displayName=null` / `avatarUrl=null` / `userId=null` straight from
 * `/events/{id}/attendees`. The rendering layer (`AttendeeCard`) decides
 * "real identity" vs "Utilisateur anonyme" from `attendance.displayName`.
 *
 * `enabled=false` short-circuits the fetch entirely so unauthenticated
 * viewers don't trigger any API call on the event detail page.
 */
export function useAttendees(
  eventId: number,
  options: UseAttendeesOptions = {},
): UseAttendeesResult {
  const { enabled = true, pageSize = DEFAULT_PAGE_SIZE } = options

  const [attendees, setAttendees] = useState<Attendance[]>([])
  const [isLoading, setIsLoading] = useState(false)
  const [error, setError] = useState<Error | null>(null)
  const [hasMore, setHasMore] = useState(true)
  const [page, setPage] = useState(0)

  // Concurrency control:
  //  - loadingRef: prevents overlapping loadMore() clicks within the same fetch generation.
  //  - requestIdRef: monotonically increases on every reset/unmount; in-flight responses
  //    whose captured id != current id are discarded (handles eventId change + unmount).
  const loadingRef = useRef(false)
  const requestIdRef = useRef(0)

  const fetchPage = useCallback(
    async (pageToFetch: number) => {
      if (loadingRef.current) return
      loadingRef.current = true
      const myRequestId = requestIdRef.current
      const isCurrent = () => requestIdRef.current === myRequestId
      setIsLoading(true)
      setError(null)
      try {
        const rows = await getEventAttendees(eventId, { page: pageToFetch, size: pageSize })
        if (!isCurrent()) return
        setAttendees((prev) => {
          if (pageToFetch === 0) return rows
          const seen = new Set(prev.map((a) => a.id))
          const merged = [...prev]
          for (const row of rows) {
            if (!seen.has(row.id)) merged.push(row)
          }
          return merged
        })
        setHasMore(rows.length === pageSize)
      } catch (err) {
        if (!isCurrent()) return
        setError(err instanceof Error ? err : new Error('Erreur inconnue'))
      } finally {
        if (isCurrent()) setIsLoading(false)
        loadingRef.current = false
      }
    },
    [eventId, pageSize],
  )

  // Resets internal state and fetches page 0. Bumps requestIdRef so any in-flight
  // response from a previous generation is discarded.
  const reset = useCallback(() => {
    requestIdRef.current += 1
    loadingRef.current = false
    setAttendees([])
    setPage(0)
    setHasMore(true)
    setError(null)
  }, [])

  const refetch = useCallback(() => {
    reset()
    void fetchPage(0)
  }, [reset, fetchPage])

  useEffect(() => {
    if (!enabled) {
      // On disable: invalidate any in-flight request without triggering a new fetch.
      requestIdRef.current += 1
      loadingRef.current = false
      return
    }
    reset()
    void fetchPage(0)
    return () => {
      // Discard in-flight responses on unmount or eventId/enabled change.
      requestIdRef.current += 1
      loadingRef.current = false
    }
  }, [enabled, eventId, fetchPage, reset])

  const loadMore = useCallback(() => {
    if (loadingRef.current || !hasMore) return
    const next = page + 1
    setPage(next)
    void fetchPage(next)
  }, [fetchPage, hasMore, page])

  return { attendees, isLoading, error, hasMore, loadMore, refetch }
}
