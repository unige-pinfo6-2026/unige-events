
import { afterEach, describe, expect, it } from 'vitest'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import UserAvatar from '@/components/user/UserAvatar'
import type { User } from '@/types/user'

afterEach(() => { cleanup() })

const baseUser: User = {
  id: '1',
  auth0Id: 'auth0|1',
  email: 'user@example.com',
  username: 'alice.martin',
  displayName: 'Alice Martin',
  profilePublic: true,
  createdAt: '2024-01-01',
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

  it('applies size via className', () => {
    const { container } = render(<UserAvatar user={baseUser} className="size-16" />)
    const div = container.querySelector('div') as HTMLDivElement
    expect(div.className).toContain('size-16')
  })

  it('applies custom className', () => {
    const { container } = render(<UserAvatar user={baseUser} className="extra-class" />)
    expect(container.querySelector('div')?.className).toContain('extra-class')
  })

  it('renders the image with an empty alt when displayName is null (line 23 fallback)', () => {
    // avatarUrl present → <img> branch — but displayName is null, exercising
    // the `user?.displayName ?? ''` empty-string fallback inside the alt.
    const user = { ...baseUser, displayName: null, avatarUrl: 'https://example.com/a.jpg' }
    render(<UserAvatar user={user} />)
    const img = document.querySelector('img') as HTMLImageElement
    expect(img).toBeTruthy()
    expect(img.getAttribute('alt')).toBe('')
    expect(img.src).toContain('example.com/a.jpg')
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
