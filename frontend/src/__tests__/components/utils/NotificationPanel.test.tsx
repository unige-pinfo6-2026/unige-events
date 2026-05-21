import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { NotificationPanel } from '@/components/utils/NotificationPanel'
import { ThemeProvider } from '@/contexts/ThemeContext'
import { NOTIFICATION_TYPES } from '@/types/notification'
import type { Notification, NotificationType } from '@/types/notification'

function notif(overrides: Partial<Notification> = {}): Notification {
  return {
    id: 1,
    userId: 'user-1',
    type: 'EVENT_UPDATED',
    message: 'Default message',
    read: false,
    createdAt: '2026-05-18T10:00:00Z',
    eventId: 7,
    relatedUserId: null,
    readAt: null,
    ...overrides,
  }
}

function renderPanel(props: Partial<React.ComponentProps<typeof NotificationPanel>> = {}) {
  return render(
    <MemoryRouter>
      <ThemeProvider>
        <NotificationPanel
          notifications={[]}
          loading={false}
          error={null}
          onMarkAllRead={vi.fn()}
          onMarkOneRead={vi.fn()}
          {...props}
        />
      </ThemeProvider>
    </MemoryRouter>,
  )
}

afterEach(() => cleanup())

describe('NotificationPanel — loading / empty / error states', () => {
  it('renders the boneyard skeleton when loading', () => {
    renderPanel({ loading: true })

    expect(document.querySelector('[data-boneyard="notification-panel"]')).toBeTruthy()
  })

  it('renders the empty state when there are no notifications', () => {
    renderPanel({ notifications: [] })

    expect(screen.getByText('Aucune notification')).toBeTruthy()
  })

  it('renders the error message when error is set', () => {
    renderPanel({ error: 'Impossible de charger les notifications.' })

    expect(screen.getByText('Impossible de charger les notifications.')).toBeTruthy()
    expect(screen.queryByText('Aucune notification')).toBeNull()
  })
})

describe('NotificationPanel — header / mark-all-read button', () => {
  it('renders the "Tout marquer comme lu" button when at least one notif is unread', () => {
    renderPanel({ notifications: [notif({ id: 1, read: false })] })

    expect(screen.getByRole('button', { name: 'Tout marquer comme lu' })).toBeTruthy()
  })

  it('hides the "Tout marquer comme lu" button when every notif is read', () => {
    renderPanel({ notifications: [notif({ id: 1, read: true, readAt: '2026-05-18T08:00:00Z' })] })

    expect(screen.queryByRole('button', { name: 'Tout marquer comme lu' })).toBeNull()
  })

  it('invokes onMarkAllRead when the button is clicked', () => {
    const onMarkAllRead = vi.fn()
    renderPanel({ notifications: [notif({ id: 1 })], onMarkAllRead })

    fireEvent.click(screen.getByRole('button', { name: 'Tout marquer comme lu' }))

    expect(onMarkAllRead).toHaveBeenCalledOnce()
  })
})

describe('NotificationPanel — item linking', () => {
  it('wraps an item with a Link to /events/{id} when eventId is set', () => {
    const { container } = renderPanel({ notifications: [notif({ id: 1, eventId: 42, message: 'Linked' })] })

    const link = container.querySelector('a[href="/events/42"]')
    expect(link).toBeTruthy()
    expect(link?.textContent).toContain('Linked')
  })

  it('renders a plain div (no Link) when eventId is null and relatedUserId is null', () => {
    const { container } = renderPanel({ notifications: [notif({ id: 1, eventId: null, relatedUserId: null, type: 'NEW_FOLLOWER', message: 'No target' })] })

    expect(container.querySelector('a[href^="/events/"]')).toBeNull()
    expect(container.querySelector('a[href^="/profile/"]')).toBeNull()
    expect(screen.getByText('No target')).toBeTruthy()
  })

  it('wraps COMMENT_MENTION with a Link that deep-links to /events/{id}#comments', () => {
    const { container } = renderPanel({
      notifications: [notif({ id: 1, eventId: 42, type: 'COMMENT_MENTION', message: 'mention' })],
    })
    expect(container.querySelector('a[href="/events/42#comments"]')).toBeTruthy()
  })

  it('wraps NEW_COMMENT with a Link that deep-links to /events/{id}#comments', () => {
    const { container } = renderPanel({
      notifications: [notif({ id: 1, eventId: 42, type: 'NEW_COMMENT', message: 'new comment' })],
    })
    expect(container.querySelector('a[href="/events/42#comments"]')).toBeTruthy()
  })

  it('wraps a follow-related notif with a Link to /profile/{relatedUserId} when no eventId', () => {
    const { container } = renderPanel({
      notifications: [notif({ id: 1, eventId: null, relatedUserId: 'alice-uuid', type: 'NEW_FOLLOWER', message: 'follow' })],
    })
    expect(container.querySelector('a[href="/profile/alice-uuid"]')).toBeTruthy()
  })

  it('calls onMarkOneRead with the notif id when an unread row is clicked', () => {
    const onMarkOneRead = vi.fn()
    const { container } = renderPanel({
      notifications: [notif({ id: 5, eventId: 42, read: false })],
      onMarkOneRead,
    })
    fireEvent.click(container.querySelector('a[href="/events/42"]')!)
    expect(onMarkOneRead).toHaveBeenCalledWith(5)
  })

  it('does NOT call onMarkOneRead when an already-read row is clicked', () => {
    const onMarkOneRead = vi.fn()
    const { container } = renderPanel({
      notifications: [notif({ id: 5, eventId: 42, read: true, readAt: '2026-05-18T08:00:00Z' })],
      onMarkOneRead,
    })
    fireEvent.click(container.querySelector('a[href="/events/42"]')!)
    expect(onMarkOneRead).not.toHaveBeenCalled()
  })

  it('renders a button (not a Link) for unactionable rows and still marks them read on click', () => {
    const onMarkOneRead = vi.fn()
    renderPanel({
      notifications: [notif({ id: 9, eventId: null, relatedUserId: null, type: 'FOLLOW_REQUEST', message: 'no target', read: false })],
      onMarkOneRead,
    })
    const btn = screen.getByText('no target').closest('button')!
    fireEvent.click(btn)
    expect(onMarkOneRead).toHaveBeenCalledWith(9)
  })

  it('uses the notif id as the React key (does not collide on duplicate messages)', () => {
    renderPanel({
      notifications: [
        notif({ id: 1, message: 'Same text' }),
        notif({ id: 2, message: 'Same text' }),
      ],
    })

    expect(screen.getAllByText('Same text')).toHaveLength(2)
  })
})

describe('NotificationPanel — type pills (all 9 enum values + fallback)', () => {
  const allTypes: NotificationType[] = [
    'EVENT_UPDATED',
    'EVENT_CANCELLED',
    'EVENT_REMINDER',
    'NEW_ATTENDEE',
    'NEW_FOLLOWER',
    'FOLLOW_REQUEST',
    'FOLLOW_ACCEPTED',
    'COMMENT_MENTION',
    'NEW_COMMENT',
  ]

  it.each(allTypes)('renders the labelled pill for %s', type => {
    renderPanel({ notifications: [notif({ id: 1, type, eventId: null })] })

    expect(screen.getByText(NOTIFICATION_TYPES[type])).toBeTruthy()
  })

  it('falls back to the neutral "Notification" label for an unknown type without crashing', () => {
    const unknown = { ...notif({ id: 1, eventId: null }), type: 'FUTURE_TYPE' as unknown as NotificationType }
    renderPanel({ notifications: [unknown] })

    expect(screen.getByText('Notification')).toBeTruthy()
  })
})

describe('NotificationPanel — relativeTime helper boundaries', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-05-18T12:00:00Z'))
  })
  afterEach(() => {
    vi.useRealTimers()
  })

  it('renders "À l\'instant" for a notif less than 1 minute old', () => {
    renderPanel({ notifications: [notif({ id: 1, createdAt: '2026-05-18T11:59:30Z' })] })

    expect(screen.getByText("À l'instant")).toBeTruthy()
  })

  it('renders minutes when less than an hour old', () => {
    renderPanel({ notifications: [notif({ id: 1, createdAt: '2026-05-18T11:45:00Z' })] })

    expect(screen.getByText('Il y a 15 min')).toBeTruthy()
  })

  it('renders hours when less than a day old', () => {
    renderPanel({ notifications: [notif({ id: 1, createdAt: '2026-05-18T09:00:00Z' })] })

    expect(screen.getByText('Il y a 3h')).toBeTruthy()
  })

  it('renders days when less than a week old', () => {
    renderPanel({ notifications: [notif({ id: 1, createdAt: '2026-05-15T12:00:00Z' })] })

    expect(screen.getByText('Il y a 3j')).toBeTruthy()
  })

  it('renders the locale date when 7 days old or more', () => {
    renderPanel({ notifications: [notif({ id: 1, createdAt: '2026-05-01T12:00:00Z' })] })

    // fr-CH short format — "1 mai"-style. Just assert no relative-time string.
    expect(screen.queryByText(/Il y a \d/)).toBeNull()
    expect(screen.queryByText("À l'instant")).toBeNull()
  })
})
