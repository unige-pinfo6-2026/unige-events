export interface UserPublicResponse {
  id: string
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
  username: string
  displayName?: string
  firstName?: string
  lastName?: string
  faculty?: string
  studyLevel?: string
  bio?: string
  interests?: string[]
  avatarUrl?: string
  bannerUrl?: string | null
  profilePublic: boolean
  admin: boolean
  createdAt: string
}

export const USERNAME_PATTERN = /^[a-z0-9._-]{3,30}$/
export const USERNAME_MIN_LENGTH = 3
export const USERNAME_MAX_LENGTH = 30
export const RESERVED_USERNAMES = [
  'me',
  'admin',
  'api',
  'login',
  'logout',
  'signup',
  'register',
  'settings',
] as const

export function isValidUsername(value: string): boolean {
  return USERNAME_PATTERN.test(value) && !(RESERVED_USERNAMES as readonly string[]).includes(value)
}

const UUID_V4 = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i
export const UUID_PATTERN = UUID_V4
export function isUuid(value: string): boolean {
  return UUID_V4.test(value)
}

export const STUDY_LEVELS = {
  BACHELOR: { name: 'Bachelor' },
  MASTER: { name: 'Master' },
  DOCTORAT: { name: 'Doctorat' },
  POST_DOC: { name: 'Post-doctorat' },
  STAFF: { name: 'Staff' },
} as const

export type StudyLevel = keyof typeof STUDY_LEVELS