import api from './api'
import type { Attendance } from '@/types/attendance'

export interface EventStats {
  viewCount: number
  attendingCount: number
  interestedCount: number
}

export async function getEventStats(eventId: number): Promise<EventStats> {
  const response = await api.get<EventStats>(`/events/${eventId}/stats`)
  return response.data
}

export async function getEventAttendees(eventId: number): Promise<Attendance[]> {
  const response = await api.get<Attendance[]>(`/events/${eventId}/attendees`)
  return response.data
}
