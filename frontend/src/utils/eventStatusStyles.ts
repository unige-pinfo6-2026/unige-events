import type { EventStatus } from '@/types/event'

export const EVENT_STATUS_VARIANTS: Record<EventStatus, string> = {
  PUBLISHED: 'bg-emerald-500/20 text-emerald-400 border-emerald-500/40',
  DRAFT:     'bg-amber-500/20 text-amber-400 border-amber-500/40',
  CANCELLED: 'bg-red-500/20 text-red-400 border-red-500/40',
}

export const EVENT_STATUS_PARAMS: Record<EventStatus, 'published' | 'draft' | 'cancelled'> = {
  PUBLISHED: 'published',
  DRAFT:     'draft',
  CANCELLED: 'cancelled',
}
