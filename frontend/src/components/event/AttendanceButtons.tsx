import { useNavigate } from 'react-router-dom'
import { useAuth } from '@/hooks/useAuth'
import { useAttendance } from '@/hooks/useAttendance'
import { Users } from 'lucide-react'

interface AttendanceButtonsProps {
  eventId: number
  initialAttendingCount: number
  initialStatus: 'ATTENDING' | null
}

const buttonBase =
  'flex w-full items-center justify-center gap-2 px-6 py-3.5 rounded-xl text-base font-semibold transition-colors cursor-pointer border disabled:opacity-50 disabled:cursor-not-allowed'

const buttonVariants = {
  activeAttending: `${buttonBase} bg-primary/15 border-primary text-primary`,
  inactiveAttending: `${buttonBase} bg-transparent border-border text-foreground/70 hover:border-primary/50 hover:text-primary`,
}

export default function AttendanceButtons({
  eventId,
  initialAttendingCount,
  initialStatus,
}: Readonly<AttendanceButtonsProps>) {
  const { currentStatus, attendingCount, loading, error, isFull, toggle } =
    useAttendance(eventId, initialAttendingCount, initialStatus)
  const { isAuthenticated } = useAuth()
  const navigate = useNavigate()

  const isAttendingDisabled = isFull && currentStatus !== 'ATTENDING'
  const tooltipId = `attending-full-tooltip-${eventId}`

  const handleToggle = () => {
    if (!isAuthenticated) {
      navigate('/login')
      return
    }
    toggle('ATTENDING')
  }

  return (
    <div className="flex flex-col gap-3">
      <div className="flex flex-wrap gap-3">
        {/* Attending button with tooltip when full */}
        <div
          className="relative group w-full"
          tabIndex={isAttendingDisabled ? 0 : undefined}
          role={isAttendingDisabled ? 'group' : undefined}
          aria-describedby={isAttendingDisabled ? tooltipId : undefined}
        >
          <button
            type="button"
            onClick={() => { if (!isAttendingDisabled) handleToggle() }}
            disabled={loading}
            aria-disabled={isAttendingDisabled ? true : undefined}
            className={
              currentStatus === 'ATTENDING'
                ? buttonVariants.activeAttending
                : buttonVariants.inactiveAttending
            }
          >
            <Users
              className="w-5 h-5"
              fill={currentStatus === 'ATTENDING' ? 'currentColor' : 'none'}
            />
            Je participe
          </button>

          <div
            id={tooltipId}
            role="tooltip"
            className={`pointer-events-none absolute bottom-full left-1/2 -translate-x-1/2 mb-2 whitespace-nowrap rounded-xl bg-foreground px-3 py-1.5 text-xs font-medium text-background transition-opacity z-10 ${
              isAttendingDisabled
                ? 'opacity-0 group-hover:opacity-100 group-focus-within:opacity-100'
                : 'hidden'
            }`}
          >
            Événement complet
          </div>
        </div>
      </div>

      {/* Live counter */}
      <p className="text-xs text-foreground/50">
        {attendingCount} {attendingCount === 1 ? 'personne participe' : 'personnes participent'}
      </p>

      {/* Error display */}
      {error && <p className="text-xs text-error">{error}</p>}
    </div>
  )
}
