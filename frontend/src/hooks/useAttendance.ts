import { useCallback, useEffect, useRef, useState } from 'react'
import axios from 'axios'
import { attend, getMyAttendance, unattend } from '@/services/attendanceApi'
import type { AttendanceStatus } from '@/types/attendance'

interface UiState {
  currentStatus: AttendanceStatus | null
  attendingCount: number
  isFull: boolean
}

interface LabelSnapshot {
  status: AttendanceStatus | null
  isFull: boolean
}

export interface UseAttendanceOptions {
  /**
   * Invoked after a successful attend/unattend API response. The hook awaits
   * the returned promise before clearing `mutating`, so callers can chain
   * refetches that should complete before the action button "settles".
   */
  onAfterSuccess?: () => void | Promise<void>
}

interface UseAttendanceResult {
  currentStatus: AttendanceStatus | null
  attendingCount: number
  /** True while either the initial mount fetch or a user-triggered mutation is in flight. */
  loading: boolean
  /** True only while a user-triggered attend/unattend mutation is in flight (incl. parent refetch). */
  mutating: boolean
  error: string | null
  isFull: boolean
  /** Frozen at click time during a mutation; equals `currentStatus` otherwise. */
  displayStatus: AttendanceStatus | null
  /** Frozen at click time during a mutation; equals `isFull` otherwise. */
  displayIsFull: boolean
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
  options: UseAttendanceOptions = {},
): UseAttendanceResult {
  const { onAfterSuccess } = options

  // Single state object — React batches every visual field together so there
  // is no intermediate render where (currentStatus, isFull) disagree.
  const [ui, setUi] = useState<UiState>({
    currentStatus: initialStatus,
    attendingCount: initialAttendingCount,
    isFull: initialAvailableSpots === 0,
  })
  // Initial mount fetch in flight.
  const [initializing, setInitializing] = useState(true)
  // True from click → API resolved → onAfterSuccess resolved (or error rolled back).
  const [mutating, setMutating] = useState(false)
  // Snapshot of the (status, isFull) pair at click time so the button label
  // can stay frozen for the full duration of the mutation, even though the
  // underlying ui values move optimistically and from the server response.
  const [labelSnapshot, setLabelSnapshot] = useState<LabelSnapshot | null>(null)
  const [error, setError] = useState<string | null>(null)

  const mountedRef = useRef(true)

  // Latest onAfterSuccess held in a ref so callers can pass inline arrows
  // without invalidating toggle's identity on every render.
  const onAfterSuccessRef = useRef(onAfterSuccess)
  useEffect(() => {
    onAfterSuccessRef.current = onAfterSuccess
  }, [onAfterSuccess])

  useEffect(() => {
    mountedRef.current = true
    return () => {
      mountedRef.current = false
    }
  }, [eventId])

  // Fetch the current user's attendance status on mount so state survives
  // navigation and page refresh. Uses GET /users/me/attendances filtered
  // client-side; there is no per-event GET endpoint in the current API.
  useEffect(() => {
    let active = true
    setInitializing(true)
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
        setInitializing(false)
      })
    return () => {
      active = false
    }
  }, [eventId])

  const loading = initializing || mutating

  const toggle = useCallback(
    (status: 'ATTENDING') => {
      if (loading) return

      const prev = ui
      const snapshot: LabelSnapshot = { status: ui.currentStatus, isFull: ui.isFull }
      const goingToAttend = ui.currentStatus === null

      let optimistic: UiState
      if (goingToAttend) {
        optimistic = {
          currentStatus: 'ATTENDING',
          attendingCount: ui.attendingCount + 1,
          isFull: ui.isFull,
        }
      } else {
        optimistic = {
          currentStatus: null,
          attendingCount:
            ui.currentStatus === 'ATTENDING'
              ? Math.max(0, ui.attendingCount - 1)
              : ui.attendingCount,
          isFull: ui.currentStatus === 'ATTENDING' ? false : ui.isFull,
        }
      }

      // Enter mutation: freeze the label, apply optimistic ui, clear error.
      setLabelSnapshot(snapshot)
      setMutating(true)
      setUi(optimistic)
      setError(null)

      const onError = (err: unknown) => {
        if (!mountedRef.current) return
        setUi(prev)
        setError(extractErrorMessage(err))
      }

      const finalize = () => {
        if (!mountedRef.current) return
        setMutating(false)
        setLabelSnapshot(null)
      }

      const runMutation = async () => {
        try {
          if (goingToAttend) {
            const attendance = await attend(eventId, status)
            if (!mountedRef.current) return
            setUi({
              currentStatus: attendance.status,
              attendingCount:
                attendance.status === 'WAITLISTED'
                  ? prev.attendingCount
                  : prev.attendingCount + 1,
              isFull: attendance.status === 'WAITLISTED',
            })
          } else {
            await unattend(eventId)
            if (!mountedRef.current) return
            // Optimistic state stands; refetch reconciles availableSpots /
            // waitlistedCount in case the server auto-promoted someone.
          }
          // Wait for the parent refetch chain so the button label only un-freezes
          // once the surrounding components have caught up to the new state.
          try {
            await onAfterSuccessRef.current?.()
          } catch {
            // A refetch failure should not roll back the successful mutation.
          }
        } catch (err) {
          onError(err)
        } finally {
          finalize()
        }
      }

      void runMutation()
    },
    [eventId, loading, ui],
  )

  return {
    currentStatus: ui.currentStatus,
    attendingCount: ui.attendingCount,
    loading,
    mutating,
    error,
    isFull: ui.isFull,
    displayStatus: labelSnapshot ? labelSnapshot.status : ui.currentStatus,
    displayIsFull: labelSnapshot ? labelSnapshot.isFull : ui.isFull,
    toggle,
  }
}
