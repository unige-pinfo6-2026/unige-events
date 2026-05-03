import api from './api'

export interface ReportEventRequest {
  reason: string
}

export async function reportEvent(eventId: number, body: ReportEventRequest): Promise<void> {
  await api.post(`/events/${eventId}/report`, body)
}
