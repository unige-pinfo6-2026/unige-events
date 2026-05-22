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

export type ParticipationTimeframe = 'upcoming' | 'past'

/**
 * Returns the events the authenticated user is registered to, optionally filtered
 * by attendance status and timeframe. Calls GET /users/me/participations which
 * returns enriched Event payloads (counts, availableSpots) — same projection
 * used by /users/me/favorites — so the frontend can render EventCards without
 * N+1 calls to /events/{id}.
 *
 * `timeframe=upcoming` matches events whose endDate >= now (events in progress
 * are upcoming). `timeframe=past` matches events whose endDate < now.
 */
export async function getMyParticipations(
  status?: AttendanceStatus,
  timeframe?: ParticipationTimeframe,
): Promise<Event[]> {
  const params: Record<string, string> = {}
  if (status) params.status = status
  if (timeframe) params.timeframe = timeframe
  const response = await api.get<Event[]>('/users/me/participations', {
    params: Object.keys(params).length === 0 ? undefined : params,
  })
  return response.data
}

/**
 * Returns the PUBLISHED events a given user is registered to (ATTENDING),
 * enriched like the other lists (counts, availableSpots). Calls
 * GET /users/{id}/participations — the backend gates visibility server-side
 * (self → always ; public target → list ; private non-owner target → empty
 * list) and only ever exposes ATTENDING inscriptions to PUBLISHED events.
 * Used by the "Participations publiques" section of the public profile.
 */
export async function getUserParticipations(
  userId: string,
  timeframe?: ParticipationTimeframe,
): Promise<Event[]> {
  const params = timeframe ? { timeframe } : undefined
  const response = await api.get<Event[]>(`/users/${userId}/participations`, { params })
  return response.data
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
