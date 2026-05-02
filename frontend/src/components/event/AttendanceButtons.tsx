import { useNavigate } from 'react-router-dom'
import { useAuth } from '@/hooks/useAuth'
import { useAttendance } from '@/hooks/useAttendance'
import type { AttendanceStatus } from '@/types/attendance'
import { Users } from 'lucide-react'

interface AttendanceButtonsProps {
  eventId: number
  initialAttendingCount: number
  initialStatus: AttendanceStatus | null
  availableSpots?: number | null
}

const buttonBase =
  'flex w-full items-center justify-center gap-2 px-6 py-3.5 rounded-xl text-base font-semibold transition-colors cursor-pointer border disabled:opacity-50 disabled:cursor-not-allowed'

const buttonVariants = {
  activeAttending:   `${buttonBase} bg-primary/15 border-primary text-primary`,
  inactiveAttending: `${buttonBase} bg-transparent border-border text-foreground/70 hover:border-primary/50 hover:text-primary`,
  waitlistJoin:      `${buttonBase} bg-warning/10 border-warning/40 text-warning hover:bg-warning/20`,
  activeWaitlisted:  `${buttonBase} bg-warning/15 border-warning text-warning`,
} as const

type ButtonVariantKey = keyof typeof buttonVariants

const buttonLabels: Record<ButtonVariantKey, string> = {
  activeAttending:   'Annuler ma participation',
  inactiveAttending: 'Je participe',
  waitlistJoin:      "Rejoindre la liste d'attente",
  activeWaitlisted:  "En liste d'attente",
}

function getButtonVariant(
  currentStatus: AttendanceStatus | null,
  isFull: boolean,
): ButtonVariantKey {
  if (currentStatus === 'ATTENDING') return 'activeAttending'
  if (currentStatus === 'WAITLISTED') return 'activeWaitlisted'
  if (isFull) return 'waitlistJoin'
  return 'inactiveAttending'
}

export default function AttendanceButtons({
  eventId,
  initialAttendingCount,
  initialStatus,
  availableSpots,
}: Readonly<AttendanceButtonsProps>) {
  const { currentStatus, attendingCount, loading, error, isFull, toggle } =
    useAttendance(eventId, initialAttendingCount, initialStatus, availableSpots)
  const { isAuthenticated } = useAuth()
  const navigate = useNavigate()

  const handleToggle = () => {
    if (!isAuthenticated) {
      navigate('/login')
      return
    }
    toggle('ATTENDING')
  }

  const variant = getButtonVariant(currentStatus, isFull)

  return (
    <div className="flex flex-col gap-3">
      <button
        type="button"
        onClick={handleToggle}
        disabled={loading}
        className={buttonVariants[variant]}
      >
        <Users
          className="w-5 h-5"
          fill={currentStatus === 'ATTENDING' ? 'currentColor' : 'none'}
        />
        {buttonLabels[variant]}
      </button>

      {/* Live counter */}
      <p className="text-xs text-foreground/50">
        {attendingCount} {attendingCount === 1 ? 'personne participe' : 'personnes participent'}
      </p>

      {/* Error display */}
      {error && <p className="text-xs text-error">{error}</p>}
    </div>
  )
}
