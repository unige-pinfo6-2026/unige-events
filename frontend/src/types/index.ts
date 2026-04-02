export interface User {
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

export const StudyLevel = {
  BACHELOR: 'BACHELOR',
  MASTER: 'MASTER',
  DOCTORAT: 'DOCTORAT',
  POST_DOC: 'POST_DOC',
  STAFF: 'STAFF',
} as const

export type StudyLevel = (typeof StudyLevel)[keyof typeof StudyLevel]

export const EventCategory = {
  ACADEMIC: 'ACADEMIC',
  SPORTS: 'SPORTS',
  CULTURAL: 'CULTURAL',
  SOCIAL: 'SOCIAL',
  CONFERENCE: 'CONFERENCE',
  OTHER: 'OTHER',
} as const

export type EventCategory = (typeof EventCategory)[keyof typeof EventCategory]

export const EventStatus = {
  DRAFT: 'DRAFT',
  PUBLISHED: 'PUBLISHED',
  CANCELLED: 'CANCELLED',
} as const

export type EventStatus = (typeof EventStatus)[keyof typeof EventStatus]

export interface Event {
  id: number
  title: string
  description?: string
  location: string
  startDate: string
  endDate: string
  category: EventCategory
  bannerUrl?: string
  creatorId: string
  status: EventStatus
  capacity?: number
  createdAt: string
  updatedAt?: string
}

export interface CreateEventRequest {
  title: string
  description?: string
  location: string
  startDate: string
  endDate: string
  category: EventCategory
  bannerUrl?: string
  capacity?: number
  status?: EventStatus
}

export interface UpdateEventRequest {
  title: string
  description?: string
  location: string
  startDate: string
  endDate: string
  category: EventCategory
  bannerUrl?: string
  capacity?: number
  status?: EventStatus
}
