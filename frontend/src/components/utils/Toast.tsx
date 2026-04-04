export default function Toast({ type, message }: Readonly<{ type: 'success' | 'error'; message: string }>) {
  const colorClass = type === 'success'
    ? 'border-emerald-500/40 text-emerald-400'
    : 'border-red-400/40 text-red-400'

  return (
    <output className={['fixed bottom-6 right-6 px-5 py-3.5 rounded-2xl text-sm font-medium shadow-2xl z-50 border backdrop-blur-xl bg-background/90', colorClass].join(' ')}>
      {message}
    </output>
  )
}
