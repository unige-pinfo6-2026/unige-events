import type { User } from '@/types/user'
import UserAvatar from './UserAvatar'

export default function UserIdentity({ user, avatarSize = 32 }: Readonly<{ user: User; avatarSize?: number }>) {
  return (
    <div className="flex items-center gap-3">
      <UserAvatar user={user} size={avatarSize} />
      <span className="text-sm font-semibold text-foreground">{user.displayName}</span>
    </div>
  )
}
