import type { User } from '@/types/user'
import UserAvatar from './UserAvatar'

const variants = {
  inline: { wrapper: 'flex items-center gap-3', avatarSize: 32, nameSize: "sm" },
  card: { wrapper: 'flex flex-row items-center gap-3', avatarSize: 52, nameSize: "lg" },
}

export default function UserIdentity({ user, variant = 'inline' }: Readonly<{ user: User; variant?: keyof typeof variants }>) {
  const { wrapper, avatarSize, nameSize } = variants[variant]

  return (
    <div className={wrapper}>
      <UserAvatar user={user} size={avatarSize} />

      <div className="flex flex-col gap-0.5">
        <span className={`text-${nameSize} font-semibold text-foreground`}>{user.displayName}</span>
        
        {variant === 'card' && (
          <span className="text-xs text-foreground/40 font-light">
            @{user.username ?? "username"}
          </span>
        )}
      </div>
    </div>
  )
}
