import { useCallback, useEffect, useRef, useState } from 'react'
import axios from 'axios'
import { attend, getMyAttendance, unattend } from '@/services/attendanceApi'
import type { AttendanceStatus } from '@/types/attendance'

interface UiState {
  currentStatus: AttendanceStatus | null
  attendingCount: number
  isFull: boolean
}

export interface UseAttendanceOptions {
  onAfterSuccess?: () => void
}

interface UseAttendanceResult {
  currentStatus: AttendanceStatus | null
  attendingCount: number
  loading: boolean
  error: string | null
  isFull: boolean
  toggle: (status: 'ATTENDING') => void
}

const DEBOUNCE_MS = 500
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
  options: UseAttendanceOptions = {},
): UseAttendanceResult {
  const { onAfterSuccess } = options

  // Single state object for all visual fields → React batches updates so the
  // button label, count and badge variant always render in a consistent state
  // (no orange "waitlist" flash when isFull and currentStatus disagree for
  // one frame).
  const [ui, setUi] = useState<UiState>({
    currentStatus: initialStatus,
    attendingCount: initialAttendingCount,
    isFull: initialAvailableSpots === 0,
  })
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  // Loading is the OR of (API in flight) and (debounce window still open) so
  // the button stays disabled for at least DEBOUNCE_MS after a click even if
  // the API resolves faster — prevents accidental double-fires.
  const apiInFlightRef = useRef(true) // initial fetch starts in-flight
  const debouncePendingRef = useRef(false)
  const debounceTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const mountedRef = useRef(true)

  // Latest onAfterSuccess held in a ref so callers can pass inline arrows
  // without invalidating toggle's identity on every render.
  const onAfterSuccessRef = useRef(onAfterSuccess)
  useEffect(() => {
    onAfterSuccessRef.current = onAfterSuccess
  }, [onAfterSuccess])

  const recomputeLoading = useCallback(() => {
    if (!mountedRef.current) return
    setLoading(apiInFlightRef.current || debouncePendingRef.current)
  }, [])

  // Cleanup debounce on eventId change / unmount.
  useEffect(() => {
    mountedRef.current = true
    return () => {
      mountedRef.current = false
      if (debounceTimerRef.current !== null) {
        clearTimeout(debounceTimerRef.current)
        debounceTimerRef.current = null
      }
      debouncePendingRef.current = false
    }
  }, [eventId])

  // Fetch the current user's attendance status on mount so state survives
  // navigation and page refresh. Uses GET /users/me/attendances filtered
  // client-side; there is no per-event GET endpoint in the current API.
  useEffect(() => {
    let active = true
    apiInFlightRef.current = true
    setLoading(true)
    getMyAttendance(eventId)
      .then((status) => {
        if (!active) return
        setUi((prev) => ({ ...prev, currentStatus: status }))
      })
      .catch(() => {
        // Not authenticated or network error — leave currentStatus untouched
        // and let the user interact normally (toggle will fail with a 401 if
        // they try while unauthenticated).
      })
      .finally(() => {
        if (!active) return
        apiInFlightRef.current = false
        recomputeLoading()
      })
    return () => {
      active = false
    }
  }, [eventId, recomputeLoading])

  const startDebounce = useCallback(() => {
    debouncePendingRef.current = true
    if (debounceTimerRef.current !== null) {
      clearTimeout(debounceTimerRef.current)
    }
    debounceTimerRef.current = setTimeout(() => {
      debouncePendingRef.current = false
      debounceTimerRef.current = null
      recomputeLoading()
    }, DEBOUNCE_MS)
  }, [recomputeLoading])

  const toggle = useCallback(
    (status: 'ATTENDING') => {
      if (loading) return

      const prev = ui
      const goingToAttend = ui.currentStatus === null

      // Compute optimistic state in a single object — the setUi call below
      // batches every visual field together so there is no intermediate
      // render where (currentStatus, isFull) disagree.
      let optimistic: UiState
      if (goingToAttend) {
        optimistic = {
          currentStatus: 'ATTENDING',
          attendingCount: ui.attendingCount + 1,
          // Server is source of truth for capacity; do not predict isFull on attend.
          isFull: ui.isFull,
        }
      } else {
        optimistic = {
          currentStatus: null,
          attendingCount:
            ui.currentStatus === 'ATTENDING'
              ? Math.max(0, ui.attendingCount - 1)
              : ui.attendingCount,
          // Optimistically clear isFull when an ATTENDING user leaves: we just
          // freed a slot. The post-success refetch reconciles if the server
          // auto-promotes a waitlisted user. Without this, the button briefly
          // flashes the orange "Rejoindre la liste d'attente" style during the
          // transition.
          isFull: ui.currentStatus === 'ATTENDING' ? false : ui.isFull,
        }
      }

      setUi(optimistic)
      setError(null)
      apiInFlightRef.current = true
      setLoading(true)
      startDebounce()

      const settle = () => {
        if (!mountedRef.current) return
        apiInFlightRef.current = false
        recomputeLoading()
      }
      const onError = (err: unknown) => {
        if (!mountedRef.current) return
        setUi(prev)
        setError(extractErrorMessage(err))
      }

      if (goingToAttend) {
        attend(eventId, status)
          .then((attendance) => {
            if (!mountedRef.current) return
            setUi({
              currentStatus: attendance.status,
              attendingCount:
                attendance.status === 'WAITLISTED'
                  ? prev.attendingCount
                  : prev.attendingCount + 1,
              isFull: attendance.status === 'WAITLISTED',
            })
            onAfterSuccessRef.current?.()
          })
          .catch(onError)
          .finally(settle)
      } else {
        unattend(eventId)
          .then(() => {
            if (!mountedRef.current) return
            // Optimistic state stands; refetch reconciles availableSpots /
            // waitlistedCount in case the server auto-promoted someone.
            onAfterSuccessRef.current?.()
          })
          .catch(onError)
          .finally(settle)
      }
    },
    [eventId, loading, ui, startDebounce, recomputeLoading],
  )

  return {
    currentStatus: ui.currentStatus,
    attendingCount: ui.attendingCount,
    loading,
    error,
    isFull: ui.isFull,
    toggle,
  }
}
