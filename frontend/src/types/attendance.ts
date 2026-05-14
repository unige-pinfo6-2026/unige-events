export type AttendanceStatus = 'ATTENDING' | 'WAITLISTED'

export interface Attendance {
  id: number
  userId: string
  eventId: number
  status: AttendanceStatus
  createdAt: string
  /**
   * Nom d'affichage du participant, projeté depuis le User côté backend.
   * Toujours peuplé pour un user existant. `null` uniquement sur ligne orpheline
   * (user supprimé sans cascade FK). Les routes qui retournent un Attendance
   * sont restreintes (organisateur sur la liste d'event, ou inscriptions du
   * caller seul) — exposer le nom est donc sûr y compris pour profilePublic=false.
   */
  displayName: string | null
  /** URL d'avatar du participant, ou null. */
  avatarUrl: string | null
  /**
   * Username public-facing du participant (SCRUM-169). Permet à `AttendeeCard`
   * de construire `/profile/{username}` sans N+1. `null` uniquement sur ligne
   * orpheline (user supprimé).
   */
  username: string | null
}

export interface AttendanceRequest {
  status: AttendanceStatus
}
