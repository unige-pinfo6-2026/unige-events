import { Link } from 'react-router-dom'
import UserAvatar from '@/components/user/UserAvatar'
import type { UserPublicResponse } from '@/types/user'

/**
 * A single user row in the search results (bug ⑦) — avatar + displayName +
 * `@username`, linking to the public profile. Reusable wherever a user needs to
 * be rendered as a tappable card.
 */
export default function UserResultCard({ user }: Readonly<{ user: UserPublicResponse }>) {
  return (
    <Link
      to={`/profile/${user.username}`}
      className="flex items-center gap-3 p-3 rounded-2xl border border-border bg-linear-to-br from-background/80 to-background/40 backdrop-blur-xl hover:border-foreground/50 transition-colors no-underline"
    >
      <UserAvatar user={user} className="size-12 text-base" />
      <div className="flex-1 min-w-0">
        {user.displayName && (
          <p className="font-semibold text-foreground truncate">{user.displayName}</p>
        )}
        <p className="text-sm text-foreground/60 truncate">@{user.username}</p>
      </div>
    </Link>
  )
}
