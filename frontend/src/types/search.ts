import type { EventCategory } from "./event"
import type { Faculty } from "./faculty"

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
