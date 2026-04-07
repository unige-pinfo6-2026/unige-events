import type { Event, EventCategory } from "./event"
import type { Faculty } from "./faculty"

export interface SearchFilters {
  category?: EventCategory
  faculty?: Faculty
  dateFrom?: string
  dateTo?: string
  includePast: boolean
}

export interface SearchParams {
  q?: string
  category?: EventCategory
  faculty?: Faculty
  dateFrom?: string
  dateTo?: string
  page?: number
  size?: number
}

export type SearchResponse = Event[]
