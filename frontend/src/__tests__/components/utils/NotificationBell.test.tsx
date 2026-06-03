
import { afterEach, describe, expect, it } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
import { NotificationBell } from '@/components/utils/NotificationBell'

afterEach(() => { cleanup() })

describe('NotificationBell', () => {
  it('renders the bell as presentational, not a nested button', () => {
    render(<NotificationBell unreadCount={0} />)
    // The interactive control is the Dropdown trigger that wraps this bell, so
    // the bell itself must not be a button (avoids button-in-button on iOS).
    expect(screen.queryByRole('button')).toBeNull()
    expect(document.querySelector('svg')).toBeTruthy()
  })

  it('shows no badge when unreadCount is 0', () => {
    render(<NotificationBell unreadCount={0} />)
    expect(screen.queryByLabelText(/non lue/i)).toBeNull()
  })

  it('shows badge with singular label for 1 unread notification', () => {
    render(<NotificationBell unreadCount={1} />)
    expect(screen.getByLabelText('1 notification non lue')).toBeTruthy()
    expect(screen.getByText('1')).toBeTruthy()
  })

  it('shows badge with plural label for multiple unread notifications', () => {
    render(<NotificationBell unreadCount={5} />)
    expect(screen.getByLabelText('5 notifications non lues')).toBeTruthy()
    expect(screen.getByText('5')).toBeTruthy()
  })

  it('shows 99 when unreadCount is exactly 99', () => {
    render(<NotificationBell unreadCount={99} />)
    expect(screen.getByText('99')).toBeTruthy()
  })

  it('shows 99+ when unreadCount exceeds 99', () => {
    render(<NotificationBell unreadCount={100} />)
    expect(screen.getByText('99+')).toBeTruthy()
  })

  it('shows 99+ for large unread counts', () => {
    render(<NotificationBell unreadCount={999} />)
    expect(screen.getByText('99+')).toBeTruthy()
    expect(screen.getByLabelText('999 notifications non lues')).toBeTruthy()
  })
})
