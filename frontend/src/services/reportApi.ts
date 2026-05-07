import api from './api'
import type { ReportReason } from '@/types/admin'

export interface ReportEventRequest {
  reason: ReportReason
  description?: string | null
}

export async function reportEvent(eventId: number, body: ReportEventRequest): Promise<void> {
  await api.post(`/events/${eventId}/report`, body)
}
