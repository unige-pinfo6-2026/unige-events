import { AxiosError } from 'axios'
import api from './api'
import type { User } from '@/types/user'
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
 * Lookup d'un profil par son `username` public-facing (SCRUM-169).
 * Case-insensitive côté backend. Retourne `null` si le username est
 * introuvable OU si la cible est un profil privé non-owner non-admin
 * (anti-oracle 404 indistinguable, cf. spec § 3 décision F).
 */
export async function getUserByUsername(username: string): Promise<User | null> {
  try {
    const response = await api.get<User>(`/users/by-username/${encodeURIComponent(username)}`)
    return response.data
  } catch (error) {
    if (error instanceof AxiosError && error.response?.status === 404) {
      return null
    }
    throw error
  }
}

export async function updateProfile(data: Partial<User>): Promise<User> {
  const response = await api.put<User>('/users/me', data)
  return response.data
}

/**
 * Met à jour le username de l'utilisateur courant (SCRUM-169).
 *
 * Endpoint dédié séparé de `updateProfile` pour granularité d'erreur :
 * `409 username_taken`, `400 username_invalid`, `400 username_reserved`.
 * Le caller (`ProfileEditPage`) doit gérer ces codes spécifiquement
 * sans bloquer le reste du submit (cf. spec § 6 cas-limite "race
 * debounce/submit").
 */
export async function updateUsername(username: string): Promise<User> {
  const response = await api.patch<User>('/users/me/username', { username })
  return response.data
}

/**
 * Vérifie si le `username` est disponible (SCRUM-169). Wrapper du
 * `HEAD /users/by-username/{u}` à la sémantique **inversée** : retourne
 * `true` (disponible) sur 404, `false` (pris) sur 200. Le frontend
 * absorbe l'inversion pour exposer un boolean naturel à
 * `ProfileEditPage`.
 *
 * Une erreur réseau ou un 5xx propage l'exception — le caller affiche
 * un état "erreur de vérification" plutôt qu'un faux "disponible".
 */
export async function checkUsernameAvailable(username: string): Promise<boolean> {
  try {
    await api.head(`/users/by-username/${encodeURIComponent(username)}`)
    return false // 200 = pris
  } catch (error) {
    if (error instanceof AxiosError && error.response?.status === 404) {
      return true // 404 = libre
    }
    throw error
  }
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
