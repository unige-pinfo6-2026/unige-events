import { AxiosError } from 'axios'

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

export async function getUserByUsername(username: string): Promise<UserPublicResponse> {
  const response = await api.get<UserPublicResponse>(
    `/users/by-username/${encodeURIComponent(username)}`,
  )
  return response.data
}

export async function updateUsername(username: string): Promise<User> {
  const response = await api.patch<User>('/users/me/username', { username })
  return response.data
}

/**
 * HEAD /users/by-username/{username} → 200 = pris, 404 = libre.
 * Used by the live debounced check in ProfileEditPage.
 */
export async function checkUsernameAvailable(username: string): Promise<boolean> {
  try {
    await api.head(`/users/by-username/${encodeURIComponent(username)}`)
    return false
  } catch (error) {
    if (error instanceof AxiosError && error.response?.status === 404) {
      return true
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
