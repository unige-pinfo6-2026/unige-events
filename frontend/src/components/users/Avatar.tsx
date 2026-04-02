interface AvatarProps {
  avatarUrl?: string | null
  displayName?: string | null
  size?: number
  className?: string
}

const Avatar = ({ avatarUrl, displayName, size = 40, className = '' }: AvatarProps) => {
  const initials = (displayName ?? '')
    .split(' ')
    .map((n) => n[0])
    .join('')
    .toUpperCase()
    .slice(0, 2)

  const base = `rounded-full shrink-0 overflow-hidden flex items-center justify-center bg-accent text-white font-bold ${className}`

  if (avatarUrl) {
    return (
      <div className={base} style={{ width: size, height: size }}>
        <img src={avatarUrl} alt={displayName ?? ''} className="w-full h-full object-cover" />
      </div>
    )
  }

  return (
    <div className={base} style={{ width: size, height: size, fontSize: size * 0.35 }}>
      {initials}
    </div>
  )
}

export default Avatar