import { useState } from 'react'
import axios from 'axios'
import { reportEvent } from '@/services/reportApi'
import type { ReportEventRequest } from '@/services/reportApi'
import type { ReportReason } from '@/types/report'
import { useToast } from '@/hooks/useToast'

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
      const trimmed = description?.trim()
      const body: ReportEventRequest = trimmed ? { reason, description: trimmed } : { reason }
      await reportEvent(eventId, body)
      toast.showToast('success', 'Merci pour votre signalement.')
      setIsOpen(false)
    } catch (err: unknown) {
      if (axios.isAxiosError(err) && err.response?.status === 409) {
        toast.showToast('error', 'Vous avez déjà signalé cet événement.')
      } else if (axios.isAxiosError(err) && err.response?.status === 422) {
        toast.showToast('error', 'Vous ne pouvez pas signaler un événement que vous organisez.')
      } else {
        toast.showToast('error', 'Impossible d\'envoyer le signalement.')
      }
    } finally {
      setSubmitting(false)
    }
  }

  return { isOpen, submitting, open, close, submit }
}
