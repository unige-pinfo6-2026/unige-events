import type { Event, EventCategory } from "./event"
import type { Faculty } from "./faculty"

// `faculty` et `facultyNone` sont mutuellement exclusifs côté UI :
// `facultyNone: true` signifie « uniquement les events sans faculté rattachée »
// et désélectionne toute sélection `faculty`. Côté serveur, si les deux sont
// envoyés, `facultyNone` a priorité (règle documentée dans openapi.yaml).
export interface SearchFilters {
  category?: EventCategory
  faculty?: Faculty
  facultyNone?: boolean
  dateFrom?: string
  dateTo?: string
  includePast: boolean
}

export interface SearchParams {
  q?: string
  category?: EventCategory
  faculty?: Faculty
  facultyNone?: boolean
  dateFrom?: string
  dateTo?: string
  page?: number
  size?: number
}

export type SearchResponse = Event[]
