import api from './api'
import type { CreateEventRequest, Event, EventCategory, EventStatus, UpdateEventRequest } from '../types'

export interface EventsParams {
  page?: number
  size?: number
  status?: EventStatus
  category?: EventCategory
  organizerId?: string
  endDateFrom?: string
}

export async function getAll(params: EventsParams = {}): Promise<Event[]> {
  const response = await api.get<Event[]>('/events', { params })
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
