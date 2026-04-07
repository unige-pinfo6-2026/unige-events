import type { ToastType } from '@/types/toast'
import { createContext, useCallback, useMemo, useRef, useState } from 'react'
import type { ReactNode } from 'react'

interface ToastEntry {
  id: number
  type: ToastType
  message: string
  duration?: number
}

export interface ToastContextValue {
  showToast: (type: ToastType, message: string, duration?: number) => void
  toasts: ToastEntry[]
  dismiss: (id: number) => void
}

// eslint-disable-next-line react-refresh/only-export-components
export const ToastContext = createContext<ToastContextValue | null>(null)

export function ToastProvider({ children }: Readonly<{ children: ReactNode }>) {
  const [toasts, setToasts] = useState<ToastEntry[]>([])
  const nextId = useRef(0)

  const dismiss = useCallback((id: number) => {
    setToasts(prev => prev.filter(t => t.id !== id))
  }, [])

  const showToast = useCallback((type: ToastType, message: string, duration = 5000) => {
    const id = nextId.current++
    setToasts(prev => [...prev, { id, type, message, duration }])
  }, [])

  const value = useMemo(
    () => ({ showToast, toasts, dismiss }),
    [showToast, toasts, dismiss]
  )

  return (
    <ToastContext.Provider value={value}>
      {children}
    </ToastContext.Provider>
  )
}
