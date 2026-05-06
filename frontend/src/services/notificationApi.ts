import api from './api'
import type { Notification } from '@/types/notification'

export async function getNotifications(): Promise<Notification[]> {
  const { data } = await api.get<Notification[]>('/notifications')
  return data
}

export async function markAllRead(): Promise<void> {
  await api.put('/notifications/read-all')
}
