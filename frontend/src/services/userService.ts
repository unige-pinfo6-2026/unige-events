import axios from 'axios'
import api from './api'
import type { User, UserPublicResponse } from '@/types/user'
import type { CalendarTokenResponse } from '@/types/calendarToken'

export async function getMe(): Promise<User> {
  const response = await api.get<User>('/users/me')
  return response.data
}

export async function getUserById(id: string): Promise<User | null> {
  const response = await api.get<User>(`/users/${id}`)
  return response.data
}

/**
 * Public profile fetch for `/profile/:id` (SCRUM-141). Returns `null` on 404,
 * which on the backend covers both "user does not exist" and "profile is
 * private and caller is neither owner nor admin" (anti-oracle ISSUE-93 —
 * the two cases are indistinguishable by design). The caller renders a
 * unified "Ce profil est privé ou introuvable" UI for `null`.
 *
 * Other HTTP errors (5xx, network) are rethrown so the hook can surface a
 * retryable error state.
 */
export async function getPublicProfile(id: string): Promise<UserPublicResponse | null> {
  try {
    const response = await api.get<UserPublicResponse>(`/users/${id}`)
    return response.data
  } catch (error) {
    if (axios.isAxiosError(error) && error.response?.status === 404) {
      return null
    }
    throw error
  }
}

export async function updateProfile(data: Partial<User>): Promise<User> {
  const response = await api.put<User>('/users/me', data)
  return response.data
}

export async function uploadPhoto(file: File): Promise<User> {
  const formData = new FormData()
  formData.append('file', file)
  const response = await api.post<User>('/users/me/image', formData)
  return response.data
}

export async function uploadBanner(file: File): Promise<User> {
  const formData = new FormData()
  formData.append('file', file)
  const response = await api.post<User>('/users/me/banner', formData)
  return response.data
}

export async function deleteBanner(): Promise<User> {
  const response = await api.delete<User>('/users/me/banner')
  return response.data
}

export async function getCalendarToken(): Promise<CalendarTokenResponse> {
  const response = await api.get<CalendarTokenResponse>('/users/me/calendar-token')
  return response.data
}

export async function regenerateCalendarToken(): Promise<CalendarTokenResponse> {
  const response = await api.post<CalendarTokenResponse>('/users/me/calendar-token/regenerate')
  return response.data
}
