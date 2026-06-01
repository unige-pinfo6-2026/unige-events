import { useEffect, useState } from 'react'
import { useToast } from '@/hooks/useToast'
import { TOAST_VARIANTS, type ToastType } from '@/types/toast'

function Toast({
  id,
  type: variant,
  message,
  duration = 5000,
}: Readonly<{
  id: number
  type: ToastType
  message: string
  duration?: number
}>) {
  const { dismiss } = useToast()
  const [hiding, setHiding] = useState(false)
  const { className, icon: Icon } = TOAST_VARIANTS[variant]

  useEffect(() => {
    const t = setTimeout(() => setHiding(true), duration)
    return () => clearTimeout(t)
  }, [duration])

  useEffect(() => {
    if (!hiding) return
    const t = setTimeout(() => dismiss(id), 400)
    return () => clearTimeout(t)
  }, [hiding, id, dismiss])

  return (
    <button
      type="button"
      className={[
        'flex items-center gap-2.5 px-5 py-4 rounded-2xl text-sm font-medium shadow-2xl border backdrop-blur-xl bg-background/90 cursor-pointer text-left',
        hiding ? 'animate-toast-out' : 'animate-toast-in',
        className,
      ].join(' ')}
      onClick={() => setHiding(true)}
    >
      <Icon className="size-5 shrink-0" aria-hidden="true" />
      <span>{message}</span>
    </button>
  )
}

export default function ToastsWrapper() {
  const { toasts } = useToast()

  return (
    <div className="fixed z-50 bottom-3 right-3 flex flex-col gap-2 items-end">
      {toasts.map(toast => (
        <Toast key={toast.id} {...toast} />
      ))}
    </div>
  )
}
