import axios from 'axios'
import api from './api'
import type { Attendance } from '@/types/attendance'
import type { UserPublicResponse } from '@/types/user'

export interface GetEventAttendeesParams {
  page: number
  size: number
}

export async function getEventAttendees(
  eventId: number,
  params: GetEventAttendeesParams,
): Promise<Attendance[]> {
  const response = await api.get<Attendance[]>(`/events/${eventId}/attendees`, { params })
  return response.data
}

export async function getPublicUser(userId: string): Promise<UserPublicResponse | null> {
  try {
    const response = await api.get<UserPublicResponse>(`/users/${userId}`)
    return response.data
  } catch (error) {
    if (axios.isAxiosError(error)) {
      const status = error.response?.status
      if (status === 403 || status === 404) return null
    }
    throw error
  }
}
