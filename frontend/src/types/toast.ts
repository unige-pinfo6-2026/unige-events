export const TOAST_VARIANTS = {
  success: 'border-emerald-500/40 text-emerald-400',
  error: 'border-error/40 text-error',
} as const

export type ToastType = keyof typeof TOAST_VARIANTS