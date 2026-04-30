import { useCallback, useEffect, useRef, useState } from 'react'
import axios from 'axios'
import { getEventAttendees, getPublicUser } from '@/services/attendeesApi'
import type { Attendance } from '@/types/attendance'
import type { UserPublicResponse } from '@/types/user'

export interface AttendeeWithProfile {
  attendance: Attendance
  profile: UserPublicResponse | null
}

export interface UseAttendeesOptions {
  enabled?: boolean
  pageSize?: number
}

export interface UseAttendeesResult {
  attendees: AttendeeWithProfile[]
  isLoading: boolean
  error: Error | null
  hasMore: boolean
  loadMore: () => void
  refetch: () => void
  isForbidden: boolean
}

const DEFAULT_PAGE_SIZE = 20

async function fetchProfilesFor(attendances: Attendance[]): Promise<AttendeeWithProfile[]> {
  const results = await Promise.allSettled(
    attendances.map((attendance) => getPublicUser(attendance.userId)),
  )
  return attendances.map((attendance, idx) => {
    const settled = results[idx]
    const profile = settled.status === 'fulfilled' ? settled.value : null
    return { attendance, profile }
  })
}

export function useAttendees(
  eventId: number,
  options: UseAttendeesOptions = {},
): UseAttendeesResult {
  const { enabled = true, pageSize = DEFAULT_PAGE_SIZE } = options

  const [attendees, setAttendees] = useState<AttendeeWithProfile[]>([])
  const [isLoading, setIsLoading] = useState(false)
  const [error, setError] = useState<Error | null>(null)
  const [hasMore, setHasMore] = useState(true)
  const [isForbidden, setIsForbidden] = useState(false)
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
        const enriched = await fetchProfilesFor(rows)
        if (!isCurrent()) return
        setAttendees((prev) => {
          if (pageToFetch === 0) return enriched
          const seen = new Set(prev.map((a) => a.attendance.id))
          const merged = [...prev]
          for (const item of enriched) {
            if (!seen.has(item.attendance.id)) merged.push(item)
          }
          return merged
        })
        setHasMore(rows.length === pageSize)
      } catch (err) {
        if (!isCurrent()) return
        if (axios.isAxiosError(err) && err.response?.status === 403) {
          setIsForbidden(true)
          setAttendees([])
          setHasMore(false)
        } else {
          setError(err instanceof Error ? err : new Error('Erreur inconnue'))
        }
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
    setIsForbidden(false)
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
    if (loadingRef.current || !hasMore || isForbidden) return
    const next = page + 1
    setPage(next)
    void fetchPage(next)
  }, [fetchPage, hasMore, isForbidden, page])

  return { attendees, isLoading, error, hasMore, loadMore, refetch, isForbidden }
}
