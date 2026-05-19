import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/services/api', () => ({
  default: {
    get: vi.fn(),
    patch: vi.fn(),
  },
}))

import api from '@/services/api'
import {
  getNotifications,
  markAllRead,
  markNotificationRead,
  NOTIFICATIONS_PAGE_SIZE,
} from '@/services/notificationApi'
import type { Notification } from '@/types/notification'

const mockApiGet = vi.mocked(api.get)
const mockApiPatch = vi.mocked(api.patch)

const sampleNotif: Notification = {
  id: 42,
  userId: 'user-1',
  type: 'EVENT_UPDATED',
  message: 'Test message',
  read: false,
  createdAt: '2026-05-18T10:00:00Z',
  eventId: 7,
  relatedUserId: null,
  readAt: null,
}

beforeEach(() => {
  mockApiGet.mockReset()
  mockApiPatch.mockReset()
})

afterEach(() => vi.clearAllMocks())

describe('notificationApi.getNotifications', () => {
  it('hits the canonical /users/me/notifications path with no params by default', async () => {
    mockApiGet.mockResolvedValue({ data: [sampleNotif], headers: { 'x-unread-count': '3' } } as Awaited<ReturnType<typeof api.get>>)

    const result = await getNotifications()

    expect(mockApiGet).toHaveBeenCalledWith('/users/me/notifications', { params: {} })
    expect(result.notifications).toEqual([sampleNotif])
    expect(result.unreadCount).toBe(3)
  })

  it('forwards page and size query params when provided', async () => {
    mockApiGet.mockResolvedValue({ data: [], headers: { 'x-unread-count': '0' } } as Awaited<ReturnType<typeof api.get>>)

    await getNotifications({ page: 2, size: NOTIFICATIONS_PAGE_SIZE })

    expect(mockApiGet).toHaveBeenCalledWith('/users/me/notifications', { params: { page: 2, size: NOTIFICATIONS_PAGE_SIZE } })
  })

  it('forwards only page when size is omitted', async () => {
    mockApiGet.mockResolvedValue({ data: [], headers: { 'x-unread-count': '0' } } as Awaited<ReturnType<typeof api.get>>)

    await getNotifications({ page: 1 })

    expect(mockApiGet).toHaveBeenCalledWith('/users/me/notifications', { params: { page: 1 } })
  })

  it('falls back to 0 when the X-Unread-Count header is missing', async () => {
    mockApiGet.mockResolvedValue({ data: [sampleNotif], headers: {} } as Awaited<ReturnType<typeof api.get>>)

    const result = await getNotifications()

    expect(result.unreadCount).toBe(0)
  })

  it('falls back to 0 when the X-Unread-Count header is not a number', async () => {
    mockApiGet.mockResolvedValue({ data: [], headers: { 'x-unread-count': 'banana' } } as Awaited<ReturnType<typeof api.get>>)

    const result = await getNotifications()

    expect(result.unreadCount).toBe(0)
  })
})

describe('notificationApi.markAllRead', () => {
  it('PATCHes the bulk endpoint and returns the updated count', async () => {
    mockApiPatch.mockResolvedValue({ data: { updated: 5 } } as Awaited<ReturnType<typeof api.patch>>)

    const result = await markAllRead()

    expect(mockApiPatch).toHaveBeenCalledWith('/users/me/notifications/read-all')
    expect(result).toEqual({ updated: 5 })
  })
})

describe('notificationApi.markNotificationRead', () => {
  it('PATCHes the per-item endpoint with the numeric id', async () => {
    mockApiPatch.mockResolvedValue({} as Awaited<ReturnType<typeof api.patch>>)

    await markNotificationRead(42)

    expect(mockApiPatch).toHaveBeenCalledWith('/users/me/notifications/42/read')
  })
})
