import type { User } from '@/types/user'
import UserAvatar from './UserAvatar'
import { Link } from 'react-router-dom'
import { useAuth } from '@/hooks'
import { useTheme } from '@/contexts/ThemeContext'
import { Skeleton } from 'boneyard-js/react'

interface UserIdentityProps {
  user: User | null
  loading?: boolean
  variant?: keyof typeof variants
}

const variants = {
  inline: { wrapper: 'flex items-center gap-3', avatarClass: 'size-8', nameSize: 'sm' },
  card: { wrapper: 'flex flex-col gap-3', avatarClass: 'size-18', nameSize: 'lg' },
} as const

const fixtureVariants = {
  inline: (
    <div className="flex items-center gap-3">
      <div className="rounded-full shrink-0" style={{ width: 32, height: 32 }} />
      <span className="text-sm font-semibold">Elie Bsd</span>
    </div>
  ),
  card: (
    <div className="flex flex-col gap-3">
      <div className="rounded-full shrink-0" style={{ width: 72, height: 72 }} />
      <div className="flex flex-col gap-0.5">
        <span className="text-lg font-semibold">Elie Bsd</span>
        <span className="text-xs">@username</span>
      </div>
    </div>
  ),
} as const

export default function UserIdentity({ user, loading = false, variant = 'inline' }: Readonly<UserIdentityProps>) {
  const { wrapper, avatarClass, nameSize } = variants[variant]
  const { user: currentUser } = useAuth()
  const { theme } = useTheme()
  const skeletonColor = theme === 'dark' ? 'rgba(255,255,255,0.06)' : 'rgba(0,0,0,0.08)'
  const profileUrl =
    currentUser?.id === user?.id
      ? '/profile/me'
      : `/profile/${encodeURIComponent(user?.username ?? '')}`

  if (loading) {
    return (
      <Skeleton name={`user-identity-${variant}`} loading animate="pulse" color={skeletonColor}>
        {fixtureVariants[variant]}
      </Skeleton>
    )
  }

  if (!user) return null

  return (
    <Link to={profileUrl} className={wrapper}>
      <UserAvatar user={user} className={avatarClass} />

      <div className="flex flex-col gap-0.5">
        <span className={`text-${nameSize} font-semibold text-foreground`}>{user.displayName}</span>

        {variant === 'card' && (
          <span className="text-sm text-foreground/40 font-light">
            @{user.username}
          </span>
        )}
      </div>
    </Link>
  )
}
