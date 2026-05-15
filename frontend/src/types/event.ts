import type { Faculty } from "./faculty"

export type Event = {
  id: number
  title: string
  description?: string
  location: string
  startDate: string
  endDate: string
  category: EventCategory
  faculty?: Faculty | null
  bannerUrl?: string
  creatorId: string
  status: EventStatus
  capacity?: number
  availableSpots?: number | null
  waitlistedCount?: number
  viewCount?: number | null
  interestedCount?: number | null
  allDay: boolean
  attendingCount: number
  featured?: boolean
  featuredAt?: string | null
  websiteUrl?: string | null
  contactEmail?: string | null
  registrationDeadline?: string | null
  tags?: string[]
  createdAt: string
  updatedAt?: string
  parentEventId?: number | null
  recurrenceRule?: string | null
  /**
   * SCRUM-136 — set to `true` when the request carried `?check-co-org-of=<callerId>`
   * and the caller is an ACCEPTED co-organizer of this event. `false` when the
   * caller is authenticated but not a co-organizer, `null`/absent when the
   * param wasn't sent (or was rejected by the membership-oracle guard). The
   * frontend uses this to widen "isOrganizer" beyond `creatorId` so co-organizers
   * see the edit/cancel/stats actions.
   */
  coOrganizerOf?: boolean | null
}

export const EVENT_WEBSITE_URL_MAX_LENGTH = 500
export const EVENT_CONTACT_EMAIL_MAX_LENGTH = 255
export const EVENT_TAG_MAX_LENGTH = 16
export const EVENT_TAGS_MAX_ITEMS = 20

export const EVENT_CATEGORIES = {
  ACADEMIC: { name: 'Académique', color: '#2563eb' },
  SPORTS: { name: 'Sports', color: '#16a34a' },
  CULTURAL: { name: 'Culturel', color: '#9333ea' },
  SOCIAL: { name: 'Social', color: '#ea580c' },
  CONFERENCE: { name: 'Conférence', color: '#0891b2' },
  OTHER: { name: 'Autre', color: '#6b7280' },
} as const

export type EventCategory = keyof typeof EVENT_CATEGORIES

export const EVENT_STATUSES = {
  DRAFT: { name: 'Brouillon' },
  PUBLISHED: { name: 'Publié' },
  CANCELLED: { name: 'Annulé' },
  EXPIRED: { name: 'Terminé' },
  BANNED: { name: 'Banni' },
} as const

export type EventStatus = keyof typeof EVENT_STATUSES

export const RECURRENCE_FREQUENCIES = {
  WEEKLY: { name: 'Chaque semaine' },
  BIWEEKLY: { name: 'Toutes les 2 semaines' },
  MONTHLY: { name: 'Chaque mois' },
} as const

export type RecurrenceFrequency = keyof typeof RECURRENCE_FREQUENCIES

export interface RecurrenceRequest {
  frequency: RecurrenceFrequency
  endDate?: string | null
  maxOccurrences?: number | null
}

export const RECURRENCE_MAX_OCCURRENCES = 52

export interface CreateEventRequest {
  title: string
  description?: string
  location: string
  startDate: string
  endDate: string
  category: EventCategory
  faculty?: Faculty | null
  bannerUrl?: string
  capacity?: number
  status?: EventStatus
  allDay?: boolean
  websiteUrl?: string | null
  contactEmail?: string | null
  registrationDeadline?: string | null
  tags?: string[] | null
  recurrence?: RecurrenceRequest | null
}

export interface UpdateEventRequest {
  title: string
  description?: string
  location: string
  startDate: string
  endDate: string
  category: EventCategory
  faculty?: Faculty | null
  bannerUrl?: string
  capacity?: number
  status?: EventStatus
  allDay?: boolean
  websiteUrl?: string | null
  contactEmail?: string | null
  registrationDeadline?: string | null
  tags?: string[] | null
}