import api from './api'
import type { Attendance, AttendanceStatus } from '@/types/attendance'

export async function attend(eventId: number, status: AttendanceStatus): Promise<Attendance> {
  const response = await api.post<Attendance>(`/events/${eventId}/attend`, { status })
  return response.data
}

export async function unattend(eventId: number): Promise<void> {
  await api.delete(`/events/${eventId}/attend`)
}
