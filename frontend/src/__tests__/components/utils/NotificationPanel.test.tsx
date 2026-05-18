
import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import type { ReactNode } from 'react'

vi.mock('@/contexts/ThemeContext', () => ({
  useTheme: () => ({ theme: 'dark', toggleTheme: () => {} }),
}))

vi.mock('boneyard-js/react', () => ({
  Skeleton: ({ children, name }: { children: ReactNode; name: string }) => (
    <div data-testid="skeleton" data-name={name}>{children}</div>
  ),
}))

import { NotificationPanel } from '@/components/utils/NotificationPanel'
import type { Notification } from '@/types/notification'

afterEach(() => { cleanup() })

const makeNotification = (id: string, read = false, eventId = 'e1'): Notification => ({
  id,
  userId: 'u1',
  eventId,
  type: 'EVENT_UPDATED',
  message: `Notification ${id}`,
  read,
  createdAt: new Date(Date.now() - 60_000 * 5).toISOString(),
})

function renderPanel(props: Partial<Parameters<typeof NotificationPanel>[0]> = {}) {
  return render(
    <MemoryRouter>
      <NotificationPanel
        notifications={[]}
        loading={false}
        error={null}
        onMarkAllRead={() => {}}
        {...props}
      />
    </MemoryRouter>,
  )
}

describe('NotificationPanel', () => {
  it('shows skeleton while loading', () => {
    renderPanel({ loading: true })
    expect(screen.getByTestId('skeleton')).toBeTruthy()
  })

  it('shows empty state when no notifications', () => {
    renderPanel({ notifications: [] })
    expect(screen.getByText('Aucune notification')).toBeTruthy()
  })

  it('shows error message when error is set', () => {
    renderPanel({ error: 'Impossible de charger les notifications.' })
    expect(screen.getByText('Impossible de charger les notifications.')).toBeTruthy()
  })

  it('renders a list of notifications', () => {
    const notifs = [makeNotification('n1'), makeNotification('n2')]
    renderPanel({ notifications: notifs })
    expect(screen.getByText('Notification n1')).toBeTruthy()
    expect(screen.getByText('Notification n2')).toBeTruthy()
  })

  it('shows "Tout marquer comme lu" button when there are unread notifications', () => {
    renderPanel({ notifications: [makeNotification('n1', false)] })
    expect(screen.getByRole('button', { name: 'Tout marquer comme lu' })).toBeTruthy()
  })

  it('hides "Tout marquer comme lu" button when all notifications are read', () => {
    renderPanel({ notifications: [makeNotification('n1', true)] })
    expect(screen.queryByRole('button', { name: 'Tout marquer comme lu' })).toBeNull()
  })

  it('calls onMarkAllRead when button is clicked', () => {
    const onMarkAllRead = vi.fn()
    renderPanel({ notifications: [makeNotification('n1', false)], onMarkAllRead })
    fireEvent.click(screen.getByRole('button', { name: 'Tout marquer comme lu' }))
    expect(onMarkAllRead).toHaveBeenCalledOnce()
  })

  it('renders notification as a link when eventId is present', () => {
    renderPanel({ notifications: [makeNotification('n1', false, 'e42')] })
    const link = screen.getByRole('link')
    expect((link as HTMLAnchorElement).href).toContain('/events/e42')
  })

  it('shows Notifications header', () => {
    renderPanel()
    expect(screen.getByText('Notifications')).toBeTruthy()
  })

  it('renders notification without eventId as a plain div (not a link)', () => {
    renderPanel({ notifications: [makeNotification('n1', false, '')] })
    expect(screen.queryByRole('link')).toBeNull()
    expect(screen.getByText('Notification n1')).toBeTruthy()
  })

  it('shows "À l\'instant" for notifications less than 1 minute old', () => {
    const notif = { ...makeNotification('n1'), createdAt: new Date(Date.now() - 30_000).toISOString() }
    renderPanel({ notifications: [notif] })
    expect(screen.getByText("À l'instant")).toBeTruthy()
  })

  it('shows hours for notifications 1–23 hours old', () => {
    const notif = { ...makeNotification('n1'), createdAt: new Date(Date.now() - 2 * 3_600_000).toISOString() }
    renderPanel({ notifications: [notif] })
    expect(screen.getByText('Il y a 2h')).toBeTruthy()
  })

  it('shows days for notifications 1–6 days old', () => {
    const notif = { ...makeNotification('n1'), createdAt: new Date(Date.now() - 3 * 86_400_000).toISOString() }
    renderPanel({ notifications: [notif] })
    expect(screen.getByText('Il y a 3j')).toBeTruthy()
  })

  it('shows locale date string for notifications 7+ days old', () => {
    const old = new Date(Date.now() - 10 * 86_400_000)
    const notif = { ...makeNotification('n1'), createdAt: old.toISOString() }
    renderPanel({ notifications: [notif] })
    const expected = old.toLocaleDateString('fr-CH', { day: 'numeric', month: 'short' })
    expect(screen.getByText(expected)).toBeTruthy()
  })
})
