import type { Faculty } from '@/types/faculty'
import api from './api'
import type { CreateEventRequest, Event, EventCategory, EventStatus, UpdateEventRequest } from '@/types/event'

export interface EventsParams {
  page?: number
  size?: number
  status?: EventStatus
  category?: EventCategory
  organizerId?: string
  endDateFrom?: string
  faculty?: Faculty
  facultyNone?: boolean
}

export async function getAll(params: EventsParams = {}): Promise<Event[]> {
  const response = await api.get<Event[]>('/events', { params })
  return response.data
}

export interface MyEventsParams {
  status?: EventStatus
  page?: number
  size?: number
}

export async function getMyEvents(params: MyEventsParams = {}): Promise<Event[]> {
  const response = await api.get<Event[]>('/users/me/events', { params })
  return response.data
}

export async function getById(id: number): Promise<Event> {
  const response = await api.get<Event>('/events/' + id)
  return response.data
}

export async function createEvent(data: CreateEventRequest): Promise<Event> {
  const response = await api.post<Event>('/events', data)
  return response.data
}

export async function updateEvent(id: number, data: UpdateEventRequest): Promise<Event> {
  const response = await api.put<Event>('/events/' + id, data)
  return response.data
}

export async function uploadEventImage(id: number, file: File): Promise<Event> {
  const formData = new FormData()
  formData.append('file', file)

  const response = await api.post<Event>('/events/' + id + '/image', formData)
  return response.data
}

export async function deleteEvent(id: number): Promise<void> {
  await api.delete('/events/' + id)
}

const MAX_DRAFTS_FETCH = 5

export async function getMyDrafts(
  organizerId: string,
  limit: number = MAX_DRAFTS_FETCH,
): Promise<Event[]> {
  return getAll({
    organizerId,
    status: 'DRAFT',
    size: limit,
  })
}

export async function cancelEvent(id: number): Promise<Event> {
  const response = await api.patch<Event>('/events/' + id + '/cancel')
  return response.data
}

export async function restoreEvent(id: number): Promise<Event> {
  const response = await api.patch<Event>('/events/' + id + '/restore')
  return response.data
}

export async function publishEvent(id: number): Promise<Event> {
  const response = await api.patch<Event>('/events/' + id + '/publish')
  return response.data
}
