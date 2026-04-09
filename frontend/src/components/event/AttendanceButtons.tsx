import { useAttendance } from '@/hooks/useAttendance'
import type { AttendanceStatus } from '@/types/attendance'
import { Heart, Users } from 'lucide-react'

interface AttendanceButtonsProps {
  eventId: number
  initialAttendingCount: number
  initialInterestedCount: number
  initialStatus: AttendanceStatus | null
}

const buttonBase =
  'inline-flex items-center gap-2 px-4 py-2 rounded-xl text-sm font-semibold transition-colors cursor-pointer border disabled:opacity-50 disabled:cursor-not-allowed'

const buttonVariants = {
  activeInterested: `${buttonBase} bg-accent/15 border-accent text-accent`,
  inactiveInterested: `${buttonBase} bg-transparent border-border text-foreground/70 hover:border-accent/50 hover:text-accent`,
  activeAttending: `${buttonBase} bg-primary/15 border-primary text-primary`,
  inactiveAttending: `${buttonBase} bg-transparent border-border text-foreground/70 hover:border-primary/50 hover:text-primary`,
}

export default function AttendanceButtons({
  eventId,
  initialAttendingCount,
  initialInterestedCount,
  initialStatus,
}: Readonly<AttendanceButtonsProps>) {
  const { currentStatus, attendingCount, interestedCount, loading, error, isFull, toggle } =
    useAttendance(eventId, initialAttendingCount, initialInterestedCount, initialStatus)

  const isAttendingDisabled = isFull && currentStatus !== 'ATTENDING'
  const tooltipId = `attending-full-tooltip-${eventId}`

  return (
    <div className="flex flex-col gap-3">
      <div className="flex flex-wrap gap-3">
        {/* Interested button */}
        <button
          type="button"
          onClick={() => toggle('INTERESTED')}
          disabled={loading}
          className={
            currentStatus === 'INTERESTED'
              ? buttonVariants.activeInterested
              : buttonVariants.inactiveInterested
          }
        >
          <Heart
            className="w-4 h-4"
            fill={currentStatus === 'INTERESTED' ? 'currentColor' : 'none'}
          />
          Je suis intéressé(e)
        </button>

        {/* Attending button with tooltip when full */}
        <div
          className="relative group"
          tabIndex={isAttendingDisabled ? 0 : undefined}
          role={isAttendingDisabled ? 'group' : undefined}
          aria-describedby={isAttendingDisabled ? tooltipId : undefined}
        >
          <button
            type="button"
            onClick={() => { if (!isAttendingDisabled) toggle('ATTENDING') }}
            disabled={loading}
            aria-disabled={isAttendingDisabled ? true : undefined}
            className={
              currentStatus === 'ATTENDING'
                ? buttonVariants.activeAttending
                : buttonVariants.inactiveAttending
            }
          >
            <Users
              className="w-4 h-4"
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
        {attendingCount} {attendingCount === 1 ? 'personne participe' : 'personnes participent'} ·{' '}
        {interestedCount} intéressée{interestedCount !== 1 ? 's' : ''}
      </p>

      {/* Error display */}
      {error && <p className="text-xs text-error">{error}</p>}
    </div>
  )
}
