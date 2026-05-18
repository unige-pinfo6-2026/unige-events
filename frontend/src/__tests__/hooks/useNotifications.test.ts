
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { act, renderHook, waitFor } from '@testing-library/react'

vi.mock('@/services/notificationApi', () => ({
  getNotifications: vi.fn(),
  markAllRead: vi.fn(),
}))

vi.mock('axios', async () => {
  const actual = await vi.importActual<typeof import('axios')>('axios')
  return {
    ...actual,
    default: {
      ...actual.default,
      isAxiosError: vi.fn().mockReturnValue(false),
    },
  }
})

import { getNotifications, markAllRead } from '@/services/notificationApi'
import { useNotifications } from '@/hooks/useNotifications'
import type { Notification } from '@/types/notification'
import axios from 'axios'

const mockGetNotifications = vi.mocked(getNotifications)
const mockMarkAllRead = vi.mocked(markAllRead)
const mockIsAxiosError = vi.mocked(axios.isAxiosError)

const makeNotification = (id: string, read = false): Notification => ({
  id,
  userId: 'u1',
  eventId: 'e1',
  type: 'EVENT_UPDATED',
  message: `Message ${id}`,
  read,
  createdAt: '2026-05-01T10:00:00',
})

beforeEach(() => {
  mockGetNotifications.mockReset()
  mockMarkAllRead.mockReset()
  mockIsAxiosError.mockReturnValue(false)
})

afterEach(() => vi.clearAllMocks())

describe('useNotifications', () => {
  it('starts in loading state', () => {
    mockGetNotifications.mockResolvedValue([])
    const { result } = renderHook(() => useNotifications())
    expect(result.current.loading).toBe(true)
  })

  it('loads notifications on mount', async () => {
    const notifs = [makeNotification('n1'), makeNotification('n2', true)]
    mockGetNotifications.mockResolvedValue(notifs)

    const { result } = renderHook(() => useNotifications())
    await waitFor(() => expect(result.current.loading).toBe(false))

    expect(result.current.notifications).toHaveLength(2)
    expect(result.current.error).toBeNull()
  })

  it('computes unreadCount correctly', async () => {
    const notifs = [makeNotification('n1'), makeNotification('n2', true), makeNotification('n3')]
    mockGetNotifications.mockResolvedValue(notifs)

    const { result } = renderHook(() => useNotifications())
    await waitFor(() => expect(result.current.loading).toBe(false))

    expect(result.current.unreadCount).toBe(2)
  })

  it('sets error when fetch fails with non-401 error', async () => {
    mockGetNotifications.mockRejectedValue(new Error('Network error'))
    mockIsAxiosError.mockReturnValue(false)

    const { result } = renderHook(() => useNotifications())
    await waitFor(() => expect(result.current.loading).toBe(false))

    expect(result.current.error).toBe('Impossible de charger les notifications.')
    expect(result.current.notifications).toHaveLength(0)
  })

  it('silently ignores 401 errors — no error message set', async () => {
    const err = { response: { status: 401 } }
    mockGetNotifications.mockRejectedValue(err)
    mockIsAxiosError.mockReturnValue(true)

    const { result } = renderHook(() => useNotifications())
    await waitFor(() => expect(result.current.loading).toBe(false))

    expect(result.current.error).toBeNull()
  })

  it('registers a 30-second polling interval on mount', () => {
    mockGetNotifications.mockResolvedValue([])
    const spyInterval = vi.spyOn(global, 'setInterval')

    const { unmount } = renderHook(() => useNotifications())

    const delays = spyInterval.mock.calls.map(([, delay]) => delay)
    expect(delays).toContain(30_000)

    unmount()
    spyInterval.mockRestore()
  })

  it('clears the polling interval on unmount', () => {
    mockGetNotifications.mockResolvedValue([])
    const spyClear = vi.spyOn(global, 'clearInterval')

    const { unmount } = renderHook(() => useNotifications())
    unmount()

    expect(spyClear).toHaveBeenCalled()
    spyClear.mockRestore()
  })

  it('marks all notifications as read optimistically', async () => {
    const notifs = [makeNotification('n1'), makeNotification('n2')]
    mockGetNotifications.mockResolvedValue(notifs)
    mockMarkAllRead.mockResolvedValue(undefined)

    const { result } = renderHook(() => useNotifications())
    await waitFor(() => expect(result.current.loading).toBe(false))

    act(() => { result.current.markAllAsRead() })

    expect(result.current.notifications.every(n => n.read)).toBe(true)
    expect(result.current.unreadCount).toBe(0)
  })

  it('reverts optimistic update when markAllRead API call fails', async () => {
    const notifs = [makeNotification('n1')]
    mockGetNotifications.mockResolvedValueOnce(notifs)
    mockMarkAllRead.mockRejectedValue(new Error('fail'))
    mockGetNotifications.mockResolvedValue(notifs)

    const { result } = renderHook(() => useNotifications())
    await waitFor(() => expect(result.current.loading).toBe(false))

    act(() => { result.current.markAllAsRead() })
    expect(result.current.unreadCount).toBe(0)

    await waitFor(() => expect(mockGetNotifications).toHaveBeenCalledTimes(2))
    expect(result.current.notifications[0].read).toBe(false)
  })

  it('returns empty notifications list on successful fetch with no data', async () => {
    mockGetNotifications.mockResolvedValue([])

    const { result } = renderHook(() => useNotifications())
    await waitFor(() => expect(result.current.loading).toBe(false))

    expect(result.current.notifications).toHaveLength(0)
    expect(result.current.unreadCount).toBe(0)
    expect(result.current.error).toBeNull()
  })
})
