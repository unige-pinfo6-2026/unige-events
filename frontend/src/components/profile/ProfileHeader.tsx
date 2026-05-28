import type { ReactNode } from 'react'
import UserAvatar from '@/components/user/UserAvatar'
import UserBanner from '@/components/user/UserBanner'
import StaffBadge from '@/components/profile/StaffBadge'
import { STUDY_LEVELS, type StudyLevel, type UserPublicResponse, isStaff } from '@/types/user'
import { FACULTIES, type Faculty } from '@/types/faculty'

interface ProfileHeaderProps {
  profile: UserPublicResponse
  /**
   * Optional right-side slot (Modifier link, FollowButton…). Rendered
   * inside the same flex row as the avatar + displayName so the header
   * stays balanced.
   */
  actions?: ReactNode
}

/**
 * Visual frame partagé par `PublicProfileView` et `ProfilePrivateState` :
 * banner (avec fallback gradient) + avatar overlapping en bas-gauche +
 * displayName + sous-titre faculté/niveau d'étude. Extrait pour garantir
 * un cadre visuellement identique au pixel près entre les deux vues (cf.
 * SCRUM-141 redesign — la vue privée doit donner l'impression d'un vrai
 * compte, pas d'un état d'erreur).
 */
export default function ProfileHeader({ profile, actions }: Readonly<ProfileHeaderProps>) {
  const showStaffBadge = isStaff(profile.roles)
  const studyLevelName = profile.studyLevel
    ? STUDY_LEVELS[profile.studyLevel as StudyLevel]?.name
    : null
  const facultyEntry = profile.faculty ? FACULTIES[profile.faculty as Faculty] : null
  const facultyName = facultyEntry?.name ?? null
  const profileSubtitle = [studyLevelName, facultyName].filter(Boolean).join(' · ')

  return (
    <>
      <UserBanner user={profile} className="h-52">
        <div className="absolute inset-0 bg-linear-to-t from-background/50 to-transparent" />
      </UserBanner>

      <div className="max-w-5xl mx-auto px-6 lg:px-8">
        <div className="relative z-10 -mt-14 flex flex-wrap items-end justify-between gap-4 mb-6">
          <div className="flex items-end gap-5">
            <div className="relative shrink-0">
              <UserAvatar user={profile} className="size-28 relative ring-4 ring-background shadow-2xl" />
            </div>
            <div className="pb-2 min-w-0">
              <div className="flex flex-wrap items-center gap-2">
                <h1 className="text-3xl lg:text-4xl font-bold tracking-tight leading-tight wrap-anywhere">
                  {profile.displayName}
                </h1>
                {showStaffBadge && <StaffBadge />}
              </div>
              {profile.username && (
                <p className="text-sm text-foreground/50 mt-0.5 font-medium">@{profile.username}</p>
              )}
              {profileSubtitle && (
                <p className="text-sm text-foreground/40 mt-1 font-medium">{profileSubtitle}</p>
              )}
            </div>
          </div>

          {actions}
        </div>
      </div>
    </>
  )
}
