import { useState } from 'react'
import axios from 'axios'
import { reportEvent } from '@/services/reportApi'
import { useToast } from '@/hooks/useToast'

export type ReportReason = 'Spam' | 'Contenu inapproprié' | 'Faux événement' | 'Autre'

export const REPORT_REASONS: readonly ReportReason[] = [
  'Spam',
  'Contenu inapproprié',
  'Faux événement',
  'Autre',
] as const

export interface UseReportReturn {
  isOpen: boolean
  submitting: boolean
  open: () => void
  close: () => void
  submit: (reason: ReportReason, description?: string) => Promise<void>
}

export function useReport(eventId: number): UseReportReturn {
  const [isOpen, setIsOpen] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const toast = useToast()

  function open() { setIsOpen(true) }
  function close() { setIsOpen(false) }

  async function submit(reason: ReportReason, description?: string): Promise<void> {
    setSubmitting(true)
    try {
      const fullReason = description?.trim()
        ? `${reason}\n\n${description.trim()}`
        : reason
      await reportEvent(eventId, { reason: fullReason })
      toast.showToast('success', 'Merci pour votre signalement.')
      setIsOpen(false)
    } catch (err: unknown) {
      if (axios.isAxiosError(err) && err.response?.status === 409) {
        toast.showToast('error', 'Vous avez déjà signalé cet événement.')
      } else {
        toast.showToast('error', 'Impossible d\'envoyer le signalement.')
      }
    } finally {
      setSubmitting(false)
    }
  }

  return { isOpen, submitting, open, close, submit }
}
