/**
 * État de la relation de suivi entre l'appelant authentifié et l'utilisateur
 * cible (cf. SCRUM-138). `null` quand l'appelant est anonyme, sur son propre
 * profil, ou qu'aucune row `Follow` n'existe pour le couple
 * `(callerId, targetId)`.
 */
export type FollowStatus = 'PENDING' | 'ACCEPTED'

export interface UserPublicResponse {
  id: string
  displayName?: string | null
  faculty?: string | null
  studyLevel?: string | null
  bio?: string | null
  interests?: string[]
  avatarUrl?: string | null
  bannerUrl?: string | null
  /** Nombre de followers ACCEPTED (toujours présent, `0` pour anonyme). */
  followerCount: number
  /** Nombre d'utilisateurs suivis ACCEPTED (toujours présent, `0` pour anonyme). */
  followingCount: number
  /** État de la relation caller → cible. `null` si aucune row Follow. */
  followStatus?: FollowStatus | null
}

export type User = {
  id: string
  auth0Id: string
  email: string
  displayName?: string
  firstName?: string
  lastName?: string
  faculty?: string
  studyLevel?: string
  bio?: string
  interests?: string[]
  avatarUrl?: string
  bannerUrl?: string | null
  username?: string
  profilePublic: boolean
  createdAt: string
}

export const STUDY_LEVELS = {
  BACHELOR: { name: 'Bachelor' },
  MASTER: { name: 'Master' },
  DOCTORAT: { name: 'Doctorat' },
  POST_DOC: { name: 'Post-doctorat' },
  STAFF: { name: 'Staff' },
} as const

export type StudyLevel = keyof typeof STUDY_LEVELS