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
// a displayName link to /profile/{username|userId}: prefer the SCRUM-169
// username slug when present, fall back to the UUID (ProfilePage's transient
// redirect handles the UUID path — Décision I). If both are null (defensive —
// the SCRUM-S7 privacy filter nulls them together but never strands a
// displayName), we render the identity body inline without a link to avoid
// producing /profile/null.
export default function AttendeeCard({ attendance }: Readonly<AttendeeCardProps>) {
  const isWaitlisted = attendance.status === 'WAITLISTED'
  const hasIdentity = attendance.displayName !== null
  const profileSlug = attendance.username ?? attendance.userId
  const hasLinkableProfile = hasIdentity && profileSlug !== null

  if (!hasIdentity) {
    return (
      <div className={cardClass}>
        <AnonymousBody />
        {isWaitlisted && <WaitlistBadge />}
      </div>
    )
  }

  if (!hasLinkableProfile) {
    return (
      <div className={cardClass}>
        <IdentityBody attendance={attendance} />
        {isWaitlisted && <WaitlistBadge />}
      </div>
    )
  }

  return (
    <Link to={`/profile/${profileSlug}`} className={cardClass}>
      <IdentityBody attendance={attendance} />
      {isWaitlisted && <WaitlistBadge />}
    </Link>
  )
}
