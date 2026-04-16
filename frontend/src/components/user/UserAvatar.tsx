import { useState } from "react"
import type { User } from "@/types/user"

interface UserAvatarProps {
  user: User | null
  className?: string
}

export default function UserAvatar({ user, className = '' }: Readonly<UserAvatarProps>) {
  const [errorSrc, setErrorSrc] = useState<string | null>(null)
  const src = user?.avatarUrl ?? null
  const showImage = Boolean(src) && src !== errorSrc

  const initials = (user?.displayName ?? '').split(' ').map((n) => n[0]).join('').toUpperCase().slice(0, 2)

  return (
    <div className={`rounded-full shrink-0 overflow-hidden flex items-center justify-center bg-accent text-white font-bold ${className}`}>
      {showImage ? (
        <img src={src ?? undefined} alt={user?.displayName ?? ''} className="w-full h-full object-cover" onError={() => setErrorSrc(src)} />
      ) : (
        <p className="text-center leading-none">{initials}</p>
      )}
    </div>
  )
}
