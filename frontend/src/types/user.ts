export interface UserPublicResponse {
  id: string
  /**
   * Public-facing handle — toujours présent (SCRUM-169). Exposé même aux
   * appelants anonymes car c'est l'identifiant utilisé dans l'URL
   * `/profile/{username}`.
   */
  username: string
  displayName?: string | null
  faculty?: string | null
  studyLevel?: string | null
  bio?: string | null
  interests?: string[]
  avatarUrl?: string | null
  bannerUrl?: string | null
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
  /**
   * Public-facing handle — required after SCRUM-169 back-fill. Stocké
   * lowercase, doit matcher `^[a-z0-9._-]{3,30}$`. Modifiable via
   * `PATCH /users/me/username`.
   */
  username: string
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

/**
 * Usernames réservés — miroir de `UsernameGenerator.RESERVED` côté backend
 * (SCRUM-169). Refusés à l'écriture dans `PATCH /users/me/username` (code
 * `username_reserved` 400) et évités à l'auto-gen. Le frontend en a besoin
 * pour le live-check côté `ProfileEditPage` afin de retourner immédiatement
 * un statut `reserved` sans round-trip.
 */
export const RESERVED_USERNAMES = new Set([
  'me',
  'admin',
  'api',
  'login',
  'logout',
  'signup',
  'register',
  'settings',
])

/** Pattern d'unicode/format du username (mirroir backend). */
export const USERNAME_PATTERN = /^[a-z0-9._-]{3,30}$/
export const USERNAME_MIN_LENGTH = 3
export const USERNAME_MAX_LENGTH = 30