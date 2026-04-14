import type { User } from '@/types/user'
import UserAvatar from './UserAvatar'
import { Link } from 'react-router-dom'
import { useAuth } from '@/hooks'

interface UserIdentityProps {
  user: User,
  variant?: keyof typeof variants
}

const variants = {
  inline: { wrapper: 'flex items-center gap-3', avatarSize: "sm", nameSize: "sm" },
  card: { wrapper: 'flex flex-row items-center gap-3', avatarSize: "lg", nameSize: "lg" },
} as const

export default function UserIdentity({ user, variant = 'inline' }: Readonly<UserIdentityProps>) {
  const { wrapper, avatarSize, nameSize } = variants[variant]
  
  const { user: currentUser } = useAuth()
  const profileUrl = currentUser?.id === user.id ? '/profile/me' : `/profile/${user.id}`

  return (
    <Link to={profileUrl} className={wrapper}>
      <UserAvatar user={user} size={avatarSize} />

      <div className="flex flex-col gap-0.5">
        <span className={`text-${nameSize} font-semibold text-foreground`}>{user.displayName}</span>
        
        {variant === 'card' && (
          <span className="text-xs text-foreground/40 font-light">
            {/* TODO: SPRINT 5 : Username */}
            @{user.username ?? "username"}
          </span>
        )}
      </div>
    </Link>
  )
}
