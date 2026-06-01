import { CheckCircle2, Info, XCircle } from 'lucide-react'

export const TOAST_VARIANTS = {
  success: { className: 'border-emerald-500/40 text-emerald-400', icon: CheckCircle2 },
  info: { className: 'border-sky-500/40 text-sky-400', icon: Info },
  error: { className: 'border-error/40 text-error', icon: XCircle },
} as const

export type ToastType = keyof typeof TOAST_VARIANTS
