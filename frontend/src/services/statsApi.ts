import api from './api'
import type { Attendance } from '@/types/attendance'

export interface EventStats {
  viewCount: number
  attendingCount: number
  interestedCount: number
}

export async function getEventStats(eventId: number): Promise<EventStats> {
  const response = await api.get<EventStats>(`/events/${eventId}/stats`)
  return response.data
}

export async function recordEventView(eventId: number): Promise<void> {
  await api.post(`/events/${eventId}/view`)
}

/**
 * Fetches every attendance row for an event, paging through the backend
 * until exhaustion. The endpoint paginates with default `size=20` and a
 * server-side max of 100, so a single naive call would silently truncate
 * the organizer-facing list. We use the largest allowed page size and
 * stop as soon as a batch comes back smaller than that page size.
 */
export async function getEventAttendees(eventId: number): Promise<Attendance[]> {
  const PAGE_SIZE = 100
  const all: Attendance[] = []
  let page = 0
  let lastBatchSize = PAGE_SIZE
  while (lastBatchSize === PAGE_SIZE) {
    const response = await api.get<Attendance[]>(`/events/${eventId}/attendees`, {
      params: { page, size: PAGE_SIZE },
    })
    all.push(...response.data)
    lastBatchSize = response.data.length
    page += 1
  }
  return all
}
