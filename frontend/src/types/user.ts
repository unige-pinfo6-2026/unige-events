/**
 * État de la relation de suivi entre l'appelant authentifié et l'utilisateur
 * cible (cf. SCRUM-138). `null` quand l'appelant est anonyme, sur son propre
 * profil, ou qu'aucune row `Follow` n'existe pour le couple
 * `(callerId, targetId)`.
 */
export type FollowStatus = 'PENDING' | 'ACCEPTED'

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
  /**
   * URL de la bannière. Présente dans la projection complète ET la projection
   * verrouillée (carte « compte privé ») ; `null` si l'utilisateur n'en a pas.
   */
  bannerUrl?: string | null
  /**
   * Flag de visibilité du profil cible (cf. SCRUM-169 Décision E). Toujours
   * présent dans `UserPublicResponse`. Vaut `false` pour la projection
   * restreinte (caller non-owner non-admin sur un profil privé) — le frontend
   * l'utilise pour basculer sur le placeholder « Compte privé ».
   */
  profilePublic: boolean
  /** Nombre de followers ACCEPTED — toujours présent, réel y compris pour un appelant anonyme ou une vue verrouillée. */
  followerCount: number
  /** Nombre d'abonnements ACCEPTED — toujours présent, réel y compris pour un appelant anonyme ou une vue verrouillée. */
  followingCount: number
  /** État de la relation caller → cible. `null` si aucune row Follow. */
  followStatus?: FollowStatus | null
  /**
   * Rôles Auth0 du **profil affiché** (pas du viewer), mirrorés en base par
   * le backend depuis le claim JWT du propriétaire à chaque hit `/me`.
   * Toujours présent côté API (liste vide par défaut) — optionnel ici pour
   * tolérer les mocks/payloads pré-existants. Utilisé pour driver les
   * éléments d'UI dépendants du rôle de la cible (badge "Staff" sur le profil
   * de tout admin, par ex.). Pour les permissions *du viewer*, lire la claim
   * Auth0 via `useAuth().isAdmin` à la place.
   */
  roles?: string[]
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
  /**
   * Rôles Auth0 du user authentifié, mirrorés par le backend à chaque
   * `/users/me`. Toujours présent côté API (liste vide par défaut) —
   * optionnel ici pour tolérer les mocks existants. Pour driver l'UI
   * dépendante du rôle d'un autre user, lire le champ `roles` sur
   * `UserPublicResponse` à la place.
   */
  roles?: string[]
}

/** Rôle Auth0 qui déclenche le badge "Staff" sur le profil du propriétaire. */
export const STAFF_ROLE = 'ADMIN'

/** True si le profil possède au moins un rôle visible comme "staff". */
export function isStaff(roles: string[] | undefined | null): boolean {
  return Array.isArray(roles) && roles.includes(STAFF_ROLE)
}

export const STUDY_LEVELS = {
  BACHELOR: { name: 'Bachelor' },
  MASTER: { name: 'Master' },
  DOCTORAT: { name: 'Doctorat' },
  POST_DOC: { name: 'Post-doctorat' },
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