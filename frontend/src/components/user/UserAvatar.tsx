import { useState } from "react"
import type { User } from "@/types/user"

interface UserAvatarProps {
  user: User | null,
  size?: keyof typeof sizes,
  className?: string
}

const sizes = {
  'sm': "size-8",
  'md': "size-10",
  'lg': "size-12",
  'xl': "size-14"
} as const

export default function UserAvatar({ user, size = "lg", className }: Readonly<UserAvatarProps>) {
  const [error, setError] = useState(false)

  const initials = (user?.displayName ?? '').split(' ').map((n) => n[0]).join('').toUpperCase().slice(0, 2)

  return (
    <div className={`${sizes[size]} rounded-full shrink-0 overflow-hidden flex items-center justify-center bg-accent text-white font-bold ${className}`}>
      {user?.avatarUrl && !error ? (
        <img src={user.avatarUrl} alt={user.displayName ?? ''} className="w-full h-full object-cover" onError={() => setError(true)} />
      ) : (
        <p className="text-center leading-none">{initials}</p>
      )}
    </div>
  )
}