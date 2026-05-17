import api from './api'
import type { Attendance } from '@/types/attendance'

export interface GetEventAttendeesParams {
  page: number
  size: number
}

/**
 * Liste paginée des participants à un événement. Le backend applique le filtre
 * de confidentialité (SCRUM-S7) côté DTO : les lignes correspondant à un profil
 * privé sont renvoyées avec `displayName=null`, `avatarUrl=null` et
 * `userId=null` pour un appelant non-organisateur. Les créateurs, co-organisateurs
 * ACCEPTED et admins reçoivent l'identité réelle pour toutes les lignes. Pas
 * d'appel secondaire à `/users/{id}` — un seul round-trip suffit.
 */
export async function getEventAttendees(
  eventId: number,
  params: GetEventAttendeesParams,
): Promise<Attendance[]> {
  const response = await api.get<Attendance[]>(`/events/${eventId}/attendees`, { params })
  return response.data
}
