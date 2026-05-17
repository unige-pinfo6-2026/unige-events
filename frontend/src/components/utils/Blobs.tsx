interface BlobProps {
  color?: keyof typeof colors
  size?: keyof typeof sizes
  position?: keyof typeof positions
  delay?: number
}

const colors = {
  accent: 'bg-accent/30',
  purple: 'bg-purple-600/25',
  blue: 'bg-blue-600/20',
  pink: 'bg-pink-500/30',
  cyan: 'bg-cyan-500/20',
} as const

const sizes = {
  sm: 'w-[300px] h-[300px]',
  md: 'w-[450px] h-[450px]',
  lg: 'w-[600px] h-[600px]',
  xl: 'w-[800px] h-[800px]',
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
      <div className="absolute top-0 left-1/4 w-150 h-150 bg-linear-to-br from-accent/40 via-pink-600/30 to-purple-600/30 rounded-full blur-3xl pointer-events-none" />
      <div className="absolute bottom-0 right-1/4 w-150 h-150 bg-linear-to-tl from-purple-600/40 via-blue-600/30 to-cyan-600/30 rounded-full blur-3xl pointer-events-none" />
    </>
  )
}

export function BlobsSubtle() {
  return (
    <>
      <Blob color="pink" position="top-right" size="md" delay={300} />
      <Blob color="blue" position="bottom-left" size="md" delay={1000} />
    </>
  )
}

/**
 * Landing-page-wide background — large soft halos scattered across the full
 * height of the page so the gradient is continuous as you scroll instead of
 * being clipped at each `SectionWrapper`'s `overflow-hidden`. Use it inside a
 * `relative overflow-hidden` parent that wraps every section.
 *
 * Positioned via percentage-of-parent (top-0 / top-1/4 / top-1/2 / ...) so the
 * blobs spread along the page rather than stacking up on the hero band.
 */
export function BlobsLanding() {
  return (
    <div className="absolute inset-0 -z-0 overflow-hidden pointer-events-none">
      <div className="absolute -top-20 left-1/4 w-[700px] h-[700px] bg-accent/30 rounded-full blur-3xl animate-pulse" />
      <div
        className="absolute top-[10%] right-1/4 w-[600px] h-[600px] bg-purple-600/25 rounded-full blur-3xl animate-pulse"
        style={{ animationDelay: '700ms' }}
      />
      <div
        className="absolute top-[22%] left-[-100px] w-[550px] h-[550px] bg-pink-500/25 rounded-full blur-3xl animate-pulse"
        style={{ animationDelay: '300ms' }}
      />
      <div
        className="absolute top-[35%] right-[-50px] w-[500px] h-[500px] bg-blue-600/20 rounded-full blur-3xl animate-pulse"
        style={{ animationDelay: '1000ms' }}
      />
      <div
        className="absolute top-[48%] left-1/3 w-[600px] h-[600px] bg-linear-to-br from-accent/25 via-pink-500/20 to-purple-600/25 rounded-full blur-3xl animate-pulse"
        style={{ animationDelay: '500ms' }}
      />
      <div
        className="absolute top-[60%] right-1/4 w-[550px] h-[550px] bg-pink-500/25 rounded-full blur-3xl animate-pulse"
        style={{ animationDelay: '200ms' }}
      />
      <div
        className="absolute top-[73%] left-[-80px] w-[500px] h-[500px] bg-cyan-500/20 rounded-full blur-3xl animate-pulse"
        style={{ animationDelay: '900ms' }}
      />
      <div
        className="absolute top-[85%] left-1/4 w-[650px] h-[650px] bg-linear-to-br from-accent/35 via-pink-600/25 to-purple-600/25 rounded-full blur-3xl animate-pulse"
        style={{ animationDelay: '600ms' }}
      />
      <div
        className="absolute -bottom-20 right-1/4 w-[600px] h-[600px] bg-linear-to-tl from-purple-600/30 via-blue-600/20 to-cyan-600/25 rounded-full blur-3xl animate-pulse"
        style={{ animationDelay: '1200ms' }}
      />
    </div>
  )
}