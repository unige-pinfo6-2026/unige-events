
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/services/api', () => ({
  default: {
    get: vi.fn(),
    put: vi.fn(),
  },
}))

import api from '@/services/api'
import { getNotifications, markAllRead } from '@/services/notificationApi'
import type { Notification } from '@/types/notification'

const mockApiGet = vi.mocked(api.get)
const mockApiPut = vi.mocked(api.put)

const sampleNotification: Notification = {
  id: 'n1',
  userId: 'u1',
  eventId: 'e1',
  type: 'EVENT_UPDATED',
  message: "L'événement a été mis à jour.",
  read: false,
  createdAt: '2026-05-01T10:00:00',
}

beforeEach(() => {
  mockApiGet.mockReset()
  mockApiPut.mockReset()
})

afterEach(() => vi.clearAllMocks())

describe('notificationApi', () => {
  it('fetches notifications from /notifications', async () => {
    mockApiGet.mockResolvedValue({ data: [sampleNotification] } as Awaited<ReturnType<typeof api.get>>)

    const result = await getNotifications()

    expect(mockApiGet).toHaveBeenCalledWith('/notifications')
    expect(result).toEqual([sampleNotification])
  })

  it('returns empty array when API returns no notifications', async () => {
    mockApiGet.mockResolvedValue({ data: [] } as Awaited<ReturnType<typeof api.get>>)

    const result = await getNotifications()

    expect(result).toEqual([])
  })

  it('marks all notifications as read via PUT /notifications/read-all', async () => {
    mockApiPut.mockResolvedValue({} as Awaited<ReturnType<typeof api.put>>)

    await markAllRead()

    expect(mockApiPut).toHaveBeenCalledWith('/notifications/read-all')
  })

  it('propagates errors from getNotifications', async () => {
    mockApiGet.mockRejectedValue(new Error('Network error'))

    await expect(getNotifications()).rejects.toThrow('Network error')
  })

  it('propagates errors from markAllRead', async () => {
    mockApiPut.mockRejectedValue(new Error('Server error'))

    await expect(markAllRead()).rejects.toThrow('Server error')
  })
})
