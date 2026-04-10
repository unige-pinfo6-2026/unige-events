import api from '@/services/api'
import type { Event } from '@/types/event'

export async function getFavorites(): Promise<Event[]> {
  const { data } = await api.get<Event[]>('/users/me/favorites')
  return data
}

export async function addFavorite(eventId: number): Promise<void> {
  await api.post(`/events/${eventId}/favorite`)
}

export async function removeFavorite(eventId: number): Promise<void> {
  await api.delete(`/events/${eventId}/favorite`)
}
