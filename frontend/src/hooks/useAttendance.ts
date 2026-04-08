import { useCallback, useEffect, useState } from 'react'
import axios from 'axios'
import { attend, getMyAttendance, unattend } from '@/services/attendanceApi'
import type { AttendanceStatus } from '@/types/attendance'

interface AttendanceState {
  currentStatus: AttendanceStatus | null
  attendingCount: number
  interestedCount: number
}

interface UseAttendanceResult {
  currentStatus: AttendanceStatus | null
  attendingCount: number
  interestedCount: number
  loading: boolean
  error: string | null
  isFull: boolean
  toggle: (status: AttendanceStatus) => void
}

export function useAttendance(
  eventId: number,
  initialAttendingCount: number,
  initialInterestedCount: number,
  initialStatus: AttendanceStatus | null,
): UseAttendanceResult {
  const [state, setState] = useState<AttendanceState>({
    currentStatus: initialStatus,
    attendingCount: initialAttendingCount,
    interestedCount: initialInterestedCount,
  })
  // Start as true — buttons stay disabled until the real status is fetched.
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [isFull, setIsFull] = useState(false)

  // Fetch the current user's attendance status on mount so state survives
  // navigation and page refresh. Uses GET /users/me/attendances filtered
  // client-side; there is no per-event GET endpoint in the current API.
  useEffect(() => {
    let active = true
    getMyAttendance(eventId)
      .then((status) => {
        if (active) {
          setState((prev) => ({ ...prev, currentStatus: status }))
          setLoading(false)
        }
      })
      .catch(() => {
        // Not authenticated or network error — leave currentStatus at null
        // and let the user interact normally (toggle will fail with a 401 if
        // they try while unauthenticated).
        if (active) setLoading(false)
      })
    return () => {
      active = false
    }
  }, [eventId])

  const toggle = useCallback(
    (status: AttendanceStatus) => {
      if (loading) return

      const prev = state

      if (state.currentStatus === status) {
        const optimistic: AttendanceState = {
          currentStatus: null,
          attendingCount:
            status === 'ATTENDING'
              ? Math.max(0, state.attendingCount - 1)
              : state.attendingCount,
          interestedCount:
            status === 'INTERESTED'
              ? Math.max(0, state.interestedCount - 1)
              : state.interestedCount,
        }
        setState(optimistic)
        setLoading(true)
        setError(null)

        unattend(eventId)
          .then(() => setLoading(false))
          .catch(() => {
            setState(prev)
            setError('Une erreur est survenue.')
            setLoading(false)
          })
      } else {
        let nextAttending = state.attendingCount
        let nextInterested = state.interestedCount

        if (state.currentStatus === 'ATTENDING') nextAttending = Math.max(0, nextAttending - 1)
        if (state.currentStatus === 'INTERESTED') nextInterested = Math.max(0, nextInterested - 1)
        if (status === 'ATTENDING') nextAttending += 1
        if (status === 'INTERESTED') nextInterested += 1

        const optimistic: AttendanceState = {
          currentStatus: status,
          attendingCount: nextAttending,
          interestedCount: nextInterested,
        }
        setState(optimistic)
        setLoading(true)
        setError(null)
        setIsFull(false)

        attend(eventId, status)
          .then(() => setLoading(false))
          .catch((err: unknown) => {
            setState(prev)
            if (axios.isAxiosError(err) && err.response?.status === 409) {
              setIsFull(true)
            } else {
              setError('Une erreur est survenue.')
            }
            setLoading(false)
          })
      }
    },
    [eventId, loading, state],
  )

  return {
    currentStatus: state.currentStatus,
    attendingCount: state.attendingCount,
    interestedCount: state.interestedCount,
    loading,
    error,
    isFull,
    toggle,
  }
}
