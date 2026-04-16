import { type ReactNode, useState } from 'react'
import type { User } from '@/types/user'

interface UserBannerProps {
  user: User | null
  className?: string
  children?: ReactNode
}

export default function UserBanner({ user, className = '', children }: Readonly<UserBannerProps>) {
  const [errorSrc, setErrorSrc] = useState<string | null>(null)
  const src = user?.bannerUrl ?? null
  const showImage = Boolean(src) && src !== errorSrc

  return (
    <div className={`relative overflow-hidden bg-cover bg-center ${className}`}>
      {showImage ? (
        <img
          src={src ?? undefined}
          alt=""
          aria-hidden="true"
          className="absolute inset-0 w-full h-full object-cover"
          onError={() => setErrorSrc(src)}
        />
      ) : (
        <div className="absolute inset-0 bg-linear-to-br from-accent/20 via-pink-600/15 to-purple-600/20" />
      )}
      {children}
    </div>
  )
}
