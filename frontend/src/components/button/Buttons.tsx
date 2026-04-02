// TODO rework c'est horrible

export const ButtonPrimary = ({ children, onClick, className = '', size = 'md' }: {
  children: React.ReactNode
  onClick?: () => void
  className?: string
  size?: 'sm' | 'md' | 'lg'
}) => {
  const pad = size === 'sm' ? 'px-4 py-2 text-sm' : size === 'lg' ? 'px-8 py-4 text-lg' : 'px-6 py-3 text-base'
  return (
    <button
      onClick={onClick}
      className={`inline-flex items-center gap-2 font-semibold rounded-xl bg-linear-to-r from-accent to-pink-600 hover:from-accent/90 hover:to-pink-600/90 text-white shadow-xl shadow-accent/30 transition-all cursor-pointer border-0 ${pad} ${className}`}
    >
      {children}
    </button>
  )
}

export const ButtonSecondary = ({ children, onClick, className = '', size = 'md' }: {
  children: React.ReactNode
  onClick?: () => void
  className?: string
  size?: 'sm' | 'md' | 'lg'
}) => {
  const pad = size === 'sm' ? 'px-4 py-2 text-sm' : size === 'lg' ? 'px-8 py-4 text-lg' : 'px-6 py-3 text-base'
  return (
    <button
      onClick={onClick}
      className={`inline-flex items-center gap-2 font-semibold rounded-xl border-2 border-white/10 hover:border-accent/50 hover:bg-white/5 text-white transition-all cursor-pointer bg-transparent ${pad} ${className}`}
    >
      {children}
    </button>
  )
}