export type AttendanceStatus = 'ATTENDING' | 'WAITLISTED'

export interface Attendance {
  id: number
  /**
   * UUID du participant. `null` sur `GET /events/{id}/attendees` quand la ligne
   * est anonymisée pour un appelant non-organisateur (profil privé). Non-nul
   * sur toutes les autres routes (`/users/me/attendances`, etc.).
   */
  userId: string | null
  eventId: number
  status: AttendanceStatus
  createdAt: string
  /**
   * Nom d'affichage du participant, projeté depuis le User côté backend.
   * Peuplé pour un user public ou pour la vue organisateur (créateur,
   * co-organisateur ACCEPTED, admin). `null` quand la ligne est anonymisée par
   * le filtre de confidentialité côté backend (profil privé vu par un
   * appelant non-organisateur), ou pour les inscriptions orphelines (user
   * supprimé sans cascade FK).
   */
  displayName: string | null
  /**
   * URL d'avatar du participant. `null` quand anonymisée par le filtre de
   * confidentialité ou pour les inscriptions orphelines.
   */
  avatarUrl: string | null
}

export interface AttendanceRequest {
  status: AttendanceStatus
}
