import { Link } from 'react-router-dom'
import UserAvatar from '@/components/user/UserAvatar'
import { FACULTIES, type Faculty } from '@/types/faculty'
import { STUDY_LEVELS, type StudyLevel, type UserPublicResponse } from '@/types/user'

interface FollowListRowProps {
  user: UserPublicResponse
  /** When provided (own followers list), renders a "Retirer" action that drops
   *  this user from the caller's followers. */
  onRemove?: (userId: string) => void
}

/**
 * Single row in the followers / following list (SCRUM-142). Avatar +
 * displayName + faculty/study-level subtitle, the whole row links to
 * `/profile/{username}`.
 *
 * The row deliberately does **not** render a `FollowButton`: the backend's
 * list projection forces `followStatus = null` on every item (cf. openapi
 * spec for `/users/{id}/followers` — counters and followStatus only make
 * sense on the target profile, not on list items). Rendering the button
 * with `null` would always show "Suivre" and a click would 409 on
 * already-followed users — bad UX. The row's profile link lets the user
 * navigate to the target's profile where the FollowButton sees the
 * correct followStatus.
 */
export default function FollowListRow({ user, onRemove }: Readonly<FollowListRowProps>) {
  const studyLevelName = user.studyLevel
    ? STUDY_LEVELS[user.studyLevel as StudyLevel]?.name
    : null
  const facultyName = user.faculty
    ? FACULTIES[user.faculty as Faculty]?.abbr
    : null
  const subtitle = [studyLevelName, facultyName].filter(Boolean).join(' · ')
  const displayName = user.displayName ?? user.username

  return (
    // The hover band lives on the whole row (incl. the "Retirer" button), not
    // just the link, so the highlight spans the full width.
    <li className="flex items-center gap-2 py-3 px-2 -mx-2 rounded-2xl transition-colors hover:bg-foreground/5">
      <Link
        to={`/profile/${user.username}`}
        className="flex-1 min-w-0 flex items-center gap-3 no-underline rounded-2xl focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent"
      >
        <UserAvatar user={user} className="size-12" />
        <div className="flex-1 min-w-0">
          <p className="text-sm font-semibold text-foreground truncate">
            {displayName}
          </p>
          <p className="text-xs text-foreground/40 truncate">
            @{user.username}
            {subtitle && <span> · {subtitle}</span>}
          </p>
        </div>
      </Link>
      {onRemove && (
        <button
          type="button"
          onClick={() => onRemove(user.id)}
          className="shrink-0 px-3 py-1.5 text-xs font-semibold rounded-lg border border-border text-foreground/60 hover:text-error hover:border-error/40 hover:bg-error/5 transition-colors cursor-pointer bg-transparent"
        >
          Retirer
        </button>
      )}
    </li>
  )
}
