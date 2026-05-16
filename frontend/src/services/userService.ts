import axios, { AxiosError } from 'axios'
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

/**
 * Lookup d'un profil par son `username` public-facing (SCRUM-169).
 * Case-insensitive côté backend. Retourne `null` si le username est
 * introuvable OU si la cible est un profil privé non-owner non-admin
 * (anti-oracle 404 indistinguable, cf. spec § 3 décision F).
 *
 * Typed as `User | null` for backward-compat with the SCRUM-169 callers
 * (CoOrganizersEditor flow). The runtime response actually carries the
 * SCRUM-138 follow fields too (`followerCount`, `followingCount`,
 * `followStatus`); ProfilePage reads them off and casts to
 * `UserPublicResponse` at the boundary.
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
 * SCRUM-137 polish — username autocomplete. Calls
 * `GET /users/search?q=<prefix>&limit=<n>` and returns the lightweight
 * projection (id + username + displayName + avatarUrl + profilePublic) used
 * by `UsernameAutocomplete` to render a dropdown of suggestions on the
 * co-organizer invitation field.
 *
 * The backend caps `limit` at 20 and validates `q` server-side (2-30 chars,
 * charset `[a-zA-Z0-9._-]`). This wrapper does not duplicate the validation —
 * the caller is the autocomplete component which trims to `>= 2` before
 * firing.
 */
export async function searchUsernames(q: string, limit?: number): Promise<UserPublicResponse[]> {
  const params: { q: string; limit?: number } = { q }
  if (limit !== undefined) params.limit = limit
  const response = await api.get<UserPublicResponse[]>('/users/search', { params })
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
