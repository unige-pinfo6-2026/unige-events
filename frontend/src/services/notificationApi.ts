import api from './api'
import type { Notification } from '@/types/notification'

export const NOTIFICATIONS_PAGE_SIZE = 20

export interface NotificationsPage {
  notifications: Notification[]
  unreadCount: number
}

export interface ReadAllResponse {
  updated: number
}

export async function getNotifications(
  { page, size }: { page?: number; size?: number } = {},
): Promise<NotificationsPage> {
  const params: Record<string, number> = {}
  if (page !== undefined) params.page = page
  if (size !== undefined) params.size = size

  const response = await api.get<Notification[]>('/users/me/notifications', { params })
  const headerValue = response.headers['x-unread-count']
  const unreadCount = Number(headerValue ?? 0)

  return {
    notifications: response.data,
    unreadCount: Number.isFinite(unreadCount) ? unreadCount : 0,
  }
}

export async function markAllRead(): Promise<ReadAllResponse> {
  const { data } = await api.patch<ReadAllResponse>('/users/me/notifications/read-all')
  return data
}

export async function markNotificationRead(id: number): Promise<void> {
  await api.patch(`/users/me/notifications/${id}/read`)
}
