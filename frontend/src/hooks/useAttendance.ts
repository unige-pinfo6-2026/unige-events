import { useCallback, useEffect, useState } from 'react'
import axios from 'axios'
import { attend, getMyAttendance, unattend } from '@/services/attendanceApi'
import type { AttendanceStatus } from '@/types/attendance'

interface AttendanceState {
  currentStatus: AttendanceStatus | null
  attendingCount: number
}

interface UseAttendanceResult {
  currentStatus: AttendanceStatus | null
  attendingCount: number
  loading: boolean
  error: string | null
  isFull: boolean
  toggle: (status: 'ATTENDING') => void
}

const REGISTRATION_CLOSED_MESSAGE = 'Les inscriptions sont closes pour cet événement.'
const GENERIC_ERROR_MESSAGE = 'Une erreur est survenue.'

interface ApiErrorBody {
  error?: string
  message?: string
}

function extractErrorMessage(err: unknown): string {
  if (!axios.isAxiosError(err)) return GENERIC_ERROR_MESSAGE
  const body = err.response?.data as ApiErrorBody | undefined
  if (body?.error === 'registration_closed') return REGISTRATION_CLOSED_MESSAGE
  if (typeof body?.message === 'string' && body.message.trim() !== '') return body.message
  return GENERIC_ERROR_MESSAGE
}

export function useAttendance(
  eventId: number,
  initialAttendingCount: number,
  initialStatus: AttendanceStatus | null,
  initialAvailableSpots?: number | null,
): UseAttendanceResult {
  const [state, setState] = useState<AttendanceState>({
    currentStatus: initialStatus,
    attendingCount: initialAttendingCount,
  })
  // Start as true — buttons stay disabled until the real status is fetched.
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [isFull, setIsFull] = useState(initialAvailableSpots === 0)

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
    (status: 'ATTENDING') => {
      if (loading) return

      // Snapshot every piece of state we touch optimistically so we can roll
      // back atomically on error.
      const prevState = state
      const prevIsFull = isFull

      if (state.currentStatus === null) {
        const prevCount = state.attendingCount

        const optimistic: AttendanceState = {
          currentStatus: 'ATTENDING',
          attendingCount: prevCount + 1,
        }
        setState(optimistic)
        setLoading(true)
        setError(null)
        // isFull is intentionally NOT cleared optimistically — the server is
        // the source of truth for capacity. We update it only after the
        // response, based on the assigned status.

        attend(eventId, status)
          .then((attendance) => {
            // Server may assign WAITLISTED if event filled up between load and submit.
            setState({
              currentStatus: attendance.status,
              attendingCount:
                attendance.status === 'WAITLISTED' ? prevCount : prevCount + 1,
            })
            setIsFull(attendance.status === 'WAITLISTED')
            setLoading(false)
          })
          .catch((err: unknown) => {
            setState(prevState)
            setIsFull(prevIsFull)
            setError(extractErrorMessage(err))
            setLoading(false)
          })
      } else {
        // Already registered (ATTENDING or WAITLISTED) → unregister.
        const optimistic: AttendanceState = {
          currentStatus: null,
          // WAITLISTED users are not counted in attendingCount
          attendingCount:
            state.currentStatus === 'ATTENDING'
              ? Math.max(0, state.attendingCount - 1)
              : state.attendingCount,
        }
        setState(optimistic)
        setLoading(true)
        setError(null)

        unattend(eventId)
          .then(() => {
            // Leave isFull unchanged: a successful unattend does not guarantee
            // a free slot — the server may auto-promote the next waitlisted
            // user, or others may already be queued. A future improvement
            // could refetch the event to recompute availableSpots.
            setLoading(false)
          })
          .catch((err: unknown) => {
            setState(prevState)
            setIsFull(prevIsFull)
            setError(extractErrorMessage(err))
            setLoading(false)
          })
      }
    },
    [eventId, isFull, loading, state],
  )

  return {
    currentStatus: state.currentStatus,
    attendingCount: state.attendingCount,
    loading,
    error,
    isFull,
    toggle,
  }
}
