// @vitest-environment jsdom

import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import UserAvatar from '@/components/user/UserAvatar'
import type { User } from '@/types/user'

afterEach(() => { cleanup() })

const baseUser: User = {
  id: '1',
  auth0Id: 'auth0|1',
  email: 'user@example.com',
  displayName: 'Alice Martin',
  profilePublic: true,
  createdAt: '2024-01-01',
  admin: false,
}

describe('UserAvatar', () => {
  it('renders an image when user has an avatarUrl', () => {
    const user = { ...baseUser, avatarUrl: 'https://example.com/avatar.jpg' }
    render(<UserAvatar user={user} />)
    const img = screen.getByAltText('Alice Martin') as HTMLImageElement
    expect(img).toBeTruthy()
    expect(img.src).toContain('example.com/avatar.jpg')
  })

  it('renders initials when user has no avatarUrl', () => {
    render(<UserAvatar user={baseUser} />)
    expect(screen.getByText('AM')).toBeTruthy()
  })

  it('renders initials from a single-word display name', () => {
    render(<UserAvatar user={{ ...baseUser, displayName: 'Alice' }} />)
    expect(screen.getByText('A')).toBeTruthy()
  })

  it('renders empty initials when displayName is undefined', () => {
    render(<UserAvatar user={{ ...baseUser, displayName: undefined }} />)
    const { container } = render(<UserAvatar user={{ ...baseUser, displayName: undefined }} />)
    expect(container.firstChild).toBeTruthy()
  })

  it('renders with null user (shows empty initials)', () => {
    const { container } = render(<UserAvatar user={null} />)
    expect(container.firstChild).toBeTruthy()
  })

  it('applies the specified size', () => {
    const { container } = render(<UserAvatar user={baseUser} size="xl" />)
    const div = container.querySelector('div') as HTMLDivElement
    expect(div.className).toContain('size-16')
  })

  it('applies default size lg', () => {
    const { container } = render(<UserAvatar user={baseUser} />)
    const div = container.querySelector('div') as HTMLDivElement
    expect(div.className).toContain('size-12')
  })

  it('applies custom className', () => {
    const { container } = render(<UserAvatar user={baseUser} className="extra-class" />)
    expect(container.querySelector('div')?.className).toContain('extra-class')
  })

  it('shows initials when image fails to load (onError callback)', () => {
    const user = { ...baseUser, avatarUrl: 'https://invalid.example.com/avatar.jpg' }
    render(<UserAvatar user={user} />)

    // Get the image element
    const img = screen.getByAltText('Alice Martin') as HTMLImageElement
    expect(img).toBeTruthy()

    // Trigger the error event to test the onError callback
    fireEvent.error(img)

    // After error, initials should be displayed
    expect(screen.getByText('AM')).toBeTruthy()
  })
})
