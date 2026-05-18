import { afterEach, describe, expect, it } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
import { NotificationBell } from '@/components/utils/NotificationBell'

afterEach(() => cleanup())

describe('NotificationBell', () => {
  it('renders the bell trigger with no badge when unreadCount is 0', () => {
    render(<NotificationBell unreadCount={0} />)

    expect(screen.getByRole('button', { name: 'Notifications' })).toBeTruthy()
    expect(screen.queryByLabelText(/notification.* non lue/)).toBeNull()
  })

  it('renders a singular aria-label when there is exactly 1 unread', () => {
    render(<NotificationBell unreadCount={1} />)

    const badge = screen.getByLabelText('1 notification non lue')
    expect(badge.textContent).toBe('1')
  })

  it('renders a plural aria-label when there are 2+ unread', () => {
    render(<NotificationBell unreadCount={5} />)

    const badge = screen.getByLabelText('5 notifications non lues')
    expect(badge.textContent).toBe('5')
  })

  it('renders the exact count up to 99', () => {
    render(<NotificationBell unreadCount={99} />)

    const badge = screen.getByLabelText('99 notifications non lues')
    expect(badge.textContent).toBe('99')
  })

  it('renders "99+" when there are more than 99 unread', () => {
    render(<NotificationBell unreadCount={123} />)

    const badge = screen.getByLabelText('123 notifications non lues')
    expect(badge.textContent).toBe('99+')
  })
})
