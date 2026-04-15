interface BlobProps {
  color?: keyof typeof colors
  size?: keyof typeof sizes
  position?: keyof typeof positions
  delay?: number
}

const colors = {
  accent: 'bg-accent/20',
  purple: 'bg-purple-600/15',
  blue: 'bg-blue-600/15',
  pink: 'bg-pink-500/20',
} as const

const sizes = {
  sm: 'w-[300px] h-[300px]',
  md: 'w-[450px] h-[450px]',
  lg: 'w-[600px] h-[600px]',
} as const

const positions = {
  'top-left': 'top-0 left-1/4',
  'top-right': 'top-0 right-1/4',
  'bottom-left': 'bottom-0 left-1/4',
  'bottom-right': 'bottom-0 right-1/4',
} as const

function Blob({ color = 'accent', size = 'md', position = 'top-left', delay = 0 }: Readonly<BlobProps>) {
  return (
    <div
      className={`absolute rounded-full blur-3xl animate-pulse pointer-events-none ${colors[color]} ${sizes[size]} ${positions[position]}`}
      style={delay ? { animationDelay: `${delay}ms` } : undefined}
    />
  )
}

export function Blobs() {
  return (
    <>
      <Blob color="accent" position="top-left" />
      <Blob color="purple" position="bottom-right" size="lg" delay={700} />
    </>
  )
}

export function BlobsHero() {
  return (
    <>
      <Blob color="accent" position="top-left" size="lg" />
      <Blob color="purple" position="bottom-right" size="lg" delay={700} />
    </>
  )
}

export function BlobsCta() {
  return (
    <>
      <div className="absolute top-0 left-1/4 w-150 h-150 bg-linear-to-br from-accent/30 via-pink-600/20 to-purple-600/20 rounded-full blur-3xl pointer-events-none" />
      <div className="absolute bottom-0 right-1/4 w-150 h-150 bg-linear-to-tl from-purple-600/30 via-blue-600/20 to-cyan-600/20 rounded-full blur-3xl pointer-events-none" />
    </>
  )
}

export function BlobsSubtle() {
  return (
    <>
      <Blob color="pink" position="top-right" size="sm" delay={300} />
      <Blob color="blue" position="bottom-left" size="sm" delay={1000} />
    </>
  )
}