import axios from 'axios'
import api from './api'
import type { Attendance, AttendanceStatus } from '@/types/attendance'
import type { Event } from '@/types/event'

export async function attend(eventId: number, status: 'ATTENDING'): Promise<Attendance> {
  const response = await api.post<Attendance>(`/events/${eventId}/attend`, { status })
  return response.data
}

export async function unattend(eventId: number): Promise<void> {
  await api.delete(`/events/${eventId}/attend`)
}

/**
 * Returns the current user's attendance status for a given event, or null when:
 * - the user has no attendance record for the event, or
 * - the user is not authenticated (401 response).
 *
 * Uses GET /users/me/attendances (returns all attendances) and filters
 * client-side — no per-event endpoint exists yet.
 *
 * For all non-401 errors the error is rethrown.
 */
/**
 * Stub — returns an empty list until the backend delivers a dedicated endpoint
 * to fetch the current user's attended events (with full Event payloads, not
 * just the Attendance rows returned by /users/me/attendances).
 *
 * TODO: replace with GET /api/users/me/attendances when backend delivers
 * (enriched with event data so /my-events "Mes Participations" tab can render
 * EventCards without N+1 calls to /events/{id}).
 */
export async function getMyParticipations(): Promise<Event[]> {
  return []
}

export async function getMyAttendance(eventId: number): Promise<AttendanceStatus | null> {
  try {
    const response = await api.get<Attendance[]>('/users/me/attendances')
    const match = response.data.find((a) => a.eventId === eventId)
    return match?.status ?? null
  } catch (error) {
    if (axios.isAxiosError(error) && error.response?.status === 401) {
      return null
    }
    throw error
  }
}
