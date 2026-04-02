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