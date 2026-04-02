export type Event = {
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
export type EventCategory = {
  id: string
  name: string
}

export const EVENT_CATEGORIES = [
  { id: 'ACADEMIC', name: 'Académique' },
  { id: 'SPORTS', name: 'Sports' },
  { id: 'CULTURAL', name: 'Culturel' },
  { id: 'SOCIAL', name: 'Social' },
  { id: 'CONFERENCE', name: 'Conférence' },
  { id: 'OTHER', name: 'Autre' },
] as const satisfies EventCategory[]

export type EventStatus = {
  id: string
  name: string
}

export const EVENT_STATUSES = [
  { id: 'DRAFT', name: 'Brouillon' },
  { id: 'PUBLISHED', name: 'Publié' },
  { id: 'CANCELLED', name: 'Annulé' },
] as const satisfies EventStatus[]

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
