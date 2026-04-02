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

export type StudyLevel = {
  id: string
  name: string
}

export const STUDY_LEVELS = [
  { id: 'BACHELOR', name: 'Bachelor' },
  { id: 'MASTER', name: 'Master' },
  { id: 'DOCTORAT', name: 'Doctorat' },
  { id: 'POST_DOC', name: 'Post-doctorat' },
  { id: 'STAFF', name: 'Staff' },
] as const satisfies StudyLevel[]