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

  const loadingRef = useRef(false)

  const fetchPage = useCallback(
    async (pageToFetch: number) => {
      if (loadingRef.current) return
      loadingRef.current = true
      setIsLoading(true)
      setError(null)
      try {
        const rows = await getEventAttendees(eventId, { page: pageToFetch, size: pageSize })
        const enriched = await fetchProfilesFor(rows)
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
        if (axios.isAxiosError(err) && err.response?.status === 403) {
          setIsForbidden(true)
          setAttendees([])
          setHasMore(false)
        } else {
          setError(err instanceof Error ? err : new Error('Erreur inconnue'))
        }
      } finally {
        setIsLoading(false)
        loadingRef.current = false
      }
    },
    [eventId, pageSize],
  )

  useEffect(() => {
    if (!enabled) return
    setAttendees([])
    setPage(0)
    setHasMore(true)
    setIsForbidden(false)
    void fetchPage(0)
  }, [enabled, eventId, fetchPage])

  const loadMore = useCallback(() => {
    if (loadingRef.current || !hasMore || isForbidden) return
    const next = page + 1
    setPage(next)
    void fetchPage(next)
  }, [fetchPage, hasMore, isForbidden, page])

  return { attendees, isLoading, error, hasMore, loadMore, isForbidden }
}
