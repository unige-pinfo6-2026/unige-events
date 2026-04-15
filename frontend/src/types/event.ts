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
  allDay: boolean
  attendingCount: number
  createdAt: string
  updatedAt?: string
}

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
} as const

export type EventStatus = keyof typeof EVENT_STATUSES

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
}