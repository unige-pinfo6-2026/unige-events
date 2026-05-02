import { Link } from 'react-router-dom'
import UserAvatar from '@/components/user/UserAvatar'
import { STUDY_LEVELS, type StudyLevel, type UserPublicResponse } from '@/types/user'
import { FACULTIES, type Faculty } from '@/types/faculty'
import type { Attendance } from '@/types/attendance'
import WaitlistBadge from './WaitlistBadge'

interface AttendeeCardProps {
  attendance: Attendance
  profile: UserPublicResponse | null
}

const cardClass =
  'flex items-center gap-3 rounded-2xl border border-border bg-linear-to-br from-background/80 to-background/40 backdrop-blur-xl p-3 transition-colors hover:border-foreground/30 no-underline'

function getProfileMeta(profile: UserPublicResponse) {
  const studyLevelName = profile.studyLevel
    ? STUDY_LEVELS[profile.studyLevel as StudyLevel]?.name
    : null
  const facultyName = profile.faculty
    ? FACULTIES[profile.faculty as Faculty]?.abbr
    : null
  return [studyLevelName, facultyName].filter(Boolean).join(' · ')
}

function ProfileBody({ profile }: Readonly<{ profile: UserPublicResponse }>) {
  const subtitle = getProfileMeta(profile)
  const displayName = profile.displayName ?? 'Utilisateur'
  return (
    <>
      <UserAvatar user={profile} className="size-10" />
      <div className="flex flex-col min-w-0 flex-1">
        <span className="text-sm font-semibold text-foreground truncate">
          {displayName}
        </span>
        {subtitle && (
          <span className="text-xs text-foreground/50 truncate">{subtitle}</span>
        )}
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

export default function AttendeeCard({ attendance, profile }: Readonly<AttendeeCardProps>) {
  const isWaitlisted = attendance.status === 'WAITLISTED'

  if (profile === null) {
    return (
      <div className={cardClass}>
        <AnonymousBody />
        {isWaitlisted && <WaitlistBadge />}
      </div>
    )
  }

  return (
    <Link to={`/profile/${encodeURIComponent(profile.username)}`} className={cardClass}>
      <ProfileBody profile={profile} />
      {isWaitlisted && <WaitlistBadge />}
    </Link>
  )
}
