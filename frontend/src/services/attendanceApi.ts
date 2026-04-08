import api from './api'
import type { Attendance, AttendanceStatus } from '@/types/attendance'

export async function attend(eventId: number, status: AttendanceStatus): Promise<Attendance> {
  const response = await api.post<Attendance>(`/events/${eventId}/attend`, { status })
  return response.data
}

export async function unattend(eventId: number): Promise<void> {
  await api.delete(`/events/${eventId}/attend`)
}

/**
 * Returns the current user's attendance status for a given event, or null if
 * not attending / not authenticated. Uses GET /users/me/attendances (returns
 * all attendances) and filters client-side — no per-event endpoint exists yet.
 */
export async function getMyAttendance(eventId: number): Promise<AttendanceStatus | null> {
  const response = await api.get<Attendance[]>('/users/me/attendances')
  const match = response.data.find((a) => a.eventId === eventId)
  return match?.status ?? null
}
