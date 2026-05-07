import api from './api'
import type { ReportReason } from '@/types/report'

export interface ReportEventRequest {
  reason: ReportReason
  description?: string
}

export async function reportEvent(eventId: number, body: ReportEventRequest): Promise<void> {
  await api.post(`/events/${eventId}/report`, body)
}
