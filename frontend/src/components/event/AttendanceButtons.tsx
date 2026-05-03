import { useNavigate } from 'react-router-dom'
import { useAuth } from '@/hooks/useAuth'
import { useAttendance } from '@/hooks/useAttendance'
import type { AttendanceStatus } from '@/types/attendance'
import { Loader2, Users } from 'lucide-react'

interface AttendanceButtonsProps {
  eventId: number
  initialAttendingCount: number
  initialStatus: AttendanceStatus | null
  availableSpots?: number | null
  onAfterSuccess?: () => void | Promise<void>
}

const buttonBase =
  'flex w-full items-center justify-center gap-2 px-6 py-3.5 rounded-xl text-base font-semibold transition-colors border disabled:opacity-50 disabled:cursor-not-allowed'

const buttonVariants = {
  activeAttending:   `${buttonBase} cursor-pointer bg-primary/15 border-primary text-primary`,
  inactiveAttending: `${buttonBase} cursor-pointer bg-transparent border-border text-foreground/70 hover:border-primary/50 hover:text-primary`,
  waitlistJoin:      `${buttonBase} cursor-pointer bg-warning/10 border-warning/40 text-warning hover:bg-warning/20`,
  activeWaitlisted:  `${buttonBase} cursor-pointer bg-warning/15 border-warning text-warning`,
} as const

type ButtonVariantKey = keyof typeof buttonVariants

const buttonLabels: Record<ButtonVariantKey, string> = {
  activeAttending:   'Annuler ma participation',
  inactiveAttending: 'Je participe',
  waitlistJoin:      "Rejoindre la liste d'attente",
  activeWaitlisted:  "Quitter la liste d'attente",
}

function getButtonVariant(
  status: AttendanceStatus | null,
  isFull: boolean,
): ButtonVariantKey {
  if (status === 'ATTENDING') return 'activeAttending'
  if (status === 'WAITLISTED') return 'activeWaitlisted'
  if (isFull) return 'waitlistJoin'
  return 'inactiveAttending'
}

export default function AttendanceButtons({
  eventId,
  initialAttendingCount,
  initialStatus,
  availableSpots,
  onAfterSuccess,
}: Readonly<AttendanceButtonsProps>) {
  const {
    attendingCount,
    loading,
    mutating,
    error,
    displayStatus,
    displayIsFull,
    toggle,
  } = useAttendance(eventId, initialAttendingCount, initialStatus, availableSpots, { onAfterSuccess })
  const { isAuthenticated } = useAuth()
  const navigate = useNavigate()

  const handleToggle = () => {
    if (!isAuthenticated) {
      navigate('/login')
      return
    }
    toggle('ATTENDING')
  }

  // Variant + label are computed from `display*` values, which are frozen to
  // the click-time snapshot during a mutation. This guarantees the user
  // never sees an intermediate (e.g. "Rejoindre la liste d'attente") flash
  // while the optimistic state and the server response are still settling.
  const variant = getButtonVariant(displayStatus, displayIsFull)
  const label = buttonLabels[variant]

  return (
    <div className="flex flex-col gap-3">
      <button
        type="button"
        onClick={handleToggle}
        disabled={loading}
        aria-label={label}
        aria-busy={mutating || undefined}
        style={mutating ? { cursor: 'wait' } : undefined}
        className={buttonVariants[variant]}
      >
        {mutating ? (
          <Loader2 className="w-5 h-5 animate-spin" aria-hidden="true" />
        ) : (
          <Users
            className="w-5 h-5"
            fill={displayStatus === 'ATTENDING' ? 'currentColor' : 'none'}
          />
        )}
        {label}
      </button>

      {/* Live counter — updates optimistically for snappy feedback. */}
      <p className="text-xs text-foreground/50">
        {attendingCount} {attendingCount === 1 ? 'personne participe' : 'personnes participent'}
      </p>

      {/* Error display */}
      {error && <p className="text-xs text-error">{error}</p>}
    </div>
  )
}
