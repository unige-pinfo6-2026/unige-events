
import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
import type { ReactNode } from 'react'

vi.mock('@/hooks/useNotifications', () => ({
  useNotifications: vi.fn(),
}))

vi.mock('@/components/utils/Dropdown', () => ({
  Dropdown: ({ trigger, children }: { trigger: ReactNode; children: ReactNode }) => (
    <div>
      <div data-testid="dropdown-trigger">{trigger}</div>
      <div data-testid="dropdown-panel">{children}</div>
    </div>
  ),
}))

vi.mock('@/components/utils/NotificationBell', () => ({
  NotificationBell: ({ unreadCount }: { unreadCount: number }) => (
    <span data-testid="notification-bell" data-unread={unreadCount}>{unreadCount}</span>
  ),
}))

vi.mock('@/components/utils/NotificationPanel', () => ({
  NotificationPanel: ({
    notifications,
    loading,
    error,
    onMarkAllRead,
    onMarkOneRead,
  }: {
    notifications: unknown[]
    loading: boolean
    error: string | null
    onMarkAllRead: () => void
    onMarkOneRead: (id: number) => void
  }) => (
    <div
      data-testid="notification-panel"
      data-loading={loading}
      data-error={error}
      data-count={notifications.length}
      onClick={onMarkAllRead}
    >
      <button
        type="button"
        data-testid="mark-one"
        onClick={(e) => { e.stopPropagation(); onMarkOneRead(99) }}
      />
    </div>
  ),
}))

import { useNotifications } from '@/hooks/useNotifications'
import { NotificationsDropdown } from '@/components/utils/NotificationsDropdown'
import type { Notification } from '@/types/notification'

const mockUseNotifications = vi.mocked(useNotifications)

afterEach(() => { cleanup() })

const defaultHookResult = {
  notifications: [] as Notification[],
  unreadCount: 0,
  loading: false,
  error: null,
  markAllAsRead: vi.fn(),
  markOneAsRead: vi.fn(),
}

describe('NotificationsDropdown', () => {
  it('renders the notification bell as the dropdown trigger', () => {
    mockUseNotifications.mockReturnValue({ ...defaultHookResult, unreadCount: 3 })
    render(<NotificationsDropdown />)
    const bell = screen.getByTestId('notification-bell')
    expect(bell).toBeTruthy()
    expect(bell.getAttribute('data-unread')).toBe('3')
  })

  it('renders the notification panel inside the dropdown', () => {
    mockUseNotifications.mockReturnValue(defaultHookResult)
    render(<NotificationsDropdown />)
    expect(screen.getByTestId('notification-panel')).toBeTruthy()
  })

  it('passes loading state to NotificationPanel', () => {
    mockUseNotifications.mockReturnValue({ ...defaultHookResult, loading: true })
    render(<NotificationsDropdown />)
    expect(screen.getByTestId('notification-panel').getAttribute('data-loading')).toBe('true')
  })

  it('passes error state to NotificationPanel', () => {
    mockUseNotifications.mockReturnValue({
      ...defaultHookResult,
      error: 'Impossible de charger les notifications.',
    })
    render(<NotificationsDropdown />)
    expect(screen.getByTestId('notification-panel').getAttribute('data-error')).toBe(
      'Impossible de charger les notifications.',
    )
  })

  it('passes notification count to NotificationPanel', () => {
    const notifications: Notification[] = [
      { id: 1, userId: 'u1', eventId: 1, type: 'EVENT_UPDATED', message: 'msg', read: false, createdAt: '', relatedUserId: null, readAt: null },
      { id: 2, userId: 'u1', eventId: 1, type: 'EVENT_REMINDER', message: 'msg', read: true, createdAt: '', relatedUserId: null, readAt: '2026-05-18T08:00:00Z' },
    ]
    mockUseNotifications.mockReturnValue({ ...defaultHookResult, notifications })
    render(<NotificationsDropdown />)
    expect(screen.getByTestId('notification-panel').getAttribute('data-count')).toBe('2')
  })

  it('wires markAllAsRead to the panel onMarkAllRead prop', () => {
    const markAllAsRead = vi.fn()
    mockUseNotifications.mockReturnValue({ ...defaultHookResult, markAllAsRead })
    render(<NotificationsDropdown />)
    screen.getByTestId('notification-panel').click()
    expect(markAllAsRead).toHaveBeenCalledOnce()
  })

  it('wires markOneAsRead to the panel onMarkOneRead prop', () => {
    const markOneAsRead = vi.fn()
    mockUseNotifications.mockReturnValue({ ...defaultHookResult, markOneAsRead })
    render(<NotificationsDropdown />)
    screen.getByTestId('mark-one').click()
    expect(markOneAsRead).toHaveBeenCalledWith(99)
  })
})
