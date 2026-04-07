import { ToastContext, type ToastContextValue } from "@/contexts/ToastContext"
import { useContext } from "react"

export function useToast(): ToastContextValue {
  const ctx = useContext(ToastContext)
  if (!ctx) throw new Error('useToast doit être utilisé à l\'intérieur de ToastProvider')
  return ctx
}