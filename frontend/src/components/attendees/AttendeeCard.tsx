import { Link } from 'react-router-dom'
import UserAvatar from '@/components/user/UserAvatar'
import type { Attendance } from '@/types/attendance'
import WaitlistBadge from './WaitlistBadge'

interface AttendeeCardProps {
  attendance: Attendance
}

const cardClass =
  'flex items-center gap-3 rounded-2xl border border-border bg-linear-to-br from-background/80 to-background/40 backdrop-blur-xl p-3 transition-colors hover:border-foreground/30 no-underline'

function IdentityBody({ attendance }: Readonly<{ attendance: Attendance }>) {
  return (
    <>
      <UserAvatar user={attendance} className="size-10" />
      <div className="flex flex-col min-w-0 flex-1">
        <span className="text-sm font-semibold text-foreground truncate">
          {attendance.displayName}
        </span>
      </div>
    </>
  )
}

function AnonymousBody() {
  return (
    <>
      <div
        aria-label="Avatar anonyme"
        className="size-10 rounded-full shrink-0 bg-foreground/10 border border-border"
      />
      <div className="flex flex-col min-w-0 flex-1">
        <span className="text-sm font-semibold text-foreground/50 truncate">
          Utilisateur anonyme
        </span>
        <span className="text-xs text-foreground/30 truncate">Profil privé</span>
      </div>
    </>
  )
}

// Anonymized rows (private profiles seen by non-organizers + orphan rows from
// deleted users) arrive with displayName=null from the backend. We render the
// anonymous body in both cases — there's no real identity to expose. Rows with
// a known userId AND a displayName link to /profile/{userId}; admins/organizers
// who legitimately see a private profile reach the linked page where the user
// profile resource enforces its own visibility cascade.
export default function AttendeeCard({ attendance }: Readonly<AttendeeCardProps>) {
  const isWaitlisted = attendance.status === 'WAITLISTED'
  const hasIdentity = attendance.displayName !== null
  const hasLinkableProfile = hasIdentity && attendance.userId !== null

  if (!hasIdentity) {
    return (
      <div className={cardClass}>
        <AnonymousBody />
        {isWaitlisted && <WaitlistBadge />}
      </div>
    )
  }

  if (!hasLinkableProfile) {
    // Edge case: displayName present but userId null — currently unreachable in
    // the backend contract, but guard against it so we never produce
    // `/profile/null`.
    return (
      <div className={cardClass}>
        <IdentityBody attendance={attendance} />
        {isWaitlisted && <WaitlistBadge />}
      </div>
    )
  }

  return (
    <Link to={`/profile/${attendance.userId}`} className={cardClass}>
      <IdentityBody attendance={attendance} />
      {isWaitlisted && <WaitlistBadge />}
    </Link>
  )
}
