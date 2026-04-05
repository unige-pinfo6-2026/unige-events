import { useEffect, useState } from 'react'

const variants = {
  success: 'border-emerald-500/40 text-emerald-400',
  error: 'border-error/40 text-error',
}

export default function Toast({
  type: variant,
  message,
  duration = 5000
}: Readonly<{
  type: keyof typeof variants
  message: string
  duration?: number
}>) {
  const [hiding, setHiding] = useState(false)

  useEffect(() => {
    const t = setTimeout(() => setHiding(true), duration)
    return () => clearTimeout(t)
  }, [duration])

  return (
    <output
      className={[
        'fixed top-3 right-3 px-5 py-3.5 rounded-2xl text-sm font-medium shadow-2xl z-50 border backdrop-blur-xl bg-background/90',
        hiding ? 'animate-toast-out' : 'animate-toast-in',
        variants[variant],
      ].join(' ')}
    >
      {message}
    </output>
  )
}
