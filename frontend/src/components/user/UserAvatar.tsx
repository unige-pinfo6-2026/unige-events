import type { User } from "@/types/user"

export default function UserAvatar({ user, size = 40, className }: Readonly<{ user: User | null, size?: number, className?: string}>) {
  const base = `rounded-full shrink-0 overflow-hidden flex items-center justify-center bg-accent text-white font-bold ${className}`

  if(user?.avatarUrl) {
    return (
      <div className={base} style={{ width: size, height: size }}>
        <img src={user.avatarUrl} alt={user.displayName ?? ''} className="w-full h-full object-cover" />
      </div>
    )
  }

  const initials = (user?.displayName ?? '').split(' ').map((n) => n[0]).join('').toUpperCase().slice(0, 2)

  return (
    <div className={base} style={{ width: size, height: size, fontSize: size * 0.35 }}>
      {initials}
    </div>
  )
}