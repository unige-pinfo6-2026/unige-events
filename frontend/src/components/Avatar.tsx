import type { CSSProperties } from 'react'

interface AvatarProps {
  avatarUrl?: string | null
  displayName?: string | null
  size?: number
  style?: CSSProperties
}

function Avatar({ avatarUrl, displayName, size = 40, style }: AvatarProps) {
  const initials = (displayName ?? '')
    .split(' ')
    .map((n) => n[0])
    .join('')
    .toUpperCase()
    .slice(0, 2)

  const circleStyle: CSSProperties = {
    width: size,
    height: size,
    borderRadius: '50%',
    flexShrink: 0,
    overflow: 'hidden',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    background: '#CF0063',
    color: 'white',
    fontWeight: 700,
    fontSize: size * 0.35,
    ...style,
  }

  if (avatarUrl) {
    return (
      <div style={{ ...circleStyle, background: 'transparent' }}>
        <img
          src={avatarUrl}
          alt={displayName ?? ''}
          style={{ width: '100%', height: '100%', objectFit: 'cover' }}
        />
      </div>
    )
  }

  return <div style={circleStyle}>{initials}</div>
}

export default Avatar
