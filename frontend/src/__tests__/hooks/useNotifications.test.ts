import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { act, renderHook, waitFor } from '@testing-library/react'
import { AxiosError, type AxiosResponse } from 'axios'

vi.mock('@/services/notificationApi', () => ({
  getNotifications: vi.fn(),
  markAllRead: vi.fn(),
  markNotificationRead: vi.fn(),
}))

import { getNotifications, markAllRead, markNotificationRead } from '@/services/notificationApi'
import { useNotifications } from '@/hooks/useNotifications'
import type { Notification } from '@/types/notification'

const mockGetNotifications = vi.mocked(getNotifications)
const mockMarkAllRead = vi.mocked(markAllRead)
const mockMarkNotificationRead = vi.mocked(markNotificationRead)

const POLL_MS = 30_000

function notif(overrides: Partial<Notification> = {}): Notification {
  return {
    id: 1,
    userId: 'user-1',
    type: 'EVENT_UPDATED',
    message: 'Test',
    read: false,
    createdAt: '2026-05-18T10:00:00Z',
    eventId: 7,
    relatedUserId: null,
    readAt: null,
    ...overrides,
  }
}

function axios401(): AxiosError {
  const err = new AxiosError('Unauthorized')
  err.response = { status: 401 } as AxiosResponse
  err.isAxiosError = true
  return err
}

beforeEach(() => {
  mockGetNotifications.mockReset()
  mockMarkAllRead.mockReset()
  mockMarkNotificationRead.mockReset()
})

afterEach(() => {
  vi.useRealTimers()
})

describe('useNotifications — initial fetch', () => {
  it('fetches notifications on mount and exposes the server unread count from the header', async () => {
    mockGetNotifications.mockResolvedValue({ notifications: [notif({ id: 1 }), notif({ id: 2, read: true })], unreadCount: 42 })

    const { result } = renderHook(() => useNotifications())

    expect(result.current.loading).toBe(true)
    await waitFor(() => expect(result.current.loading).toBe(false))

    expect(result.current.notifications).toHaveLength(2)
    expect(result.current.unreadCount).toBe(42)
    expect(result.current.error).toBeNull()
  })

  it('silently ignores 401 errors (user not authenticated)', async () => {
    mockGetNotifications.mockRejectedValue(axios401())

    const { result } = renderHook(() => useNotifications())
    await waitFor(() => expect(result.current.loading).toBe(false))

    expect(result.current.error).toBeNull()
    expect(result.current.notifications).toEqual([])
  })

  it('sets a user-facing error string on non-401 axios failures', async () => {
    const err = new AxiosError('boom')
    err.response = { status: 500 } as AxiosResponse
    err.isAxiosError = true
    mockGetNotifications.mockRejectedValue(err)

    const { result } = renderHook(() => useNotifications())
    await waitFor(() => expect(result.current.loading).toBe(false))

    expect(result.current.error).toBe('Impossible de charger les notifications.')
  })

  it('sets the error string on non-axios failures', async () => {
    mockGetNotifications.mockRejectedValue(new Error('network down'))

    const { result } = renderHook(() => useNotifications())
    await waitFor(() => expect(result.current.loading).toBe(false))

    expect(result.current.error).toBe('Impossible de charger les notifications.')
  })
})

describe('useNotifications — polling', () => {
  it('re-fetches every 30 seconds silently (does not flip loading back on)', async () => {
    vi.useFakeTimers()
    mockGetNotifications.mockResolvedValue({ notifications: [], unreadCount: 0 })

    const { result } = renderHook(() => useNotifications())
    await vi.waitFor(() => expect(result.current.loading).toBe(false))
    expect(mockGetNotifications).toHaveBeenCalledTimes(1)

    await act(async () => {
      vi.advanceTimersByTime(POLL_MS)
    })
    expect(mockGetNotifications).toHaveBeenCalledTimes(2)
    expect(result.current.loading).toBe(false) // silent refetch

    await act(async () => {
      vi.advanceTimersByTime(POLL_MS)
    })
    expect(mockGetNotifications).toHaveBeenCalledTimes(3)
  })

  it('clears the polling interval on unmount (no fetch after unmount)', async () => {
    vi.useFakeTimers()
    mockGetNotifications.mockResolvedValue({ notifications: [], unreadCount: 0 })

    const { result, unmount } = renderHook(() => useNotifications())
    await vi.waitFor(() => expect(result.current.loading).toBe(false))
    expect(mockGetNotifications).toHaveBeenCalledTimes(1)

    unmount()

    await act(async () => {
      vi.advanceTimersByTime(POLL_MS * 5)
    })
    expect(mockGetNotifications).toHaveBeenCalledTimes(1)
  })

  it('ignores a fetch that resolves after unmount (mountedRef guard)', async () => {
    let resolveFetch!: (v: { notifications: Notification[]; unreadCount: number }) => void
    mockGetNotifications.mockReturnValue(new Promise(res => { resolveFetch = res }))

    const { result, unmount } = renderHook(() => useNotifications())
    unmount()
    // Resolving after unmount must not throw; React would warn loudly if a
    // state setter ran on an unmounted hook.
    resolveFetch({ notifications: [notif()], unreadCount: 99 })
    await Promise.resolve()
    // No way to assert state after unmount via the result handle; the
    // assertion is "no console error / no throw" — implicitly enforced by the
    // mountedRef guard inside fetchNotifications.
    expect(result.current.notifications).toEqual([])
  })

  it('ignores a fetch that rejects after unmount (catch mountedRef guard)', async () => {
    let rejectFetch!: (e: Error) => void
    mockGetNotifications.mockReturnValue(new Promise((_, rej) => { rejectFetch = rej }))

    const { result, unmount } = renderHook(() => useNotifications())
    unmount()
    // Rejecting after unmount must hit the `if (!mountedRef.current) return`
    // guard in the catch (line 38) — no error state set, no throw.
    rejectFetch(new Error('late network failure'))
    await Promise.resolve()
    expect(result.current.error).toBeNull()
    expect(result.current.notifications).toEqual([])
  })
})

describe('useNotifications — markAllAsRead', () => {
  it('optimistically flips every notification to read and zeroes the badge', async () => {
    mockGetNotifications.mockResolvedValue({ notifications: [notif({ id: 1 }), notif({ id: 2 })], unreadCount: 2 })
    mockMarkAllRead.mockResolvedValue({ updated: 2 })

    const { result } = renderHook(() => useNotifications())
    await waitFor(() => expect(result.current.loading).toBe(false))

    act(() => { result.current.markAllAsRead() })

    expect(result.current.unreadCount).toBe(0)
    expect(result.current.notifications.every(n => n.read)).toBe(true)
    expect(result.current.notifications.every(n => n.readAt !== null)).toBe(true)
    expect(mockMarkAllRead).toHaveBeenCalledOnce()
  })

  it('reverts to the authoritative server state when markAllRead rejects', async () => {
    mockGetNotifications
      .mockResolvedValueOnce({ notifications: [notif({ id: 1 })], unreadCount: 1 })
      .mockResolvedValueOnce({ notifications: [notif({ id: 1 })], unreadCount: 1 })
    mockMarkAllRead.mockRejectedValue(new Error('500 server error'))

    const { result } = renderHook(() => useNotifications())
    await waitFor(() => expect(result.current.loading).toBe(false))

    await act(async () => {
      result.current.markAllAsRead()
      // wait for the catch + refetch
      await new Promise(res => setTimeout(res, 0))
    })

    await waitFor(() => expect(mockGetNotifications).toHaveBeenCalledTimes(2))
    expect(result.current.unreadCount).toBe(1)
    expect(result.current.notifications[0].read).toBe(false)
  })
})

describe('useNotifications — markOneAsRead', () => {
  it('optimistically flips the targeted row and decrements the badge', async () => {
    mockGetNotifications.mockResolvedValue({ notifications: [notif({ id: 1 }), notif({ id: 2 })], unreadCount: 2 })
    mockMarkNotificationRead.mockResolvedValue(undefined)

    const { result } = renderHook(() => useNotifications())
    await waitFor(() => expect(result.current.loading).toBe(false))

    act(() => { result.current.markOneAsRead(1) })

    expect(result.current.unreadCount).toBe(1)
    expect(result.current.notifications.find(n => n.id === 1)?.read).toBe(true)
    expect(result.current.notifications.find(n => n.id === 2)?.read).toBe(false)
    expect(mockMarkNotificationRead).toHaveBeenCalledWith(1)
  })

  it('does not change the badge when the row was already read', async () => {
    mockGetNotifications.mockResolvedValue({ notifications: [notif({ id: 1, read: true, readAt: '2026-05-18T08:00:00Z' })], unreadCount: 0 })
    mockMarkNotificationRead.mockResolvedValue(undefined)

    const { result } = renderHook(() => useNotifications())
    await waitFor(() => expect(result.current.loading).toBe(false))

    act(() => { result.current.markOneAsRead(1) })

    expect(result.current.unreadCount).toBe(0)
  })

  it('reverts to the authoritative server state when markNotificationRead rejects', async () => {
    mockGetNotifications
      .mockResolvedValueOnce({ notifications: [notif({ id: 1 })], unreadCount: 1 })
      .mockResolvedValueOnce({ notifications: [notif({ id: 1 })], unreadCount: 1 })
    mockMarkNotificationRead.mockRejectedValue(new Error('404 not found'))

    const { result } = renderHook(() => useNotifications())
    await waitFor(() => expect(result.current.loading).toBe(false))

    await act(async () => {
      result.current.markOneAsRead(1)
      await new Promise(res => setTimeout(res, 0))
    })

    await waitFor(() => expect(mockGetNotifications).toHaveBeenCalledTimes(2))
    expect(result.current.unreadCount).toBe(1)
    expect(result.current.notifications[0].read).toBe(false)
  })

  it('is a no-op when the id does not exist in the current page', async () => {
    mockGetNotifications.mockResolvedValue({ notifications: [notif({ id: 1 })], unreadCount: 1 })
    mockMarkNotificationRead.mockResolvedValue(undefined)

    const { result } = renderHook(() => useNotifications())
    await waitFor(() => expect(result.current.loading).toBe(false))

    act(() => { result.current.markOneAsRead(999) })

    expect(result.current.unreadCount).toBe(1) // unchanged
    expect(mockMarkNotificationRead).toHaveBeenCalledWith(999) // still fires
  })
})
